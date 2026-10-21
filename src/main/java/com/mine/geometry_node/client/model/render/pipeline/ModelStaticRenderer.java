package com.mine.geometry_node.client.model.render.pipeline;

import com.mine.geometry_node.client.model.render.bvh.ModelIrisHeldLight;
import com.mine.geometry_node.client.model.render.bvh.ModelLightingReceiverTag;
import com.mine.geometry_node.client.model.render.bvh.ModelLightingProfiler;
import com.mine.geometry_node.client.model.render.bvh.ModelResidentLight;
import com.mine.geometry_node.client.model.render.bvh.ModelSpatialLightCache;
import com.mine.geometry_node.client.model.render.diagnostic.ModelRenderMetrics;
import com.mine.geometry_node.client.model.render.geometry.ModelClusterAsset;
import com.mine.geometry_node.client.model.render.geometry.ModelCullingBounds;
import com.mine.geometry_node.client.model.render.geometry.ModelGeometryUploadRanges;
import com.mine.geometry_node.client.model.render.geometry.ModelGeometryUploadRanges.IndexRange;
import com.mine.geometry_node.client.model.render.geometry.ModelRenderAsset;
import com.mine.geometry_node.client.model.render.geometry.ModelRenderInstance;
import com.mine.geometry_node.client.model.render.lod.model.ModelLodMetrics;
import com.mine.geometry_node.client.model.render.lod.model.ModelLodObject;
import com.mine.geometry_node.client.model.render.lod.model.ModelLodPolicy;
import com.mine.geometry_node.client.model.render.lod.model.ModelObjectLodSelection;
import com.mine.geometry_node.client.model.render.resource.ModelIrisUploadBudget;
import com.mine.geometry_node.client.model.render.resource.ModelMaterialUploadQueue;
import com.mine.geometry_node.client.model.render.resource.ModelRenderBudget;
import com.mine.geometry_node.client.model.render.shadow.ModelIrisShadowUv;
import com.mine.geometry_node.client.model.render.shadow.ModelShadowDistancePolicy;
import com.mine.geometry_node.client.model.render.shadow.ModelShadowCulling;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.client.model.compat.IrisModelBridge;
import com.mine.geometry_node.core.engine.system.model.asset.GltfAsset;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import com.mojang.blaze3d.ProjectionType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import java.util.*;

/**
 * P4 static backend. Opaque geometry uses resident indexed GPU buffers and material batches;
 * transparent geometry keeps the deterministic P2 triangle sort as a safe fallback.
 */
public final class ModelStaticRenderer {
    private static volatile ModelRenderInstanceSource instanceSource = ModelRenderInstanceSource.EMPTY;

    /** Installed by client bootstrap before any render callbacks or diagnostic commands run. */
    public static void setInstanceSource(ModelRenderInstanceSource source) {
        instanceSource = Objects.requireNonNull(source);
    }

    static void refreshLoadedCounts() {
        var loaded = instanceSource.loadedCounts();
        METRICS.modelLodMetrics().setLoadedCounts(loaded.instances(), loaded.objects());
    }
    private static final int MAX_RUN_TRIANGLES = 16_384;
    private static long opaqueSubmissions, transparentTriangles, transparentDraws;
    private static String lastFailure = "";
    private static long frame;
    private static ModelRenderBudget budget = ModelRenderBudget.DEFAULT;
    private static final ModelRenderMetrics METRICS = new ModelRenderMetrics();
    /** Keep immutable native meshes on the GPU; the collector remains an explicit compatibility fallback. */
    private static final boolean DIRECT_GPU_ENABLED = Boolean.parseBoolean(System.getProperty("geometry_node.model.direct_gpu", "true"));
    private static final ModelSpatialLightCache LIGHT_CACHE = new ModelSpatialLightCache();
    private static final ModelCullingBounds CULLING_BOUNDS = new ModelCullingBounds();
    private static final IrisModelBridge IRIS = new IrisModelBridge();
    private static boolean irisInitialized;
    private static long shadowDraws, shadowCasters;
    private static float shadowToleranceMin = Float.POSITIVE_INFINITY, shadowToleranceMax;
    private static long shadowCallbacks;
    private static long shadowFullyInsidePrimitives, shadowClusterTestsSkipped;
    private static boolean shadowProjectionConsumed;
    private static ProjectionMatrixBuffer shadowProjection;
    private static String shadowTargetStatus = "UNTESTED";
    private static String shadowLastFailure = "";
    private static Map<BatchKey, List<DirectDraw>> queuedDirect = Map.of();
    private static List<IrisDraw> queuedIris = List.of();
    private static List<IrisDraw> queuedIrisImmediate = List.of();
    private static boolean irisResidentEnabled = !Boolean.getBoolean("geometry_node.model.iris_immediate");
    private static boolean drawingShadow, drawingTransparent, drawingResidentImmediate, awaitingResolvedTransparency;
    private static long mainCpuNanos;
    private static int mainDrawCount, mainPrimitiveCount;
    private static long irisMainDraws, irisShadowDraws, irisDeferredDraws;
    private static long residencyFrame = Long.MIN_VALUE;
    /** Main viewer position used by Shadow distance policy; Shadow callback has a separate light camera. */
    private static Vec3 mainCameraPosition = Vec3.ZERO;

    private static void beginResidencyFrame(long nextFrame) {
        ModelIrisUploadBudget.INSTANCE.beginFrame(nextFrame, budget);
        if (residencyFrame != nextFrame) {
            ModelMaterialUploadQueue.tick();
            residencyFrame = nextFrame;
            ModelRenderAsset.residency().beginFrame();
        }
    }
    private record IrisDraw(ModelRenderAsset asset, ModelRenderAsset.Primitive primitive,
                            ModelRenderAsset.GpuPrimitive mesh, Matrix4f transform, int packedLight,
                            boolean mirrored, List<IndexRange> ranges, float shadowTolerance, int receiverSlot) {}

    public static void setIrisResidentEnabled(boolean enabled) {
        RenderSystem.assertOnRenderThread();
        irisResidentEnabled = enabled;
        queuedIris = List.of(); queuedIrisImmediate = List.of(); queuedDirect = Map.of();
    }

    /** Counts buffers actually passed through RenderType's immediate uploader, including collector fallback. */
    public static void immediateGeometryUploaded(long bytes, long nanos) {
        METRICS.immediateGeometryUpload(drawingShadow ? ModelRenderMetrics.Pass.SHADOW : ModelRenderMetrics.Pass.MAIN, bytes, nanos);

    }

    public static void immediateDrawFinished(long nanos) {
        // Shadow and explicitly sorted transparent draws are already inside timed scopes.
        if (!drawingShadow && !drawingTransparent && !drawingResidentImmediate) mainCpuNanos += nanos;
    }

    public static void immediateUniformUploaded(long bytes) {
        METRICS.dynamicUniformUpload(drawingShadow ? ModelRenderMetrics.Pass.SHADOW : ModelRenderMetrics.Pass.MAIN, bytes);
    }

    /** Called before processRenderUploads so per-frame upload counters are not immediately erased. */
    public static void beginMainFrame() {
        RenderSystem.assertOnRenderThread();
        com.mine.geometry_node.client.model.render.diagnostic.ModelDrawGpuTimings.poll();
        frame++; METRICS.beginFrame(frame); beginResidencyFrame(frame); ModelLightingProfiler.onFrameStart();
        mainCameraPosition = Minecraft.getInstance().gameRenderer.getMainCamera().position();
        if (instanceSource.hasVisibleInstances()) LIGHT_CACHE.beginFrame(Minecraft.getInstance().level);
        mainCpuNanos = 0; mainDrawCount = mainPrimitiveCount = 0;
    }

    public static void resetPerformanceMetrics() {
        RenderSystem.assertOnRenderThread();
        METRICS.reset();
        com.mine.geometry_node.client.model.render.diagnostic.ModelShaderGpuTimings.clearSamples();
        com.mine.geometry_node.client.model.render.diagnostic.ModelDrawGpuTimings.reset();
        mainCpuNanos = 0; mainDrawCount = mainPrimitiveCount = 0;
    }

    private record DirectDraw(ModelRenderAsset asset, ModelRenderAsset.GpuPrimitive mesh, ModelRenderAsset.MaterialBinding binding,
                              Matrix4f transform, ModelRenderAsset.Primitive primitive, ModelRenderRendererLight light,
                              boolean mirrored, List<IndexRange> ranges) {}
    private record FallbackDraw(ModelRenderAsset.Primitive primitive, Matrix4f transform, boolean mirrored, int packedLight, RenderType type, boolean iris,
                                List<IndexRange> ranges) {}
    private record Triangle(FallbackDraw draw, ModelRenderInstance instance, int nodeIndex, int offset, float depth) {}
    private record BatchKey(ModelRenderAsset.MaterialBinding binding) {}
    record ModelRenderRendererLight(int packed, float factor) {}

    private ModelStaticRenderer() {}

    public static void setBudget(ModelRenderBudget next) { budget = Objects.requireNonNull(next); ModelRenderAsset.residency().setBudget(next); }
    public static ModelRenderBudget budget() { return budget; }
    public static ModelRenderMetrics metrics() { return METRICS; }

