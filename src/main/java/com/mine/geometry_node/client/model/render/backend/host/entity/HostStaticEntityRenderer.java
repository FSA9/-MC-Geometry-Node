package com.mine.geometry_node.client.model.render.backend.host.entity;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.client.model.gpu.minecraft.MinecraftModelGpuAccess;
import com.mine.geometry_node.client.model.render.backend.host.geometry.HostClusterVisibility;
import com.mine.geometry_node.client.model.render.backend.host.geometry.HostEntityGeometry;
import com.mine.geometry_node.client.model.render.backend.host.lod.HostModelLodPlan;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;

/** Direct opaque/cutout HOST pass for fully prepared static entity variants. */
public final class HostStaticEntityRenderer {
    private static final List<Command> COMMANDS = new ArrayList<>();
    private static final Set<HostEntityGeometry> LOD_AVAILABILITY_RECORDED =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private static boolean loggedLayoutMismatch;
    private static int staticDraws;
    private static int staticGpuDrawCalls;
    private static long staticSubmittedTriangles;
    private static int clusterNodesTested;
    private static int candidateClusters;
    private static int visibleClusters;
    private static int culledClusters;
    private static int rangeLimitFallbacks;
    private static final int[] requestedLod = new int[4];
    private static final int[] actualLod = new int[4];
    private static final int[] availableLod = new int[4];
    private static long lodSourceTriangles, lodLevel1Triangles, lodLevel2Triangles, lodLevel3Triangles;
    private static long lodEligibleVertices, lodLockedVertices, lodBuildNanos;
    private static int lodBuildFailures;
    private static int buildingDraws;
    private static int fallbackDraws;
    private static int deferredImmediateDraws;
    private static long immediateVertices;
    private static boolean runtimeFailed;

    private HostStaticEntityRenderer() {}

    public static void beginFrame() {
        COMMANDS.clear();
        staticDraws = 0;
        staticGpuDrawCalls = 0;
        staticSubmittedTriangles = 0;
        clusterNodesTested = candidateClusters = visibleClusters = culledClusters = rangeLimitFallbacks = 0;
        LOD_AVAILABILITY_RECORDED.clear();
        java.util.Arrays.fill(requestedLod, 0);
        java.util.Arrays.fill(actualLod, 0);
        java.util.Arrays.fill(availableLod, 0);
        lodSourceTriangles = lodLevel1Triangles = lodLevel2Triangles = lodLevel3Triangles = 0;
        lodEligibleVertices = lodLockedVertices = lodBuildNanos = 0;
        lodBuildFailures = 0;
        buildingDraws = 0;
        fallbackDraws = 0;
        deferredImmediateDraws = 0;
        immediateVertices = 0;
    }

    static void submit(HostStaticGeometryVariant variant, VertexFormat format, RenderType renderType,
                       HostPreparedArtifact.CompatibilityTexture texture, Vector3f modelOffset,
                       HostClusterVisibility.Result visibility) {
        COMMANDS.add(new Command(variant, format, renderType, texture, new Vector3f(modelOffset),
                visibility.ranges()));
        staticDraws++;
        recordVisibility(visibility);
    }

    static void recordBuilding() { buildingDraws++; }
    static void recordFallback() { fallbackDraws++; }
    static void recordCulled(HostClusterVisibility.Result visibility) { recordVisibility(visibility); }
    static void recordImmediate(long vertices) { immediateVertices += vertices; }
    static void recordDeferredImmediate() { deferredImmediateDraws++; }
    static boolean available() { return !runtimeFailed; }

