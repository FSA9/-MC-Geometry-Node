package com.mine.geometry_node.client.model.render.backend.host.entity;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.client.model.gpu.ModelGpuBuffer;
import com.mine.geometry_node.client.model.gpu.ModelUploadScheduler;
import com.mine.geometry_node.client.model.gpu.minecraft.MinecraftModelGpuAccess;
import com.mine.geometry_node.client.model.gpu.minecraft.MinecraftModelGpuBuffer;
import com.mine.geometry_node.client.model.render.backend.host.geometry.HostEntityGeometry;
import com.mine.geometry_node.client.model.render.backend.host.geometry.HostEntityMeshChunkBuilder;
import com.mine.geometry_node.client.model.render.backend.host.light.contract.HostLightBinding;
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
    private final HostPreparedArtifact.StaticDrawSlot drawSlot;
    private final HostStaticVariantKey key;
    private final HostLightBinding lightBinding;
    private final HostStaticVariantAdmissionGate gate;
    private final HostStaticAdmissionKey admissionKey;
    private final long generation;
    private final VertexFormat format;
    private final BooleanSupplier layoutValid;
    private final HostStaticVariantBudget.Reservation reservation;
    private final boolean initialWorkset;
    private final int triangleCount;
    private final int vertexCount;
    private final int indexCount;
    private final int byteSize;
    private final String label;
    private ModelGpuBuffer buffer;
    private int builtTriangles;
    private boolean ownershipTransferred;

    static HostStaticVariantUpload tryCreate(HostPreparedArtifact artifact, HostEntityGeometry geometry,
                                             HostPreparedArtifact.StaticDrawSlot drawSlot,
                                             HostStaticVariantKey key, HostStaticVariantAdmissionGate gate,
                                             HostStaticAdmissionKey admissionKey,
                                             HostLightBinding lightBinding,
                                             VertexFormat format, BooleanSupplier layoutValid, String label) {
        int triangles = key.triangleCount();
        int vertices = Math.multiplyExact(triangles, 3);
        int bytes = Math.multiplyExact(vertices, format.getVertexSize());
        boolean initialWorkset = artifact.initialStaticWorksetBuilding(key.instanceIdentity());
        HostStaticVariantBudget.Reservation reservation = initialWorkset
                ? artifact.claimInitialStaticVariant(key.instanceIdentity(), drawSlot, geometry, key, bytes)
                : artifact.reserveStaticVariant(bytes);
        return reservation == null ? null : new HostStaticVariantUpload(artifact, geometry, key, gate, admissionKey,
                lightBinding,
                drawSlot, format, layoutValid, label, reservation, initialWorkset, triangles, vertices, bytes);
    }

    private HostStaticVariantUpload(HostPreparedArtifact artifact, HostEntityGeometry geometry,
                                    HostStaticVariantKey key, HostStaticVariantAdmissionGate gate,
                                    HostStaticAdmissionKey admissionKey,
                                    HostLightBinding lightBinding,
                                    HostPreparedArtifact.StaticDrawSlot drawSlot,
                                    VertexFormat format, BooleanSupplier layoutValid, String label,
                                    HostStaticVariantBudget.Reservation reservation,
                                    boolean initialWorkset,
                                    int triangleCount, int vertexCount, int byteSize) {
        this.artifact = Objects.requireNonNull(artifact, "artifact");
        this.geometry = Objects.requireNonNull(geometry, "geometry");
        this.drawSlot = drawSlot;
        this.key = Objects.requireNonNull(key, "key");
        this.lightBinding = Objects.requireNonNull(lightBinding, "lightBinding");
        if (!key.lightIdentity().equals(lightBinding.identity())) {
            throw new IllegalArgumentException("light binding identity does not match static key");
        }
        this.gate = Objects.requireNonNull(gate, "gate");
        this.admissionKey = Objects.requireNonNull(admissionKey, "admissionKey");
        this.generation = artifact.staticGeneration();
        this.format = Objects.requireNonNull(format, "format");
        this.layoutValid = Objects.requireNonNull(layoutValid, "layoutValid");
        this.label = Objects.requireNonNull(label, "label");
        this.reservation = Objects.requireNonNull(reservation, "reservation");
        this.initialWorkset = initialWorkset;
        this.triangleCount = triangleCount;
        this.vertexCount = vertexCount;
        this.indexCount = Math.multiplyExact(triangleCount, 3);
        this.byteSize = byteSize;
    }

    @Override public long nextBytes() {
        if (buffer == null) return 0;
        int triangles = Math.min(TRIANGLES_PER_STEP, triangleCount - builtTriangles);
        return Math.multiplyExact(Math.multiplyExact((long) triangles, 3L), format.getVertexSize());
    }

    @Override public int nextObjects() { return buffer == null ? 1 : 0; }

    @Override public long remainingBytes() {
        return Math.multiplyExact(Math.multiplyExact((long) (triangleCount - builtTriangles), 3L),
                format.getVertexSize());
    }

    @Override public int remainingObjects() { return buffer == null ? 1 : 0; }

    @Override public long stagingBytes() { return nextBytes(); }

    @Override public boolean cancelled() {
        return !artifact.staticGeneration(generation) || !layoutValid.getAsBoolean()
                || initialWorkset && !artifact.initialStaticWorksetBuilding(key.instanceIdentity());
    }

    @Override public boolean runStep() {
        RenderSystem.assertOnRenderThread();
        if (buffer == null) {
            GpuBuffer gpu = RenderSystem.getDevice().createBuffer(() -> label,
                    GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, byteSize);
            buffer = new MinecraftModelGpuBuffer(gpu);
            reservation.markResident();
            return false;
        }
        int triangles = Math.min(TRIANGLES_PER_STEP, triangleCount - builtTriangles);
        HostEntityMeshChunkBuilder.BuiltChunk chunk = HostEntityMeshChunkBuilder.build(
                geometry, Math.addExact(key.firstTriangle(), builtTriangles), triangles,
                key.poseTransform(), key.normalTransform(), format,
                key.red(), key.green(), key.blue(), key.alpha(), lightBinding, key.mirrored());
        int offset = Math.multiplyExact(Math.multiplyExact(builtTriangles, 3), format.getVertexSize());
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
            publication = artifact.publishStaticVariant(drawSlot, geometry, key, generation, variant);
        } catch (RuntimeException | Error failure) {
            variant.close();
            throw failure;
        }
        retire(publication.retired(), publication.retirementComplete());
        if (publication.activated()) gate.recordSuccess(admissionKey, generation);
        else if (!publication.published()) gate.recordCancelled(admissionKey, generation);
        HostStaticCacheMetrics.INSTANCE.recordBuildCompleted();
    }

    @Override public void cancelledByScheduler() {
        releaseOwned();
        failInitialWorkset();
        gate.recordCancelled(admissionKey, generation);
    }

    @Override public void failed(Throwable failure) {
        releaseOwned();
        failInitialWorkset();
        gate.recordFailure(admissionKey, generation);
        HostStaticCacheMetrics.INSTANCE.recordBuildFailed();
        GeometryNode.LOGGER.warn("Static HOST variant build failed for {}", label, failure);
    }

    private void releaseOwned() {
        if (ownershipTransferred) return;
        try {
            if (buffer != null) buffer.close();
        } finally {
            reservation.close();
            ownershipTransferred = true;
        }
    }

    static void retire(List<HostStaticGeometryVariant> variants) {
        retire(variants, () -> {});
    }

    static void retire(List<HostStaticGeometryVariant> variants, Runnable completion) {
        if (variants.isEmpty()) {
            completion.run();
            return;
        }
        long bytes = 0;
        for (HostStaticGeometryVariant variant : variants) bytes = Math.addExact(bytes, variant.byteSize());
        HostStaticCacheMetrics.INSTANCE.recordRetired(variants.size(), bytes);
        RenderSystem.queueFencedTask(() -> {
            try {
                closeAll(variants);
            } finally {
                completion.run();
            }
        });
    }

    private void failInitialWorkset() {
        if (!initialWorkset) return;
        retire(artifact.failInitialStaticWorkset(key.instanceIdentity()));
    }

    static void closeAll(List<HostStaticGeometryVariant> variants) {
        RuntimeException firstFailure = null;
        for (HostStaticGeometryVariant variant : variants) {
            try {
                variant.close();
            } catch (RuntimeException failure) {
                if (firstFailure == null) firstFailure = failure;
            }
        }
        if (firstFailure != null) throw firstFailure;
    }

    private static ByteBuffer direct(byte[] data) {
        ByteBuffer result = ByteBuffer.allocateDirect(data.length).order(ByteOrder.nativeOrder());
        return result.put(data).flip();
    }

}