    public record ShadowHudSnapshot(boolean available, boolean toleranceSupported, float minTolerance, float maxTolerance) {}

    public static ShadowHudSnapshot shadowHudSnapshot() {
        boolean available = IRIS.active() && IRIS.callbackRegistered();
        boolean drawn = available && ModelShadowDistancePolicy.enabled() && shadowCasters > 0
                && Float.isFinite(shadowToleranceMin);
        return new ShadowHudSnapshot(available, ModelIrisShadowUv.toleranceSupported(),
                drawn ? shadowToleranceMin : -1, drawn ? shadowToleranceMax : -1);
    }
    public static String stage() {
        if (!irisInitialized || !IRIS.available()) return "P4 STATIC_RESIDENT; client-local instances";
        return IRIS.active() ? "P7 LABPBR_BOUND; visual verification pending" : "P7 NATIVE_PBR_APPROXIMATION; visual verification pending";
    }

    /** Initializes the optional Iris adapter on the render thread. */
    public static void initializeIris() {
        RenderSystem.assertOnRenderThread();
        if (irisInitialized) return;
        irisInitialized = true;
        IRIS.initialize(ModelStaticPipelines.irisMainPipelines(), ModelStaticPipelines.irisTranslucentPipelines(),
                ModelStaticPipelines.irisEmissivePipelines(), ModelStaticPipelines.irisShadowPipelines(), ModelStaticRenderer::drawShadow);
    }

    /** Retains process-wide Iris mappings after F3+T or a shaderpack reload. */
    public static void refreshIris() {
        RenderSystem.assertOnRenderThread();
        if (irisInitialized) IRIS.refresh();
    }
    public static void pollIrisLifecycle() {
        RenderSystem.assertOnRenderThread();
        if (IRIS.pollLifecycle()) {
            instanceSource.invalidateRenderResources("iris_pipeline_changed");
            closeShadowResources();
        }
    }

    /** Versioned spatial-light invalidation contract; render-thread only and absolute world block coordinates. */
    /** Reconstructable lookups only; excludes queued draws, live model definitions and shader bindings. */
    public static void clearLookupCaches() {
        RenderSystem.assertOnRenderThread();
        LIGHT_CACHE.invalidateAll("cache_clear");
        LOD_HISTORY.clear();
        ModelTransparentRenderer.clear();
        ModelIrisHeldLight.clear();
    }

    public static com.mine.geometry_node.core.cache.CacheStats lookupCacheStats() {
        RenderSystem.assertOnRenderThread();
        long light = ((Number) LIGHT_CACHE.report().get("cachedSamples")).longValue();
        long lod = LOD_HISTORY.values().stream().mapToLong(Map::size).sum();
        var transparent = ModelTransparentRenderer.report();
        long runs = ((Number) transparent.get("currentDrawRuns")).longValue();
        // GPU buffers are accounted once by the residency ledger, not again here.
        long staging = ((Number) transparent.get("cpuStagingBytes")).longValue();
        return com.mine.geometry_node.core.cache.CacheStats.memory(light + lod + runs, staging + light * 20L);
    }

    public static void invalidateLight(BlockPos position) { RenderSystem.assertOnRenderThread(); LIGHT_CACHE.invalidate(Objects.requireNonNull(position)); }
    public static void invalidateLightRegion(BlockPos center, int radius, String reason) { RenderSystem.assertOnRenderThread(); LIGHT_CACHE.invalidateRegion(Objects.requireNonNull(center), radius, reason); }
    public static void invalidateAllLight(String reason) { RenderSystem.assertOnRenderThread(); LIGHT_CACHE.invalidateAll(reason); }

    public static void prepareLighting(Vec3 camera, Frustum frustum, List<ModelRenderInstance> instances) {
        RenderSystem.assertOnRenderThread();
        mainCameraPosition = camera;
        com.mine.geometry_node.client.runtime.light.render.NativeTerrainLights.setEnabled(!(IRIS.available() && IRIS.active()));
        com.mine.geometry_node.client.runtime.light.render.NodeLightRenderer.prepare(camera);
        long lightingStarted = System.nanoTime();
        try { ModelIrisHeldLight.update(instances, camera, IRIS.available() && IRIS.active(), instance ->
                !isOutsideFrustum(instance.asset().sceneBounds(instance.scene()),
                        instance.transform().relativeTo(camera.x, camera.y, camera.z), camera, frustum)); }
        finally { METRICS.recordLightingCpu(System.nanoTime() - lightingStarted); }
    }

    public static void submit(PoseStack ignoredHostStack, SubmitNodeCollector collector, Vec3 camera, Frustum frustum, List<ModelRenderInstance> instances) {
        RenderSystem.assertOnRenderThread();
        refreshLoadedCounts();
        if (instances.isEmpty()) { queuedIris = List.of(); queuedIrisImmediate = List.of(); queuedDirect = Map.of(); return; }
        long started = System.nanoTime();
        boolean iris = IRIS.available() && IRIS.active();
        List<IrisDraw> irisDraws = new ArrayList<>();
        List<IrisDraw> irisImmediate = new ArrayList<>();
        Map<BatchKey, List<DirectDraw>> direct = new LinkedHashMap<>();
        Map<RenderType, List<FallbackDraw>> fallback = new LinkedHashMap<>();
        // Protect all visible assets before any LOD upload can request residency eviction.
        for (var instance : instances) if (instance.visible() && instance.asset().gpuResident()) {
            instance.asset().touch();
            pinPreviousObjectLevels(instance, iris, iris ? irisResidentEnabled : DIRECT_GPU_ENABLED);
        }
        try {
            for (ModelRenderInstance instance : instances) {
                if (!instance.visible() || !instance.asset().gpuResident()) continue;
                ModelRenderAsset asset = instance.asset(); boolean assetVisible = false;
                Matrix4f instanceTransform = instance.transform().relativeTo(camera.x, camera.y, camera.z);
                if (isCulled(asset.sceneBounds(instance.scene()), instanceTransform, camera, frustum)) continue;
                InstanceLodState history = objectHistory(instance);
                for (ModelLodObject object : asset.lodObjects(instance.scene())) {
                    if (isCulled(object.bounds(), instanceTransform, camera, frustum)) continue;
                    var state = selectObjectLod(instance, object, history, instanceTransform, false, iris,
                            iris ? irisResidentEnabled : DIRECT_GPU_ENABLED);
                    state.mainVisibleFrame = frame;
                    if (state.lod.selection().deferred()) { recordObjectLod(history, state, object, false); continue; }
                    boolean objectSubmitted = false;
                    for (ModelRenderAsset.Occurrence occurrence : object.occurrences(state.lod.selection().level())) {
                        Matrix4f transform = new Matrix4f(instanceTransform).mul(occurrence.transform());
                        boolean mirrored = transform.determinant3x3() < 0.0f;
                        for (ModelRenderAsset.Primitive primitive : occurrence.primitives()) {
                            ClusterCullResult clusterCull = cullClusters(primitive, transform, camera, frustum);
                            METRICS.recordClusterCulling(ModelRenderMetrics.Pass.MAIN, clusterCull.candidates(), clusterCull.visible(), clusterCull.culled());
                            if (clusterCull.allCulled()) { METRICS.culled(); continue; }
                            if (mainPrimitiveCount >= budget.maxVisiblePrimitives()) throw new IllegalStateException("visible model primitive budget exceeded");
                            METRICS.visible(); mainPrimitiveCount++;
                            if (!assetVisible) { asset.touch(); assetVisible = true; }
                            if (primitive.material.alphaMode() == GltfAsset.AlphaMode.BLEND) continue;
                            ModelRenderRendererLight light = sampleLight(primitive, transform, camera);
                            boolean residentBackend = state.lod.resident();
                            var geometry = residentBackend ? asset.residentGeometry(primitive, iris) : null;
                            var mesh = geometry == null ? null : geometry.mesh();
                            var binding = !iris ? asset.binding(primitive.material) : null;
                            if (!iris && binding == null) mesh = null;
                            var ranges = ModelGeometryUploadRanges.split(clusterCull.ranges(), primitive.indices.length,
                                    mesh == null ? 0 : geometry.uploadedIndices());
                            if (!ranges.resident().isEmpty() || !ranges.immediate().isEmpty()) objectSubmitted = true;
                            if (!ranges.resident().isEmpty()) {
                                if (iris) irisDraws.add(new IrisDraw(asset, primitive, mesh, transform,
                                        light.packed(), mirrored, ranges.resident(), 0, ModelIrisHeldLight.receiverSlot(instance.id())));
                                else direct.computeIfAbsent(new BatchKey(binding), key -> new ArrayList<>())
                                        .add(new DirectDraw(asset, mesh, binding, new Matrix4f(transform), primitive, light, mirrored, ranges.resident()));
                            }
                            if (!ranges.immediate().isEmpty()) {
                                if (iris) {
                                    irisImmediate.add(new IrisDraw(asset, primitive, null, new Matrix4f(transform),
                                            light.packed(), mirrored, ranges.immediate(), 0, ModelIrisHeldLight.receiverSlot(instance.id())));
                                    continue;
                                }
                                RenderType type = asset.renderType(primitive.material);
                                fallback.computeIfAbsent(type, key -> new ArrayList<>())
                                        .add(new FallbackDraw(primitive, new Matrix4f(transform), mirrored, light.packed(), type, false, ranges.immediate()));
                            }
                        }
                    }
                    if (objectSubmitted) recordObjectLod(history, state, object, false);
                }
            }
            queuedDirect = Map.copyOf(direct);
            queuedIris = List.copyOf(irisDraws);
            queuedIrisImmediate = List.copyOf(irisImmediate);
            submitFallbackOpaque(collector, fallback);
        } catch (RuntimeException error) {
            failed(error);
        } finally {
            mainCpuNanos += System.nanoTime() - started;
            var resident = ModelRenderAsset.residency().snapshot();
            METRICS.residency(resident.cpuBytes(), resident.gpuBytes(), resident.textureBytes());
        }
    }