    static void recordModelLod(HostEntityGeometry geometry, int requested, int actual) {
        requestedLod[Math.clamp(requested, 0, 3)]++;
        actualLod[Math.clamp(actual, 0, 3)]++;
        if (!LOD_AVAILABILITY_RECORDED.add(geometry)) return;
        HostModelLodPlan.Statistics statistics = geometry.lod().statistics();
        int maximum = geometry.lod().level(3).generatedLevel();
        availableLod[Math.clamp(maximum, 0, 3)]++;
        lodSourceTriangles += statistics.sourceTriangles();
        lodLevel1Triangles += statistics.level1Triangles();
        lodLevel2Triangles += statistics.level2Triangles();
        lodLevel3Triangles += statistics.level3Triangles();
        lodEligibleVertices += statistics.eligibleVertices();
        lodLockedVertices += statistics.lockedVertices();
        lodBuildNanos += statistics.buildNanos();
        if (statistics.stopReason() == HostModelLodPlan.StopReason.BUILD_FAILURE) lodBuildFailures++;
    }

    private static void recordVisibility(HostClusterVisibility.Result visibility) {
        clusterNodesTested += visibility.nodesTested();
        candidateClusters += visibility.candidateLeaves();
        visibleClusters += visibility.visibleLeaves();
        culledClusters += visibility.culledLeaves();
        if (visibility.rangeLimitFallback()) rangeLimitFallbacks++;
    }

    public static Diagnostics diagnostics() {
        return new Diagnostics(staticDraws, staticGpuDrawCalls, staticSubmittedTriangles,
                clusterNodesTested, candidateClusters, visibleClusters, culledClusters, rangeLimitFallbacks,
                requestedLod[0], requestedLod[1], requestedLod[2], requestedLod[3],
                actualLod[0], actualLod[1], actualLod[2], actualLod[3],
                availableLod[0], availableLod[1], availableLod[2], availableLod[3],
                LOD_AVAILABILITY_RECORDED.size(), lodSourceTriangles, lodLevel1Triangles,
                lodLevel2Triangles, lodLevel3Triangles, lodEligibleVertices, lodLockedVertices,
                lodBuildFailures, lodBuildNanos,
                buildingDraws, fallbackDraws, deferredImmediateDraws, immediateVertices,
                HostVertexBudget.MAX_VERTICES_PER_FRAME, HostStaticVariantBudget.INSTANCE.reservedBytes());
    }

    public static void render(Matrix4fc modelView) {
        if (COMMANDS.isEmpty()) return;
        Minecraft minecraft = Minecraft.getInstance();
        for (Command command : COMMANDS) {
            try {
                draw(minecraft, modelView, command);
            } catch (RuntimeException | LinkageError failure) {
                runtimeFailed = true;
                GeometryNode.LOGGER.error("Static HOST pass failed; reverting future frames to immediate", failure);
                break;
            }
        }
        COMMANDS.clear();
    }

    public static void clear() {
        COMMANDS.clear();
        loggedLayoutMismatch = false;
        staticDraws = staticGpuDrawCalls = buildingDraws = fallbackDraws = deferredImmediateDraws = 0;
        staticSubmittedTriangles = 0;
        clusterNodesTested = candidateClusters = visibleClusters = culledClusters = rangeLimitFallbacks = 0;
        LOD_AVAILABILITY_RECORDED.clear();
        java.util.Arrays.fill(requestedLod, 0);
        java.util.Arrays.fill(actualLod, 0);
        java.util.Arrays.fill(availableLod, 0);
        lodSourceTriangles = lodLevel1Triangles = lodLevel2Triangles = lodLevel3Triangles = 0;
        lodEligibleVertices = lodLockedVertices = lodBuildNanos = 0;
        lodBuildFailures = 0;
        immediateVertices = 0;
        runtimeFailed = false;
    }

