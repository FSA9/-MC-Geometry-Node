package com.mine.geometry_node.client.model.render.backend.host.entity;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.client.model.gpu.ModelGpuBuffer;
import com.mine.geometry_node.client.model.gpu.ModelUploadScheduler;
import com.mine.geometry_node.client.model.gpu.minecraft.MinecraftModelGpuAccess;
import com.mine.geometry_node.client.model.gpu.minecraft.MinecraftModelGpuBuffer;
import com.mine.geometry_node.client.model.render.backend.host.geometry.HostEntityGeometry;
import com.mine.geometry_node.client.model.render.backend.host.geometry.HostEntityMeshChunkBuilder;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/** Render-thread, chunked builder/uploader for one exact static HOST variant. */
final class HostStaticVariantUpload implements ModelUploadScheduler.WorkItem {
    static final int TRIANGLES_PER_STEP = 2048;

    private final HostPreparedArtifact artifact;
    private final HostEntityGeometry geometry;
    private final HostStaticVariantKey key;
    private final HostPackedLightVariantGate gate;
    private final int gateToken;
    private final long generation;
    private final VertexFormat format;
    private final BooleanSupplier layoutValid;
    private final HostStaticVariantBudget.Reservation reservation;
    private final int triangleCount;
    private final int vertexCount;
    private final int indexCount;
    private final int byteSize;
    private final String label;
    private ModelGpuBuffer buffer;
    private int builtTriangles;
    private boolean ownershipTransferred;

    static HostStaticVariantUpload tryCreate(HostPreparedArtifact artifact, HostEntityGeometry geometry,
                                             HostStaticVariantKey key, HostPackedLightVariantGate gate,
                                             int gateToken,
                                             VertexFormat format, BooleanSupplier layoutValid, String label) {
        int triangles = geometry.staticTriangleCount();
        int vertices = Math.multiplyExact(triangles, 4);
        int bytes = Math.multiplyExact(vertices, format.getVertexSize());
        HostStaticVariantBudget.Reservation reservation = artifact.reserveStaticVariant(bytes);
        return reservation == null ? null : new HostStaticVariantUpload(artifact, geometry, key, gate, gateToken,
                format, layoutValid, label, reservation, triangles, vertices, bytes);
    }

    private HostStaticVariantUpload(HostPreparedArtifact artifact, HostEntityGeometry geometry,
                                    HostStaticVariantKey key, HostPackedLightVariantGate gate,
                                    int gateToken,
                                    VertexFormat format, BooleanSupplier layoutValid, String label,
                                    HostStaticVariantBudget.Reservation reservation,
                                    int triangleCount, int vertexCount, int byteSize) {
        this.artifact = Objects.requireNonNull(artifact, "artifact");
        this.geometry = Objects.requireNonNull(geometry, "geometry");
        this.key = Objects.requireNonNull(key, "key");
        this.gate = Objects.requireNonNull(gate, "gate");
        this.gateToken = gateToken;
        this.generation = artifact.staticGeneration();
        this.format = Objects.requireNonNull(format, "format");
        this.layoutValid = Objects.requireNonNull(layoutValid, "layoutValid");
        this.label = Objects.requireNonNull(label, "label");
        this.reservation = Objects.requireNonNull(reservation, "reservation");
        this.triangleCount = triangleCount;
        this.vertexCount = vertexCount;
        this.indexCount = Math.multiplyExact(triangleCount, 6);
        this.byteSize = byteSize;
    }

    @Override public long nextBytes() {
        if (buffer == null) return 0;
        int triangles = Math.min(TRIANGLES_PER_STEP, triangleCount - builtTriangles);
        return Math.multiplyExact(Math.multiplyExact((long) triangles, 4L), format.getVertexSize());
    }

    @Override public int nextObjects() { return buffer == null ? 1 : 0; }

    @Override public long remainingBytes() {
        return Math.multiplyExact(Math.multiplyExact((long) (triangleCount - builtTriangles), 4L),
                format.getVertexSize());
    }

    @Override public int remainingObjects() { return buffer == null ? 1 : 0; }

    @Override public long stagingBytes() { return nextBytes(); }

    @Override public boolean cancelled() {
        return !artifact.staticGeneration(generation) || !layoutValid.getAsBoolean();
    }

    @Override public boolean runStep() {
        RenderSystem.assertOnRenderThread();
        if (buffer == null) {
            GpuBuffer gpu = RenderSystem.getDevice().createBuffer(() -> label,
                    GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, byteSize);
            buffer = new MinecraftModelGpuBuffer(gpu);
            return false;
        }
        int triangles = Math.min(TRIANGLES_PER_STEP, triangleCount - builtTriangles);
        HostEntityMeshChunkBuilder.BuiltChunk chunk = HostEntityMeshChunkBuilder.build(
                geometry, builtTriangles, triangles, key.poseTransform(), key.normalTransform(), format,
                key.red(), key.green(), key.blue(), key.alpha(), key.packedLight(), key.mirrored());
        int offset = Math.multiplyExact(Math.multiplyExact(builtTriangles, 4), format.getVertexSize());
        byte[] data = chunk.vertexData();
        RenderSystem.getDevice().createCommandEncoder().writeToBuffer(
                MinecraftModelGpuAccess.buffer(buffer).slice(offset, data.length), direct(data));
        builtTriangles += triangles;
        return builtTriangles == triangleCount;
    }

    @Override public void completed() {
        HostStaticGeometryVariant variant = new HostStaticGeometryVariant(
                Objects.requireNonNull(buffer, "uploaded HOST static buffer"), null,
                vertexCount, indexCount, reservation);
        ownershipTransferred = true;
        HostPreparedArtifact.StaticVariantPublication publication;
        try {
            publication = artifact.publishStaticVariant(geometry, key, generation, variant);
        } catch (RuntimeException | Error failure) {
            variant.close();
            throw failure;
        }
        retire(publication.retired());
        if (publication.published()) gate.recordSuccess(gateToken, generation);
        else gate.recordCancelled(gateToken, generation);
    }

    @Override public void cancelledByScheduler() {
        releaseOwned();
        gate.recordCancelled(gateToken, generation);
    }

    @Override public void failed(Throwable failure) {
        releaseOwned();
        gate.recordFailure(gateToken, generation);
        GeometryNode.LOGGER.warn("Static HOST variant build failed for {}", label, failure);
    }

    private void releaseOwned() {
        if (ownershipTransferred) return;
        if (buffer != null) buffer.close();
        reservation.close();
        ownershipTransferred = true;
    }

    static void retire(List<HostStaticGeometryVariant> variants) {
        if (variants.isEmpty()) return;
        RenderSystem.queueFencedTask(() -> variants.forEach(HostStaticGeometryVariant::close));
    }

    private static ByteBuffer direct(byte[] data) {
        ByteBuffer result = ByteBuffer.allocateDirect(data.length).order(ByteOrder.nativeOrder());
        return result.put(data).flip();
    }

}