    /** Draw only at the host stage boundary, never from a collector emission callback. */
    public static void drawResidentOpaque(Matrix4fc view) {
        RenderSystem.assertOnRenderThread();
        if (queuedDirect.isEmpty() && queuedIris.isEmpty() && queuedIrisImmediate.isEmpty()) return;
        int timer = com.mine.geometry_node.client.model.render.diagnostic.ModelDrawGpuTimings.begin("main_opaque", frame);
        try { drawResidentOpaqueMeasured(view); }
        finally { com.mine.geometry_node.client.model.render.diagnostic.ModelDrawGpuTimings.end(timer); }
    }

    private static void drawResidentOpaqueMeasured(Matrix4fc view) {
        RenderSystem.assertOnRenderThread();
        if (queuedDirect.isEmpty() && queuedIris.isEmpty() && queuedIrisImmediate.isEmpty()) return;
        long started = System.nanoTime();
        Map<BatchKey, List<DirectDraw>> batches = queuedDirect;
        List<IrisDraw> irisDraws = queuedIris;
        List<IrisDraw> immediate = queuedIrisImmediate;
        queuedDirect = Map.of(); queuedIris = List.of(); queuedIrisImmediate = List.of();
        try {
            if (IRIS.available() && IRIS.active() && irisResidentEnabled) {
                drawIrisResident(irisDraws, view, false);
            } else {
                drawDirectPass(batches, new Matrix4f(view), false, "P4 resident opaque");
            }
            if (IRIS.available() && IRIS.active()) drawIrisImmediate(immediate, view);
        } catch (RuntimeException error) {
            failed(error);
        } finally {
            mainCpuNanos += System.nanoTime() - started;
        }
    }

    private record ResolvedIrisDraw(IrisDraw source, ModelRenderAsset.GpuPrimitive mesh,
                                    com.mojang.blaze3d.pipeline.RenderPipeline pipeline,
                                    net.minecraft.resources.Identifier texture) {}

    private static void drawIrisImmediate(List<IrisDraw> draws, Matrix4fc view) {
        if (draws.isEmpty()) return;
        var modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        drawingResidentImmediate = true;
        try (ByteBufferBuilder arena = new ByteBufferBuilder(65536)) {
            modelView.set(view);
            for (IrisDraw draw : draws) {
                if (!draw.asset().gpuResident()) continue;
                RenderType type = draw.asset().irisRenderType(draw.primitive().material);
                PoseStack pose = new PoseStack();
                pose.mulPose(draw.transform());
                BufferBuilder builder = new BufferBuilder(arena, VertexFormat.Mode.TRIANGLES, type.format());
                for (IndexRange range : draw.ranges())
                    for (int offset = range.firstIndex(); offset < range.firstIndex() + range.count(); offset += 3)
                        emitTriangle(draw.primitive(), pose.last(), draw.mirrored(), draw.packedLight(), offset, builder, true);
                // RenderType's existing draw wrapper owns alpha and blend scopes.
                try (var receiver = ModelLightingReceiverTag.scope(draw.receiverSlot())) {
                    type.draw(builder.buildOrThrow());
                }
                opaqueSubmissions++; mainDrawCount++; METRICS.opaqueDraw();
            }
        } finally { drawingResidentImmediate = false; modelView.popMatrix(); }
    }

    private static boolean sharesIrisPass(ResolvedIrisDraw first, ResolvedIrisDraw next) {
        // Iris caches inverse/normal matrices and albedo sampler state for the pass.
        // Do not broaden to approximate transforms or different textures/pipelines.
        return first.pipeline() == next.pipeline() && first.texture().equals(next.texture())
                && first.source().transform().equals(next.source().transform());
    }