    private static void draw(Minecraft minecraft, Matrix4fc modelView, Command command) {
        if (command.renderType().pipeline().getVertexFormat() != command.format()) {
            if (!loggedLayoutMismatch) {
                loggedLayoutMismatch = true;
                GeometryNode.LOGGER.warn("Static HOST draw skipped because the active entity vertex layout changed");
            }
            throw new IllegalStateException("active entity vertex layout changed");
        }
        GpuBuffer vertices = MinecraftModelGpuAccess.buffer(command.variant().vertexBuffer());
        if (vertices.isClosed()) throw new IllegalStateException("static HOST vertex buffer is closed");
        RenderTarget target = command.renderType().outputTarget().getRenderTarget();
        GpuTextureView color = RenderSystem.outputColorTextureOverride != null
                ? RenderSystem.outputColorTextureOverride : target.getColorTextureView();
        GpuTextureView depth = target.useDepth
                ? (RenderSystem.outputDepthTextureOverride != null
                ? RenderSystem.outputDepthTextureOverride : target.getDepthTextureView())
                : null;
        if (color == null) throw new IllegalStateException("static HOST render target has no color attachment");

        int maxEndTriangle = 0;
        int availableTriangles = command.variant().vertexCount() / 3;
        for (HostClusterVisibility.TriangleRange range : command.ranges()) {
            if (range.endTriangle() > availableTriangles) {
                throw new IllegalArgumentException("static HOST range exceeds variant geometry");
            }
            maxEndTriangle = Math.max(maxEndTriangle, range.endTriangle());
        }
        int requiredIndices = Math.multiplyExact(maxEndTriangle, 3);
        RenderSystem.AutoStorageIndexBuffer sequential = RenderSystem.getSequentialBuffer(VertexFormat.Mode.TRIANGLES);
        GpuBuffer indices = sequential.getBuffer(requiredIndices);
        if (indices == null || indices.isClosed()) {
            throw new IllegalStateException("static HOST sequential index buffer is unavailable");
        }
        GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().writeTransform(
                modelView, new Vector4f(1), command.modelOffset(), new Matrix4f());
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "GeometryNode static HOST entity", color, OptionalInt.empty(), depth, OptionalDouble.empty())) {
            pass.setPipeline(command.renderType().pipeline());
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transforms);
            pass.setVertexBuffer(0, vertices);
            pass.setIndexBuffer(indices, sequential.type());
            pass.bindTexture("Sampler0", command.texture().texture().getTextureView(),
                    command.texture().texture().getSampler());
            pass.bindTexture("Sampler1", minecraft.gameRenderer.overlayTexture().getTextureView(),
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            pass.bindTexture("Sampler2", minecraft.gameRenderer.lightmap(),
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            for (HostClusterVisibility.TriangleRange range : command.ranges()) {
                pass.drawIndexed(0, Math.multiplyExact(range.firstTriangle(), 3),
                        Math.multiplyExact(range.triangleCount(), 3), 1);
                staticGpuDrawCalls++;
                staticSubmittedTriangles += range.triangleCount();
            }
        }
    }

    private record Command(HostStaticGeometryVariant variant, VertexFormat format, RenderType renderType,
                           HostPreparedArtifact.CompatibilityTexture texture, Vector3f modelOffset,
                           List<HostClusterVisibility.TriangleRange> ranges) {
        private Command {
            ranges = List.copyOf(ranges);
            if (ranges.isEmpty()) throw new IllegalArgumentException("static HOST command requires ranges");
        }
    }

    public record Diagnostics(int staticDraws, int staticGpuDrawCalls, long staticSubmittedTriangles,
                              int clusterNodesTested, int candidateClusters, int visibleClusters,
                              int culledClusters, int rangeLimitFallbacks,
                              int requestedLod0, int requestedLod1, int requestedLod2, int requestedLod3,
                              int actualLod0, int actualLod1, int actualLod2, int actualLod3,
                              int availableLod0, int availableLod1, int availableLod2, int availableLod3,
                              int lodGeometries, long lodSourceTriangles, long lodLevel1Triangles,
                              long lodLevel2Triangles, long lodLevel3Triangles,
                              long lodEligibleVertices, long lodLockedVertices,
                              int lodBuildFailures, long lodBuildNanos,
                              int buildingDraws, int fallbackDraws,
                              int deferredImmediateDraws, long immediateVertices, long immediateVertexLimit,
                              long bufferBytes) {
        public double lodLockedRatio() {
            return lodEligibleVertices == 0 ? 0.0 : (double) lodLockedVertices / lodEligibleVertices;
        }
    }
}