    private static int drawIrisResident(List<IrisDraw> draws, Matrix4fc view, boolean shadowPass) {
        if (draws.isEmpty()) return 0;
        ModelRenderMetrics.Pass metricPass = shadowPass ? ModelRenderMetrics.Pass.SHADOW : ModelRenderMetrics.Pass.MAIN;
        List<ResolvedIrisDraw> resolved = new ArrayList<>(draws.size());
        long resolveStarted = System.nanoTime();
        try {
            // Use the captured mesh and ranges; advancing uploads here would invalidate the split.
            for (IrisDraw draw : draws) {
                if (!draw.asset().gpuResident()) continue;
                draw.asset().touch();
                ModelRenderAsset.Primitive resolvedPrimitive = draw.primitive();
                ModelRenderAsset.GpuPrimitive mesh = draw.mesh();
                if (mesh == null) { irisDeferredDraws++; continue; }
                var pipeline = ModelRenderAsset.irisPipeline(resolvedPrimitive, shadowPass);
                if (!mesh.format().equals(pipeline.getVertexFormat()))
                    throw new IllegalStateException("Iris resident vertex layout changed within a draw");
                resolved.add(new ResolvedIrisDraw(draw, mesh, pipeline, draw.asset().binding(resolvedPrimitive.material).irisBase()));
            }
        } finally {
            METRICS.irisPhase(metricPass, ModelRenderMetrics.IrisPhase.GEOMETRY_RESOLVE, System.nanoTime() - resolveStarted);
        }
        if (resolved.isEmpty()) return 0;
        Minecraft minecraft = Minecraft.getInstance();
        var target = minecraft.getMainRenderTarget();
        GpuTextureView color = RenderSystem.outputColorTextureOverride != null ? RenderSystem.outputColorTextureOverride : target.getColorTextureView();
        GpuTextureView depth = RenderSystem.outputDepthTextureOverride != null ? RenderSystem.outputDepthTextureOverride : target.getDepthTextureView();
        if (color == null || depth == null) throw new IllegalStateException("Model render target is unavailable");
        var stack = RenderSystem.getModelViewStack();
        stack.pushMatrix();
        int count = 0;
        Matrix4f modelView = new Matrix4f(), identity = new Matrix4f();
        Vector4f white = new Vector4f(1.0f);
        Vector3f zero = new Vector3f();
        var sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        var overlay = minecraft.gameRenderer.overlayTexture().getTextureView();
        var lightmap = minecraft.gameRenderer.lightmap();
        try {
            int begin = 0;
            while (begin < resolved.size()) {
                ResolvedIrisDraw first = resolved.get(begin);
                int end = begin + 1;
                while (end < resolved.size() && sharesIrisPass(first, resolved.get(end))) end++;
                long started = System.nanoTime();
                modelView.set(view).mul(first.source().transform());
                stack.set(modelView);
                GpuBufferSlice dynamic;
                try {
                    dynamic = RenderSystem.getDynamicUniforms().writeTransform(modelView, white, zero, identity);
                    METRICS.dynamicUniformUpload(metricPass, dynamic.length());
                } finally {
                    METRICS.irisPhase(metricPass, ModelRenderMetrics.IrisPhase.UNIFORM_PREPARE, System.nanoTime() - started);
                }
                // Resolve lazy host texture/sampler access before entering the GPU pass as well.
                AbstractTexture baseTexture = minecraft.getTextureManager().getTexture(first.texture());
                var baseView = baseTexture.getTextureView();
                var baseSampler = baseTexture.getSampler();
                RenderPass pass = null;
                int completed = 0;
                try {
                    started = System.nanoTime();
                    try {
                        pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                                () -> "GeometryNode P5 resident " + (shadowPass ? "shadow" : "main"),
                                color, OptionalInt.empty(), depth, OptionalDouble.empty());
                        pass.setPipeline(first.pipeline());
                        RenderSystem.bindDefaultUniforms(pass);
                        pass.setUniform("DynamicTransforms", dynamic);
                        pass.bindTexture("Sampler0", baseView, baseSampler);
                        pass.bindTexture("Sampler1", overlay, sampler);
                        pass.bindTexture("Sampler2", lightmap, sampler);
                    } finally {
                        METRICS.irisPhase(metricPass, ModelRenderMetrics.IrisPhase.PASS_OPEN_AND_BIND, System.nanoTime() - started);
                    }
                    for (int i = begin; i < end; i++) {
                        ResolvedIrisDraw item = resolved.get(i);
                        IrisDraw draw = item.source();
                        if (shadowPass) ModelIrisShadowUv.tolerance(draw.shadowTolerance());
                        var mesh = item.mesh();
                        started = System.nanoTime();
                        try {
                            pass.setVertexBuffer(0, mesh.vertices());
                            pass.setIndexBuffer(mesh.indices(draw.mirrored()), VertexFormat.IndexType.INT);
                            try (var receiver = ModelLightingReceiverTag.scope(shadowPass ? -1 : draw.receiverSlot());
                                 var alpha = ModelIrisAlphaTest.scope(ModelRenderAsset.alphaPolicy(draw.primitive().material));
                                 var blend = ModelIrisBlendOverride.scope(false)) {
                                int light = draw.primitive().material.unlit() ? ModelRenderAsset.FULL_LIGHT : draw.packedLight();
                                for (IndexRange range : draw.ranges()) {
                                    ModelResidentLight.draw(pass, mesh.format(), light, range.firstIndex(), range.count());
                                    count++; completed++;
                                    if (shadowPass) { irisShadowDraws++; shadowDraws++; }
                                    else { irisMainDraws++; opaqueSubmissions++; mainDrawCount++; METRICS.opaqueDraw(); }
                                }
                            }
                        } finally {
                            METRICS.irisPhase(metricPass, i == begin ? ModelRenderMetrics.IrisPhase.FIRST_DRAW_WITH_IRIS_SETUP
                                    : ModelRenderMetrics.IrisPhase.SUBSEQUENT_DRAW, System.nanoTime() - started);
                        }
                    }
                } finally {
                    if (pass != null) {
                        started = System.nanoTime();
                        try { pass.close(); }
                        finally {
                            METRICS.irisPhase(metricPass, ModelRenderMetrics.IrisPhase.PASS_CLOSE, System.nanoTime() - started);
                            METRICS.irisRenderPass(metricPass, completed);
                        }
                    }
                }
                begin = end;
            }
        } finally {
            stack.popMatrix();
        }
        return count;
    }

    /**
     * Iris calls this while its shadow target and shadow matrices are active.
     * We submit the same immutable source geometry as the main pass, with no
     * main-camera frustum test and no second animation/pose evaluation. BLEND is
     * intentionally excluded; opaque and MASK geometry are valid depth casters.
     */
    private static void drawShadow(Matrix4f view, Matrix4f projection, double cameraX, double cameraY, double cameraZ, float tickDelta) {
        RenderSystem.assertOnRenderThread();
        long started = System.nanoTime();
        shadowCallbacks++;
        shadowCasters = 0;
        shadowToleranceMin = Float.POSITIVE_INFINITY; shadowToleranceMax = 0;
        if (!ModelShadowDistancePolicy.enabled() || !instanceSource.hasVisibleInstances()) {
            shadowProjectionConsumed = false;
            METRICS.recordPass(ModelRenderMetrics.Pass.SHADOW, System.nanoTime() - started, 0, 0);
            return;
        }
        pollIrisLifecycle();
        long previousShadowDraws = shadowDraws;
        int gpuTimer = -1;
        int submitted = 0;
        List<IrisDraw> draws = new ArrayList<>();
        List<IrisDraw> immediateDraws = new ArrayList<>();
        beginResidencyFrame(frame + 1);
        METRICS.modelLodMetrics().beginFrame(frame + 1);
        LIGHT_CACHE.beginFrame(Minecraft.getInstance().level);
        var savedProjection = RenderSystem.getProjectionMatrixBuffer();
        var savedProjectionType = RenderSystem.getProjectionType();
        var modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        drawingShadow = true;
        try (var shadowUv = ModelIrisShadowUv.scope()) {
            Vec3 camera = new Vec3(cameraX, cameraY, cameraZ);
            mainCameraPosition = Minecraft.getInstance().gameRenderer.getMainCamera().position();
            Frustum shadowFrustum = new Frustum(view, projection);
            shadowFrustum.prepare(cameraX, cameraY, cameraZ);
            ModelShadowCulling shadowContainment = new ModelShadowCulling(view, projection, cameraX, cameraY, cameraZ);
            // This list is independent of main-camera culling; offscreen casters remain eligible.
            var shadowInstances = instanceSource.shadowInstances();
            for (var instance : shadowInstances) if (instance.visible() && instance.asset().gpuResident()) {
                instance.asset().touch();
                pinPreviousObjectLevels(instance, true, irisResidentEnabled);
            }
            for (ModelRenderInstance instance : shadowInstances) {
                if (!instance.visible() || !instance.asset().gpuResident()) continue;
                ModelShadowDistancePolicy.Decision distanceDecision = shadowDistanceDecision(instance, mainCameraPosition);
                METRICS.recordShadowModelDecision(true, !distanceDecision.disabled(), distanceDecision.disabled(),
                        distanceDecision.reason(), distanceDecision.distance(), distanceDecision.quality(), distanceDecision.tier());
                if (distanceDecision.disabled()) continue;
                float shadowTolerance = ModelShadowDistancePolicy.tolerance(distanceDecision.distance());
                Matrix4f instanceTransform = instance.transform().relativeTo(cameraX, cameraY, cameraZ);
                Matrix4f qualityTransform = instance.transform().relativeTo(mainCameraPosition.x, mainCameraPosition.y, mainCameraPosition.z);
                InstanceLodState history = objectHistory(instance);
                for (ModelLodObject object : instance.asset().lodObjects(instance.scene())) {
                    if (object.triangles(0) == 0 || isCulled(object.bounds(), instanceTransform, camera, shadowFrustum)) continue;
                    var state = selectObjectLod(instance, object, history, qualityTransform, true, true, irisResidentEnabled);
                    if (state.lod.selection().deferred()) { recordObjectLod(history, state, object, true); continue; }
                    boolean objectSubmitted = false;
                    for (ModelRenderAsset.Occurrence occurrence : object.occurrences(state.lod.selection().level())) {
                        Matrix4f transform = new Matrix4f(instanceTransform).mul(occurrence.transform());
                        boolean mirrored = transform.determinant3x3() < 0;
                        for (ModelRenderAsset.Primitive primitive : occurrence.primitives()) {
                            if (primitive.material.alphaMode() == GltfAsset.AlphaMode.BLEND) continue;
                            ClusterCullResult clusterCull;
                            var clusters = primitive.clusters();
                            if (clusters != null && clusters.size() > 1
                                    && shadowContainment.fullyInside(primitive.bounds, transform, budget.maxDrawDistance())) {
                                clusterCull = new ClusterCullResult(clusters.size(), clusters.size(), 0, fullRange(primitive));
                                shadowFullyInsidePrimitives++;
                                shadowClusterTestsSkipped += clusters.size();
                            } else clusterCull = cullClusters(primitive, transform, camera, shadowFrustum);
                            METRICS.recordClusterCulling(ModelRenderMetrics.Pass.SHADOW, clusterCull.candidates(), clusterCull.visible(), clusterCull.culled());
                            if (clusterCull.allCulled()) continue;
                            if (draws.size() + immediateDraws.size() >= budget.maxVisiblePrimitives()) throw new IllegalStateException("shadow primitive budget exceeded");
                            instance.asset().touch();
                            int light = sampleLight(primitive, transform, camera).packed();
                            var geometry = irisResidentEnabled ? instance.asset().residentGeometry(primitive, true) : null;
                            var mesh = geometry == null ? null : geometry.mesh();
                            var ranges = ModelGeometryUploadRanges.split(clusterCull.ranges(), primitive.indices.length,
                                    mesh == null ? 0 : geometry.uploadedIndices());
                            if (!ranges.resident().isEmpty()) draws.add(new IrisDraw(instance.asset(), primitive, mesh,
                                    transform, light, mirrored, ranges.resident(), shadowTolerance, -1));
                            if (!ranges.immediate().isEmpty()) immediateDraws.add(new IrisDraw(instance.asset(), primitive, null,
                                    transform, light, mirrored, ranges.immediate(), shadowTolerance, -1));
                            if (!ranges.resident().isEmpty() || !ranges.immediate().isEmpty()) {
                                objectSubmitted = true;
                                shadowToleranceMin = Math.min(shadowToleranceMin, shadowTolerance);
                                shadowToleranceMax = Math.max(shadowToleranceMax, shadowTolerance);
                            }
                        }
                    }
                    if (objectSubmitted) recordObjectLod(history, state, object, true);
                }
            }
            if (!draws.isEmpty() || !immediateDraws.isEmpty()) {
                gpuTimer = com.mine.geometry_node.client.model.render.diagnostic.ModelDrawGpuTimings.begin("shadow", frame + 1);
                if (shadowProjection == null) shadowProjection = new ProjectionMatrixBuffer("GeometryNode model shadows");
                GpuBufferSlice projectionBuffer = shadowProjection.getBuffer(projection);
                METRICS.dynamicUniformUpload(ModelRenderMetrics.Pass.SHADOW, projectionBuffer.length());
                RenderSystem.setProjectionMatrix(projectionBuffer, ProjectionType.ORTHOGRAPHIC);
                shadowProjectionConsumed = true;
                modelView.set(view);
                if (!draws.isEmpty()) {
                    submitted = drawIrisResident(draws, view, true);
                }
                if (!immediateDraws.isEmpty()) {
                    try (ByteBufferBuilder arena = new ByteBufferBuilder(65536)) {
                        for (IrisDraw draw : immediateDraws) {
                            ModelIrisShadowUv.tolerance(draw.shadowTolerance());
                            PoseStack pose = new PoseStack(); pose.mulPose(draw.transform());
                            RenderType type = draw.asset().irisShadowRenderType(draw.primitive().material);
                            BufferBuilder builder = new BufferBuilder(arena, VertexFormat.Mode.TRIANGLES, type.format());
                            for (IndexRange range : draw.ranges())
                                for (int offset = range.firstIndex(); offset < range.firstIndex() + range.count(); offset += 3)
                                    emitTriangle(draw.primitive(), pose.last(), draw.mirrored(), draw.packedLight(), offset, builder, true);
                            type.draw(builder.buildOrThrow());
                            submitted++; shadowDraws++;
                        }
                    }
                }
            }
            shadowCasters = submitted;
            shadowTargetStatus = "IRIS_API_ROUTED_UNVERIFIED";
            shadowLastFailure = "";
        } catch (RuntimeException error) {
            shadowTargetStatus = "FAILED";
            shadowLastFailure = error.toString();
            failed(error);
        } finally {
            submitted = (int) (shadowDraws - previousShadowDraws);
            com.mine.geometry_node.client.model.render.diagnostic.ModelDrawGpuTimings.end(gpuTimer);
            shadowCasters = submitted;
            drawingShadow = false;
            modelView.popMatrix();
            RenderSystem.setProjectionMatrix(savedProjection, savedProjectionType);
            METRICS.recordPass(ModelRenderMetrics.Pass.SHADOW, System.nanoTime() - started, submitted, draws.size() + immediateDraws.size());
        }
    }

    /** Drop queued work and per-scene caches immediately; GPU buffers retire via host fences. */
    public static void clearSceneResources() {
        closeShadowResources();
        LIGHT_CACHE.beginFrame(null); // Do not retain an unloaded client world through the application adapter.
        LIGHT_CACHE.invalidateAll("scene_cleared");
        shadowCasters = 0; shadowProjectionConsumed = false;
        mainCpuNanos = 0; mainDrawCount = mainPrimitiveCount = 0;
        METRICS.beginFrame(frame);
        METRICS.recordPass(ModelRenderMetrics.Pass.MAIN, 0, 0, 0);
        METRICS.recordPass(ModelRenderMetrics.Pass.SHADOW, 0, 0, 0);
    }

    public static void closeShadowResources() {
        RenderSystem.assertOnRenderThread();
        com.mine.geometry_node.client.model.render.diagnostic.ModelDrawGpuTimings.clear();
        shadowToleranceMin = Float.POSITIVE_INFINITY; shadowToleranceMax = 0; shadowCasters = 0;
        ModelLightingProfiler.cancel();
        ModelIrisHeldLight.clear();
        if (shadowProjection != null) { shadowProjection.close(); shadowProjection = null; }
        queuedIris = List.of(); queuedIrisImmediate = List.of(); queuedDirect = Map.of();
        ModelTransparentRenderer.clear();
        LOD_HISTORY.clear();
        awaitingResolvedTransparency = false;
        ModelIrisUploadBudget.INSTANCE.reset();
        residencyFrame = Long.MIN_VALUE;
    }

    private static void drawDirectPass(Map<BatchKey, List<DirectDraw>> batches, Matrix4f baseView, boolean shadowPass, String label) {
        if (batches.isEmpty()) return;
        Minecraft minecraft = Minecraft.getInstance();
        var target = minecraft.getMainRenderTarget();
        boolean colorOverride = RenderSystem.outputColorTextureOverride != null;
        boolean depthOverride = RenderSystem.outputDepthTextureOverride != null;
        GpuTextureView color = colorOverride ? RenderSystem.outputColorTextureOverride : target.getColorTextureView();
        GpuTextureView depth = depthOverride ? RenderSystem.outputDepthTextureOverride : target.getDepthTextureView();
        if (shadowPass) {
            shadowTargetStatus = colorOverride && depthOverride ? "IRIS_OUTPUT_OVERRIDE_UNVERIFIED" : "UNSUPPORTED_TARGET";
            if (!(colorOverride && depthOverride)) {
                shadowLastFailure = "Iris shadow callback did not expose both output color/depth overrides";
                // Never draw shadow geometry into the main world target.  A callback
                // without both attachments is an unsupported target, not a safe fallback.
                return;
            }
        }
        if (color == null || depth == null) {
            if (shadowPass) {
                shadowTargetStatus = "UNAVAILABLE";
                shadowLastFailure = "No color/depth target available for Iris shadow callback";
            }
            return;
        }
        var modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        try {
            modelViewStack.set(baseView);
            for (Map.Entry<BatchKey, List<DirectDraw>> entry : batches.entrySet()) {
                ModelRenderAsset.MaterialBinding binding = entry.getKey().binding();
                List<GpuBufferSlice> transforms = new ArrayList<>();
                for (DirectDraw draw : entry.getValue()) {
                    // Opaque native draws use the exact collector lightmap coordinates. A scalar
                    // brightness approximation would lose sky/block tint and darken materials.
                    int packedLight = draw.light().packed();
                    GpuBufferSlice dynamic = RenderSystem.getDynamicUniforms().writeTransform(new Matrix4f(baseView).mul(draw.transform()),
                            new Vector4f(1.0f), new Vector3f(packedLight & 65535, packedLight >>> 16, 1), draw.transform());
                    transforms.add(dynamic);
                    METRICS.dynamicUniformUpload(shadowPass ? ModelRenderMetrics.Pass.SHADOW : ModelRenderMetrics.Pass.MAIN,
                            dynamic.length());
                }
                try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                    () -> "GeometryNode " + label, color, OptionalInt.empty(), depth, OptionalDouble.empty())) {
                pass.setPipeline(ModelStaticPipelines.pipeline(false, binding.doubleSided()));
                RenderSystem.bindDefaultUniforms(pass);
                bind(pass, minecraft, "BaseTexture", binding.base());
                bind(pass, minecraft, "EmissionTexture", binding.emission());
                bind(pass, minecraft, "NormalTexture", binding.normal());
                bind(pass, minecraft, "MetallicRoughnessTexture", binding.metallicRoughness());
                bind(pass, minecraft, "OcclusionTexture", binding.occlusion());
                bind(pass, minecraft, "MaterialData", binding.data());
                bind(pass, minecraft, "Sampler0", binding.base());
                bind(pass, minecraft, "Sampler1", binding.overlay());
                pass.bindTexture("Sampler2", minecraft.gameRenderer.lightmap(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
                int transformIndex = 0;
                try (var nodeLighting = !shadowPass && com.mine.geometry_node.client.runtime.light.render.NodeLightRenderer.active()
                        ? com.mine.geometry_node.client.model.render.bvh.ModelNativeLight.nodeScope() : null) {
                for (DirectDraw draw : entry.getValue()) {
                    pass.setUniform("DynamicTransforms", transforms.get(transformIndex++));
                    pass.setVertexBuffer(0, draw.mesh().vertices());
                    pass.setIndexBuffer(draw.mesh().indices(draw.mirrored()), VertexFormat.IndexType.INT);
                    for (IndexRange range : draw.ranges()) {
                        pass.drawIndexed(0, range.firstIndex(), range.count(), 1);
                        if (shadowPass) { shadowDraws++; METRICS.opaqueDraw(); } else { opaqueSubmissions++; mainDrawCount++; METRICS.opaqueDraw(); }
                    }
                }
                }
                } catch (RuntimeException error) { failed(error); }
            }
        } finally {
            modelViewStack.popMatrix();
        }
    }

    private static void bind(RenderPass pass, Minecraft minecraft, String slot, net.minecraft.resources.Identifier id) {
        AbstractTexture texture = minecraft.getTextureManager().getTexture(id);
        pass.bindTexture(slot, texture.getTextureView(), texture.getSampler());
    }

    private static void submitFallbackOpaque(SubmitNodeCollector collector, Map<RenderType, List<FallbackDraw>> groups) {
        for (Map.Entry<RenderType, List<FallbackDraw>> entry : groups.entrySet()) {
            RenderType type = entry.getKey();
            collector.submitCustomGeometry(new PoseStack(), type, (captured, out) -> {
                long started = System.nanoTime();
                try {
                for (FallbackDraw draw : entry.getValue()) {
                    PoseStack local = new PoseStack(); local.mulPose(draw.transform());
                    for (IndexRange range : draw.ranges()) {
                        for (int offset = range.firstIndex(); offset < range.firstIndex() + range.count(); offset += 3)
                            emitTriangle(draw.primitive(), local.last(), draw.mirrored(), draw.packedLight(), offset, out, draw.iris());
                    }
                }
                } finally { mainCpuNanos += System.nanoTime() - started; }
            });
            opaqueSubmissions++; mainDrawCount++; METRICS.opaqueDraw();
        }
    }

    static void recordTransparentDraw(int triangles) {
        transparentDraws++; mainDrawCount++; transparentTriangles += triangles; METRICS.transparentDraw(triangles);
    }

    /** Called after vanilla translucent particles; P2 cross-sorts model triangles, not vanilla translucent geometry. */
    public static void drawTransparent(Matrix4fc view, Vec3 camera, Frustum frustum, List<ModelRenderInstance> instances) {
        RenderSystem.assertOnRenderThread();
        if (instances.isEmpty()) {
            awaitingResolvedTransparency = false;
            ModelTransparentRenderer.clear();
            METRICS.recordTransparentCpu(0);
            METRICS.recordPass(ModelRenderMetrics.Pass.MAIN, mainCpuNanos, mainDrawCount, mainPrimitiveCount);
            METRICS.endFrame();
            return;
        }
        if (IRIS.active()) {
            // ENTITIES_TRANSLUCENT may resolve to an opaque packed-Gbuffer program. Do not
            // overwrite that buffer after deferred lighting, even in compatibility mode.
            awaitingResolvedTransparency = true;
            return;
        }
        drawTransparentForward(view, camera, frustum, instances, false);
    }

    public static boolean needsResolvedTransparency() { return awaitingResolvedTransparency; }

    public static void drawResolvedTransparent(Matrix4fc view, Vec3 camera, Frustum frustum, List<ModelRenderInstance> instances) {
        RenderSystem.assertOnRenderThread();
        if (!awaitingResolvedTransparency) return;
        awaitingResolvedTransparency = false;
        drawTransparentForward(view, camera, frustum, instances, true);
    }

    private static void drawTransparentForward(Matrix4fc view, Vec3 camera, Frustum frustum,
                                               List<ModelRenderInstance> instances, boolean resolvedWorld) {
        int timer = com.mine.geometry_node.client.model.render.diagnostic.ModelDrawGpuTimings.begin("transparent", frame);
        try { drawTransparentForwardMeasured(view, camera, frustum, instances, resolvedWorld); }
        finally { com.mine.geometry_node.client.model.render.diagnostic.ModelDrawGpuTimings.end(timer); }
    }

    private static void drawTransparentForwardMeasured(Matrix4fc view, Vec3 camera, Frustum frustum,
                                                       List<ModelRenderInstance> instances, boolean resolvedWorld) {
        List<Triangle> triangles = new ArrayList<>();
        long started = System.nanoTime();
        drawingTransparent = true;
        try {
            if (irisResidentEnabled || resolvedWorld) {
                ModelTransparentRenderer.draw(view, camera, frustum, instances, resolvedWorld);
                return;
            }
            for (ModelRenderInstance instance : instances) {
                if (!instance.visible() || instance.asset().isClosed() || !instance.asset().gpuResident()) continue;
                ModelRenderAsset asset = instance.asset(); boolean assetVisible = false;
                for (ModelRenderAsset.Occurrence occurrence : asset.occurrences(instance.scene())) {
                    if (objectUploadDeferred(instance, occurrence.nodeIndex())) continue;
                    Matrix4f transform = instance.transform().relativeTo(camera.x, camera.y, camera.z).mul(occurrence.transform());
                    boolean mirrored = transform.determinant3x3() < 0.0f;
                    for (ModelRenderAsset.Primitive primitive : occurrence.primitives()) {
                        if (primitive.material.alphaMode() != GltfAsset.AlphaMode.BLEND) continue;
                        ClusterCullResult clusterCull = cullClusters(primitive, transform, camera, frustum);
                        METRICS.recordTransparentClusterCulling(clusterCull.candidates(), clusterCull.visible(), clusterCull.culled());
                        if (clusterCull.allCulled()) { METRICS.culled(); continue; }
                        if (!assetVisible) { asset.touch(); assetVisible = true; }
                        long triangleCount = primitive.indices.length / 3L;
                        if ((long) triangles.size() + triangleCount > budget.maxTransparentTriangles()) throw new IllegalStateException("P4 transparent sort budget exceeded: " + budget.maxTransparentTriangles());
                        ModelRenderRendererLight light = sampleLight(primitive, transform, camera);
                        Matrix4f eye = new Matrix4f(view).mul(transform); Vector3f center = new Vector3f();
                        var draw = new FallbackDraw(primitive, transform, mirrored, light.packed(),
                                asset.renderType(primitive.material), false, fullRange(primitive));
                        for (int offset = 0; offset < primitive.indices.length; offset += 3) {
                            int a = primitive.indices[offset] * 3, b = primitive.indices[offset + 1] * 3, c = primitive.indices[offset + 2] * 3; float[] p = primitive.positions;
                            center.set((p[a] + p[b] + p[c]) / 3, (p[a + 1] + p[b + 1] + p[c + 1]) / 3, (p[a + 2] + p[b + 2] + p[c + 2]) / 3); eye.transformPosition(center);
                            triangles.add(new Triangle(draw, instance, occurrence.nodeIndex(), offset, center.z));
                        }
                    }
                }
            }
            triangles.sort(Comparator.comparingDouble(Triangle::depth));
            var global = RenderSystem.getModelViewStack(); global.pushMatrix();
            try {
                global.set(view);
                try (ByteBufferBuilder arena = new ByteBufferBuilder(65536)) {
                    int begin = 0;
                    while (begin < triangles.size()) {
                        RenderType type = triangles.get(begin).draw().type();
                        int end = begin + 1; while (end < triangles.size() && end - begin < MAX_RUN_TRIANGLES && triangles.get(end).draw().type() == type) end++;
                        BufferBuilder builder = new BufferBuilder(arena, VertexFormat.Mode.TRIANGLES, type.format());
                        for (int i = begin; i < end; i++) { Triangle triangle = triangles.get(i); FallbackDraw draw = triangle.draw(); PoseStack local = new PoseStack(); local.mulPose(draw.transform()); emitTriangle(draw.primitive(), local.last(), draw.mirrored(), draw.packedLight(), triangle.offset(), builder, draw.iris()); }
                        MeshData mesh = builder.buildOrThrow(); type.draw(mesh);
                        FallbackDraw lastDraw = null;
                        for (int i = begin; i < end; i++) {
                            var triangle = triangles.get(i);
                            if (triangle.draw() != lastDraw) recordTransparentObject(triangle.instance(), triangle.nodeIndex());
                            lastDraw = triangle.draw();
                        }
                        transparentDraws++; mainDrawCount++; transparentTriangles += end - begin; METRICS.transparentDraw(end - begin); begin = end;
                    }
                }
            } finally { global.popMatrix(); }
        } catch (RuntimeException error) { failed(error); }
        finally {
            drawingTransparent = false;
            long transparentNanos = System.nanoTime() - started;
            METRICS.recordTransparentCpu(transparentNanos);
            mainCpuNanos += transparentNanos;
            METRICS.recordPass(ModelRenderMetrics.Pass.MAIN, mainCpuNanos, mainDrawCount, mainPrimitiveCount);
            var resident = ModelRenderAsset.residency().snapshot();
            METRICS.residency(resident.cpuBytes(), resident.gpuBytes(), resident.textureBytes());
            METRICS.endFrame();
        }
    }

    /** Cluster visibility and contiguous source-index ranges used by P7-03 opaque/shadow submission.
     * Transparent BSP keeps its own sorted index stream and remains conservatively primitive/group submitted. */
    record ClusterCullResult(int candidates, int visible, int culled, List<IndexRange> ranges) {
        boolean allCulled() { return candidates > 0 && visible == 0; }
    }

    private static List<IndexRange> fullRange(ModelRenderAsset.Primitive primitive) {
        return List.of(new IndexRange(0, primitive.indices.length));
    }

    private static ModelShadowDistancePolicy.Decision shadowDistanceDecision(ModelRenderInstance instance, Vec3 camera) {
        double distance = distanceToBounds(instance.asset().sceneBounds(instance.scene()),
                instance.transform().relativeTo(camera.x, camera.y, camera.z));
        return ModelShadowDistancePolicy.evaluate(distance);
    }

    private static final class ObjectLodState {
        final ModelObjectLodSelection lod = new ModelObjectLodSelection();
        long mainVisibleFrame = -1, mainRecordedFrame = -1, shadowRecordedFrame = -1;
    }

    private static final class InstanceLodState {
        final int scene;
        final ObjectLodState[] objects;
        final Map<Integer, Integer> nodes = new HashMap<>();
        long usedFrame = -1, mainRecordedFrame = -1, shadowRecordedFrame = -1;

        InstanceLodState(int scene, List<ModelLodObject> source) {
            this.scene = scene;
            objects = new ObjectLodState[source.size()];
            for (var object : source) {
                objects[object.index()] = new ObjectLodState();
                nodes.put(object.nodeIndex(), object.index());
            }
        }
    }

    private static final Map<ModelRenderAsset, Map<String, InstanceLodState>> LOD_HISTORY = new WeakHashMap<>();
    private static long lastLodHistorySweep = -1;

    private static InstanceLodState objectHistory(ModelRenderInstance instance) {
        // Sweep at most once per second, independently of object count.
        if (frame - lastLodHistorySweep >= 60) {
            for (var entries : LOD_HISTORY.values()) entries.entrySet().removeIf(e -> e.getValue().usedFrame < frame - 120);
            lastLodHistorySweep = frame;
        }
        var history = LOD_HISTORY.computeIfAbsent(instance.asset(), ignored -> new HashMap<>());
        var state = history.get(instance.id());
        if (state == null || state.scene != instance.scene()) {
            state = new InstanceLodState(instance.scene(), instance.asset().lodObjects(instance.scene()));
            history.put(instance.id(), state);
        }
        state.usedFrame = frame;
        return state;
    }

    private static InstanceLodState existingObjectHistory(ModelRenderInstance instance) {
        var history = LOD_HISTORY.get(instance.asset());
        var state = history == null ? null : history.get(instance.id());
        return state != null && state.scene == instance.scene() ? state : null;
    }

    /** Pin previous complete object levels before another object can trigger GPU eviction. */
    private static void pinPreviousObjectLevels(ModelRenderInstance instance, boolean iris, boolean resident) {
        if (!resident) return;
        var history = existingObjectHistory(instance);
        if (history == null) return;
        for (int index = 0; index < history.objects.length; index++) {
            var state = history.objects[index];
            var lod = state.lod;
            if (lod.selection() != null && lod.selectionFrame() >= frame - 1 && lod.iris() == iris && lod.resident()
                    && lod.selection().residentReady())
                instance.asset().pinObjectLod(instance.scene(), index, lod.selection().level(), iris);
        }
    }

    /** A pending/culled object does not suppress another object's transparent geometry. */
    static boolean objectUploadDeferred(ModelRenderInstance instance, int nodeIndex) {
        var history = existingObjectHistory(instance);
        Integer index = history == null ? null : history.nodes.get(nodeIndex);
        if (index == null) return true;
        var state = history.objects[index];
        return state.mainVisibleFrame != frame || state.lod.selection() == null || state.lod.selection().deferred();
    }

    static void recordTransparentObject(ModelRenderInstance instance, int nodeIndex) {
        var history = existingObjectHistory(instance);
        Integer index = history == null ? null : history.nodes.get(nodeIndex);
        if (index == null || objectUploadDeferred(instance, nodeIndex)) return;
        recordObjectLod(history, history.objects[index], instance.asset().lodObjects(instance.scene()).get(index), false);
    }

    static void recordTransparentObjectDeferred(ModelRenderInstance instance, int nodeIndex) {
        var history = existingObjectHistory(instance);
        Integer index = history == null ? null : history.nodes.get(nodeIndex);
        if (index == null) return;
        var state = history.objects[index];
        if (state.mainRecordedFrame == frame || state.mainVisibleFrame != frame) return;
        state.mainRecordedFrame = frame;
        var object = instance.asset().lodObjects(instance.scene()).get(index);
        METRICS.modelLodMetrics().record(ModelLodMetrics.Pass.MAIN, state.lod.level(), state.lod.selection().level(),
                object.triangles(0), 0, false, true);
    }

    private static ObjectLodState selectObjectLod(ModelRenderInstance instance, ModelLodObject object,
                                                 InstanceLodState history, Matrix4fc transform,
                                                 boolean shadow, boolean iris, boolean resident) {
        var policy = ModelLodPolicy.active();
        long selectionFrame = shadow ? frame + 1 : frame;
        var state = history.objects[object.index()];
        boolean useResident = resident;
        if (state.lod.matches(selectionFrame, policy, iris, useResident)) return state;
        double distance = distanceToBounds(object.bounds(), transform);
        state.lod.select(selectionFrame, policy, distance, object.hasOpaque(), iris, useResident, level -> {
            long resolveStarted = System.nanoTime();
            try { return instance.asset().resolveObjectLod(instance.scene(), object.index(), level, iris, useResident); }
            finally {
                if (iris && useResident) METRICS.irisPhase(shadow ? ModelRenderMetrics.Pass.SHADOW : ModelRenderMetrics.Pass.MAIN,
                        ModelRenderMetrics.IrisPhase.GEOMETRY_RESOLVE, System.nanoTime() - resolveStarted);
            }
        });
        return state;
    }

    private static void recordObjectLod(InstanceLodState history, ObjectLodState state,
                                        ModelLodObject object, boolean shadow) {
        long selectionFrame = shadow ? frame + 1 : frame;
        if ((shadow ? state.shadowRecordedFrame : state.mainRecordedFrame) == selectionFrame) return;
        if (shadow) state.shadowRecordedFrame = selectionFrame;
        else state.mainRecordedFrame = selectionFrame;
        var lod = state.lod;
        var choice = lod.selection();
        var pass = shadow ? ModelLodMetrics.Pass.SHADOW : ModelLodMetrics.Pass.MAIN;
        METRICS.modelLodMetrics().record(pass, lod.level(), choice.level(), object.triangles(0),
                choice.deferred() ? 0 : object.triangles(choice.level()),
                lod.resident() && !choice.deferred() && (!choice.residentReady() || lod.level() != choice.level()),
                choice.deferred());
        if (!choice.deferred() && (shadow ? history.shadowRecordedFrame : history.mainRecordedFrame) != selectionFrame) {
            METRICS.modelLodMetrics().recordModel(pass);
            if (shadow) history.shadowRecordedFrame = selectionFrame;
            else history.mainRecordedFrame = selectionFrame;
        }
    }

    private static double distanceToBounds(GltfAsset.Bounds bounds, Matrix4fc transform) {
        return com.mine.geometry_node.client.model.render.geometry.ModelQualityDistance.distance(bounds, transform);
    }

    static ClusterCullResult cullClusters(ModelRenderAsset.Primitive primitive, Matrix4fc transform, Vec3 camera, Frustum frustum) {
        List<ModelClusterAsset.Cluster> clusters = primitive.clusters();
        if (clusters == null || clusters.isEmpty()) {
            boolean rejected = isCulled(primitive.bounds, transform, camera, frustum);
            return rejected ? new ClusterCullResult(1, 0, 1, List.of()) : new ClusterCullResult(1, 1, 0, fullRange(primitive));
        }
        int visible = 0;
        int runStart = -1, runCount = 0;
        List<IndexRange> ranges = new ArrayList<>();
        for (ModelClusterAsset.Cluster cluster : clusters) {
            if (!isCulled(cluster.bounds(), transform, camera, frustum)) {
                visible++;
                if (runStart >= 0 && runStart + runCount == cluster.firstIndex()) {
                    runCount += cluster.indexCount();
                } else {
                    if (runStart >= 0) ranges.add(new IndexRange(runStart, runCount));
                    runStart = cluster.firstIndex();
                    runCount = cluster.indexCount();
                }
            }
        }
        if (runStart >= 0) ranges.add(new IndexRange(runStart, runCount));
        return new ClusterCullResult(clusters.size(), visible, clusters.size() - visible, List.copyOf(ranges));
    }

    static boolean isOutsideFrustum(GltfAsset.Bounds bounds, Matrix4fc transform, Vec3 camera, Frustum frustum) {
        if (frustum == null || bounds == null || bounds.isEmpty()) return false;
        CULLING_BOUNDS.set(bounds, transform, camera.x, camera.y, camera.z, true);
        return !frustum.isVisible(new AABB(CULLING_BOUNDS.minX(), CULLING_BOUNDS.minY(), CULLING_BOUNDS.minZ(),
                CULLING_BOUNDS.maxX(), CULLING_BOUNDS.maxY(), CULLING_BOUNDS.maxZ()));
    }

    static boolean isCulled(GltfAsset.Bounds bounds, Matrix4fc transform, Vec3 camera, Frustum frustum) {
        if (bounds == null || bounds.isEmpty()) return false;
        if (frustum == null) CULLING_BOUNDS.set(bounds, transform, 0, 0, 0, false);
        else CULLING_BOUNDS.set(bounds, transform, camera.x, camera.y, camera.z, true);
        float maxDistance = (float) budget.maxDrawDistance();
        float distanceLimit = maxDistance + (float) Math.sqrt(CULLING_BOUNDS.radiusSquared());
        if (maxDistance >= 0 && CULLING_BOUNDS.centerDistanceSquared() > distanceLimit * distanceLimit) return true;
        if (frustum == null) return false;
        return !frustum.isVisible(new AABB(CULLING_BOUNDS.minX(), CULLING_BOUNDS.minY(), CULLING_BOUNDS.minZ(),
                CULLING_BOUNDS.maxX(), CULLING_BOUNDS.maxY(), CULLING_BOUNDS.maxZ()));
    }

    static float lightFactor(ModelRenderAsset.Primitive primitive, Matrix4fc relative, Vec3 camera) {
        return primitive.material.unlit() ? 1 : sampleLight(primitive, relative, camera).factor();
    }

    static int packedLight(ModelRenderAsset.Primitive primitive, Matrix4fc relative, Vec3 camera) {
        return sampleLight(primitive, relative, camera).packed();
    }

    static ModelRenderRendererLight sampleLight(ModelRenderAsset.Primitive primitive, Matrix4fc relative, Vec3 camera) {
        var level = Minecraft.getInstance().level; if (primitive.material.unlit() || level == null) return new ModelRenderRendererLight(LightCoordsUtil.pack(15, 15), 1.0f);
        GltfAsset.Bounds b = primitive.bounds; float cx = (b.minX() + b.maxX()) * .5f, cy = (b.minY() + b.maxY()) * .5f, cz = (b.minZ() + b.maxZ()) * .5f;
        float ex = (b.maxX() - b.minX()) * .5f, ey = (b.maxY() - b.minY()) * .5f, ez = (b.maxZ() - b.minZ()) * .5f;
        int block = 0, sky = 0;
        Vector3f relativePoint = new Vector3f();
        for (int sample = 0; sample < 7; sample++) {
            float px = cx + (sample == 1 ? -ex : sample == 2 ? ex : 0);
            float py = cy + (sample == 3 ? -ey : sample == 4 ? ey : 0);
            float pz = cz + (sample == 5 ? -ez : sample == 6 ? ez : 0);
            relative.transformPosition(px, py, pz, relativePoint);
            BlockPos pos = BlockPos.containing(camera.x + relativePoint.x, camera.y + relativePoint.y, camera.z + relativePoint.z);
            int[] sampleLight = LIGHT_CACHE.sample(level, pos); block += sampleLight[0]; sky += sampleLight[1];
        }
        int count = 7; int packed = LightCoordsUtil.pack(Math.round((float) block / count), Math.round((float) sky / count));
        float factor = Math.clamp(((block / (float) count) * .35f + (sky / (float) count) * .65f) / 15.0f, .06f, 1.0f);
        return new ModelRenderRendererLight(packed, factor);
    }

    private static void emitTriangle(ModelRenderAsset.Primitive primitive, PoseStack.Pose pose, boolean mirrored, int light, int offset, VertexConsumer out, boolean iris) {
        for (int corner = 0; corner < 3; corner++) {
            int index = primitive.indices[offset + (mirrored && corner != 0 ? 3 - corner : corner)], at = index * 3, uv = index * 2;
            if (iris) {
                float[] factor = primitive.baseColorFactor;
                int color = primitive.colors[index];
                int alpha = primitive.material.alphaMode() == GltfAsset.AlphaMode.OPAQUE ? 255
                        : Math.clamp(Math.round((color >>> 24) * factor[3]), 0, 255);
                out.addVertex(pose, primitive.positions[at], primitive.positions[at + 1], primitive.positions[at + 2])
                        .setColor(Math.clamp(Math.round(((color >>> 16) & 255)), 0, 255),
                                Math.clamp(Math.round(((color >>> 8) & 255)), 0, 255),
                                Math.clamp(Math.round((color & 255)), 0, 255), alpha)
                        .setUv(primitive.baseUv[uv], primitive.baseUv[uv + 1])
                        .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                        .setNormal(pose, primitive.normals[at], primitive.normals[at + 1], primitive.normals[at + 2]);
                continue;
            }
            int u = Float.floatToRawIntBits(primitive.uv1[uv]), v = Float.floatToRawIntBits(primitive.uv1[uv + 1]);
            out.addVertex(pose, primitive.positions[at], primitive.positions[at + 1], primitive.positions[at + 2]).setColor(primitive.colors[index]).setUv(primitive.uv0[uv], primitive.uv0[uv + 1]).setUv1(u & 65535, u >>> 16).setUv2(v & 65535, v >>> 16).setLineWidth((float) light).setNormal(pose, primitive.normals[at], primitive.normals[at + 1], primitive.normals[at + 2]);
        }
    }

    private static void failed(RuntimeException error) { String message = error.toString(); if (!message.equals(lastFailure)) GeometryNode.LOGGER.error("P4 static model renderer stopped affected pass", error); lastFailure = message; }
    public static Map<String, Object> report() {
        Map<String, Object> result = new LinkedHashMap<>();
        refreshLoadedCounts();
        result.put("materialUploads", ModelMaterialUploadQueue.report());
        result.put("modelLod", Map.of(
                "policy", ModelLodPolicy.active().report(),
                "metrics", METRICS.modelLodMetrics().report(),
                "selectionScope", "one level per mesh-bearing node instance; main and shadow share the effective choice using viewer-to-object AABB distance",
                "geometryScope", "four levels; shared source vertices; MASK and BLEND preserved",
                "bvhScope", "complete source geometry",
                "gpuScope", "cold objects wait independently for bounded GPU upload; ready object levels remain visible during LOD changes; immediate tails only for GPU budget/compatibility fallback"));
        result.put("backend", IRIS.available() && IRIS.active() ? (irisResidentEnabled ? "iris_resident" : "iris_immediate")
                : DIRECT_GPU_ENABLED ? "native_resident" : "native_collector");
        result.put("visualVerification", "UNTESTED");
        result.put("geometryTransferScope", "Model-owned vertex/index buffers and requested dynamic UBO slices; excludes host shared sequential-index cache and driver-internal transfers");
        result.put("clusterSubmission", Map.of(
                "opaqueAndMask", "visible cluster contiguous index ranges; adjacent ranges merged",
                "mainAndShadow", "partial range submission enabled",
                "transparent", "BSP/sorted index stream retains independent conservative group path"));
        result.put("irisResident", Map.of("enabled", irisResidentEnabled, "mainDraws", irisMainDraws,
                "shadowDraws", irisShadowDraws, "deferredDraws", irisDeferredDraws,
                "dynamicLightDraws", ModelResidentLight.draws(), "uploadBudget", ModelIrisUploadBudget.INSTANCE.snapshot()));
        result.put("opaqueSubmissions", opaqueSubmissions);
        result.put("transparentTrianglesTotal", transparentTriangles);
        result.put("transparentDrawCalls", transparentDraws);
        result.put("lastFailure", lastFailure);
        result.put("budget", budget);
        result.put("residency", ModelRenderAsset.residency().snapshot());
        result.put("metrics", METRICS.report());
        result.put("shaderGpu", com.mine.geometry_node.client.model.render.diagnostic.ModelShaderGpuTimings.report());
        result.put("modelGpu", com.mine.geometry_node.client.model.render.diagnostic.ModelDrawGpuTimings.report());
        result.put("lightSampleCache", LIGHT_CACHE.report());
        result.put("localLightOcclusion", ModelIrisHeldLight.report());
        Map<String, Object> iris = new LinkedHashMap<>();
        iris.put("initialized", irisInitialized);
        iris.put("available", IRIS.available());
        iris.put("active", IRIS.active());
        iris.put("shadowPass", IRIS.shadow());
        iris.put("callbackRegistered", IRIS.callbackRegistered());
        iris.put("revision", IRIS.revision());
        iris.put("status", IRIS.status());
        Map<String, Object> shadow = new LinkedHashMap<>();
        String shadowStatus = !IRIS.callbackRegistered() ? "UNAVAILABLE"
                : "FAILED".equals(shadowTargetStatus) ? "FAIL"
                : "REGISTERED_UNVERIFIED";
        shadow.put("status", shadowStatus);
        shadow.put("callbacks", shadowCallbacks);
        shadow.put("draws", shadowDraws);
        shadow.put("casters", shadowCasters);
        shadow.put("target", shadowTargetStatus);
        shadow.put("lastFailure", shadowLastFailure);
        shadow.put("projectionMatrixConsumed", shadowProjectionConsumed);
        shadow.put("effectiveToleranceMin", Float.isFinite(shadowToleranceMin) ? shadowToleranceMin : -1);
        shadow.put("effectiveToleranceMax", Float.isFinite(shadowToleranceMin) ? shadowToleranceMax : -1);
        shadow.put("cullingStrategy", "shadow-frustum full primitive containment; partial bounds retain cluster tests");
        shadow.put("fullyInsidePrimitives", shadowFullyInsidePrimitives);
        shadow.put("clusterTestsSkipped", shadowClusterTestsSkipped);
        shadow.put("repeatUvCompatibility", ModelIrisShadowUv.report());
        result.put("irisCompatibility", iris);
        result.put("irisLifecycle", IRIS.lifecycleReport());
        result.put("transparentResidency", ModelTransparentRenderer.report());
        result.put("irisAlpha", ModelIrisAlphaTest.report());
        result.put("irisMaterialBlend", ModelIrisBlendOverride.report());
        result.put("transparentRouting", Map.of("route", IRIS.active() ? "POST_IRIS_FINAL_FORWARD" : "NATIVE_FORWARD",
                "target", "resolved main color and world depth", "shaderpackGbufferWrites", false,
                "lighting", "native basic material lighting; shaderpack refraction/GI/volumetrics not applied",
                "validation", "pixels pending; bounded worker BSP within static asset scenes; budget fallback and cross-instance/vanilla glass sorting approximate"));
        result.put("shadowRouting", shadow);
        result.put("shadowDistancePolicy", ModelShadowDistancePolicy.report());
        result.put("limitations", List.of(
                "Iris/shaderpack visual pixels and per-pack capability remain UNTESTED until manual capture",
                "Iris main/shadow share entity-format resident geometry by default; new path pixels and performance remain UNTESTED",
                "Resident geometry builds and uploads triangle-aligned chunks across frames; GPU allocation and individual chunk driver calls remain non-preemptible",
                "Iris MASK/BLEND alpha adaptation recognizes inspected comparisons; fixed pack alpha floors/custom rules are reported as degraded",
                "Iris PBR uses LabPBR 1.3; pack material settings must match; native BLEND uses the documented PBR approximation",
                "BLEND draws native forward into resolved world color/depth after Iris final; preserves background instead of writing packed Gbuffers; worker BSP splits intra-scene intersections, bounded centroid fallback; instance groups and vanilla transparency remain approximate",
                "P4 culling includes distance/sphere and camera frustum; shadow callback uses an independent camera without main-view culling",
                "native opaque resident draws sample the same per-primitive lightmap coordinates as collector draws; native BLEND retains its dynamic color light factor",
                "native opaque resident rendering is default with cached reverse-winding indices; explicit compatibility draws rebuild vertices; default BLEND uploads only changed indices",
                "spatial light samples persist across frames and require explicit render-thread invalidation; geometry/GPU residency is not rebuilt"));
        return result;
    }
}
