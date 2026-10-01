package com.mine.geometry_node.client.model.render;

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
import com.mine.geometry_node.client.model.runtime.ClientModelService;
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
    private static final int MAX_RUN_TRIANGLES = 16_384;
    private static long opaqueSubmissions, transparentTriangles, transparentDraws;
    private static String lastFailure = "";
    private static long frame;
    private static ModelRenderBudget budget = ModelRenderBudget.DEFAULT;
    private static final ModelRenderMetrics METRICS = new ModelRenderMetrics();
    /** Native resident draws are opt-in and run at AfterOpaqueFeatures, outside collector emission. */
    private static final boolean DIRECT_GPU_ENABLED = Boolean.getBoolean("geometry_node.model.direct_gpu");
    private static final ModelSpatialLightCache LIGHT_CACHE = new ModelSpatialLightCache();
    private static final IrisModelBridge IRIS = new IrisModelBridge();
    private static boolean irisInitialized;
    private static long shadowDraws, shadowCasters;
    private static long shadowCallbacks;
    private static boolean shadowProjectionConsumed;
    private static ProjectionMatrixBuffer shadowProjection;
    private static String shadowTargetStatus = "UNTESTED";
    private static String shadowLastFailure = "";
    private static Map<BatchKey, List<DirectDraw>> queuedDirect = Map.of();
    private static List<IrisDraw> queuedIris = List.of();
    private static boolean irisResidentEnabled = !Boolean.getBoolean("geometry_node.model.iris_immediate");
    private static boolean drawingShadow, drawingTransparent, awaitingResolvedTransparency;
    private static long mainCpuNanos;
    private static int mainDrawCount, mainPrimitiveCount;
    private static long irisMainDraws, irisShadowDraws, irisDeferredDraws;
    private static long residencyFrame = Long.MIN_VALUE;

    private static void beginResidencyFrame(long nextFrame) {
        ModelIrisUploadBudget.INSTANCE.beginFrame(nextFrame, budget);
        if (residencyFrame != nextFrame) {
            residencyFrame = nextFrame;
            ModelRenderAsset.residency().beginFrame();
        }
    }
    private record IrisDraw(ModelRenderAsset asset, ModelRenderAsset.Primitive primitive,
                            Matrix4f transform, int packedLight, boolean mirrored) {}

    public static void setIrisResidentEnabled(boolean enabled) {
        RenderSystem.assertOnRenderThread();
        irisResidentEnabled = enabled;
        queuedIris = List.of(); queuedDirect = Map.of();
    }

    /** Counts buffers actually passed through RenderType's immediate uploader, including collector fallback. */
    public static void immediateGeometryUploaded(long bytes, long nanos) {
        METRICS.immediateGeometryUpload(drawingShadow ? ModelRenderMetrics.Pass.SHADOW : ModelRenderMetrics.Pass.MAIN, bytes, nanos);

    }

    public static void immediateDrawFinished(long nanos) {
        // Shadow and explicitly sorted transparent draws are already inside timed scopes.
        if (!drawingShadow && !drawingTransparent) mainCpuNanos += nanos;
    }

    public static void immediateUniformUploaded(long bytes) {
        METRICS.dynamicUniformUpload(drawingShadow ? ModelRenderMetrics.Pass.SHADOW : ModelRenderMetrics.Pass.MAIN, bytes);
    }

    /** Called before processRenderUploads so per-frame upload counters are not immediately erased. */
    public static void beginMainFrame() {
        RenderSystem.assertOnRenderThread();
        frame++; METRICS.beginFrame(frame); beginResidencyFrame(frame);
        if (ClientModelService.INSTANCE.hasVisibleInstances()) LIGHT_CACHE.beginFrame(Minecraft.getInstance().level);
        mainCpuNanos = 0; mainDrawCount = mainPrimitiveCount = 0;
    }

    public static void resetPerformanceMetrics() {
        RenderSystem.assertOnRenderThread();
        METRICS.reset();
        mainCpuNanos = 0; mainDrawCount = mainPrimitiveCount = 0;
    }

    private record DirectDraw(ModelRenderAsset asset, ModelRenderAsset.GpuPrimitive mesh, ModelRenderAsset.MaterialBinding binding,
                              Matrix4f transform, ModelRenderAsset.Primitive primitive, ModelRenderRendererLight light) {}
    private record FallbackDraw(ModelRenderAsset.Primitive primitive, Matrix4f transform, boolean mirrored, int packedLight, RenderType type, boolean iris) {}
    private record Triangle(FallbackDraw draw, int offset, float depth) {}
    private record BatchKey(ModelRenderAsset.MaterialBinding binding) {}
    private record ModelRenderRendererLight(int packed, float factor) {}

    private ModelStaticRenderer() {}

    public static void setBudget(ModelRenderBudget next) { budget = Objects.requireNonNull(next); ModelRenderAsset.residency().setBudget(next); }
    public static ModelRenderBudget budget() { return budget; }
    public static ModelRenderMetrics metrics() { return METRICS; }
    public static String stage() {
        if (!irisInitialized || !IRIS.available()) return "P4 STATIC_RESIDENT; client-local instances";
        return IRIS.active() ? "P5 IRIS_ROUTED; client-local instances" : "P5 IRIS_ADAPTER_READY; client-local instances";
    }

    /** Initializes the optional Iris adapter on the render thread. */
    public static void initializeIris() {
        RenderSystem.assertOnRenderThread();
        if (irisInitialized) return;
        irisInitialized = true;
        IRIS.initialize(ModelStaticPipelines.irisMainPipelines(), ModelStaticPipelines.irisTranslucentPipelines(),
                ModelStaticPipelines.irisEmissivePipelines(), ModelStaticPipelines.irisShadowPipelines(), ModelStaticRenderer::drawShadow);
    }

    /** Reapplies pipeline assignments after F3+T or a shaderpack reload. */
    public static void refreshIris() {
        RenderSystem.assertOnRenderThread();
        if (irisInitialized) IRIS.refresh();
    }
    public static void pollIrisLifecycle() {
        RenderSystem.assertOnRenderThread();
        if (IRIS.pollLifecycle()) {
            ClientModelService.INSTANCE.invalidateRenderResources("iris_pipeline_changed");
            closeShadowResources();
        }
    }

    /** Versioned spatial-light invalidation contract; render-thread only and absolute world block coordinates. */
    public static void invalidateLight(BlockPos position) { RenderSystem.assertOnRenderThread(); LIGHT_CACHE.invalidate(Objects.requireNonNull(position)); }
    public static void invalidateLightRegion(BlockPos center, int radius, String reason) { RenderSystem.assertOnRenderThread(); LIGHT_CACHE.invalidateRegion(Objects.requireNonNull(center), radius, reason); }
    public static void invalidateAllLight(String reason) { RenderSystem.assertOnRenderThread(); LIGHT_CACHE.invalidateAll(reason); }

    public static void submit(PoseStack ignoredHostStack, SubmitNodeCollector collector, Vec3 camera, Frustum frustum, List<ModelRenderInstance> instances) {
        RenderSystem.assertOnRenderThread();
        if (instances.isEmpty()) { queuedIris = List.of(); queuedDirect = Map.of(); return; }
        long started = System.nanoTime();
        boolean iris = IRIS.available() && IRIS.active();
        List<IrisDraw> irisDraws = new ArrayList<>();
        Map<BatchKey, List<DirectDraw>> direct = new LinkedHashMap<>();
        Map<RenderType, List<FallbackDraw>> fallback = new LinkedHashMap<>();
        try {
            for (ModelRenderInstance instance : instances) {
                if (!instance.visible() || instance.asset().isClosed()) continue;
                ModelRenderAsset asset = instance.asset(); boolean assetVisible = false;
                for (ModelRenderAsset.Occurrence occurrence : asset.occurrences(instance.scene())) {
                    Matrix4f transform = instance.transform().relativeTo(camera.x, camera.y, camera.z).mul(occurrence.transform());
                    boolean mirrored = transform.determinant3x3() < 0.0f;
                    for (ModelRenderAsset.Primitive primitive : occurrence.primitives()) {
                        if (isCulled(primitive.bounds, transform, camera, frustum)) { METRICS.culled(); continue; }
                        if (mainPrimitiveCount >= budget.maxVisiblePrimitives()) throw new IllegalStateException("visible model primitive budget exceeded");
                        METRICS.visible(); mainPrimitiveCount++;
                        if (!assetVisible) { asset.touch(); assetVisible = true; }
                        if (primitive.material.alphaMode() == GltfAsset.AlphaMode.BLEND) continue;
                        ModelRenderRendererLight light = sampleLight(primitive, transform, camera);
                        if (iris && irisResidentEnabled) {
                            irisDraws.add(new IrisDraw(asset, primitive, transform, light.packed(), mirrored));
                            continue;
                        }
                        ModelRenderAsset.GpuPrimitive mesh = DIRECT_GPU_ENABLED && !iris ? asset.gpuPrimitive(primitive) : null;
                        if (DIRECT_GPU_ENABLED && !iris && mesh == null) continue;
                        ModelRenderAsset.MaterialBinding binding = asset.binding(primitive.material);
                        if (DIRECT_GPU_ENABLED && !iris && mesh != null && binding != null) {
                            direct.computeIfAbsent(new BatchKey(binding), key -> new ArrayList<>())
                                    .add(new DirectDraw(asset, mesh, binding, new Matrix4f(transform), primitive, light));
                        } else {
                            RenderType type = iris ? asset.irisRenderType(primitive.material) : asset.renderType(primitive.material);
                            fallback.computeIfAbsent(type, key -> new ArrayList<>())
                                    .add(new FallbackDraw(primitive, new Matrix4f(transform), mirrored, light.packed(), type, iris));
                        }
                    }
                }
            }
            queuedDirect = Map.copyOf(direct);
            queuedIris = List.copyOf(irisDraws);
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
        if (queuedDirect.isEmpty() && queuedIris.isEmpty()) return;
        long started = System.nanoTime();
        Map<BatchKey, List<DirectDraw>> batches = queuedDirect;
        List<IrisDraw> irisDraws = queuedIris;
        queuedDirect = Map.of(); queuedIris = List.of();
        try {
            if (IRIS.available() && IRIS.active() && irisResidentEnabled) {
                drawIrisResident(irisDraws, view, false);
            } else {
                drawDirectPass(batches, new Matrix4f(view), false, "P4 resident opaque");
            }
        } catch (RuntimeException error) {
            failed(error);
        } finally {
            mainCpuNanos += System.nanoTime() - started;
        }
    }

    private record ResolvedIrisDraw(IrisDraw source, ModelRenderAsset.GpuPrimitive mesh,
                                    com.mojang.blaze3d.pipeline.RenderPipeline pipeline,
                                    net.minecraft.resources.Identifier texture) {}

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
            // Every potentially allocating/uploading resolution happens before opening any pass.
            for (IrisDraw draw : draws) {
                if (!draw.asset().gpuResident()) continue;
                draw.asset().touch();
                ModelRenderAsset.GpuPrimitive mesh = draw.asset().irisGpuPrimitive(draw.primitive());
                if (mesh == null) { irisDeferredDraws++; continue; }
                var pipeline = ModelRenderAsset.irisPipeline(draw.primitive(), shadowPass);
                if (!mesh.format().equals(pipeline.getVertexFormat()))
                    throw new IllegalStateException("Iris resident vertex layout changed within a draw");
                resolved.add(new ResolvedIrisDraw(draw, mesh, pipeline, draw.asset().binding(draw.primitive().material).irisBase()));
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
                        var mesh = item.mesh();
                        started = System.nanoTime();
                        try {
                            pass.setVertexBuffer(0, mesh.vertices());
                            pass.setIndexBuffer(mesh.indices(draw.mirrored()), VertexFormat.IndexType.INT);
                            try (var alpha = ModelIrisAlphaTest.scope(ModelRenderAsset.alphaPolicy(draw.primitive().material));
                                 var blend = ModelIrisBlendOverride.scope(false)) {
                                ModelResidentLight.draw(pass, mesh.format(), draw.primitive().material.unlit() ? ModelRenderAsset.FULL_LIGHT : draw.packedLight(), mesh.indexCount());
                            }
                            count++; completed++;
                            if (shadowPass) { irisShadowDraws++; shadowDraws++; }
                            else { irisMainDraws++; opaqueSubmissions++; mainDrawCount++; METRICS.opaqueDraw(); }
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
        if (!ClientModelService.INSTANCE.hasVisibleInstances()) {
            shadowProjectionConsumed = false;
            METRICS.recordPass(ModelRenderMetrics.Pass.SHADOW, System.nanoTime() - started, 0, 0);
            return;
        }
        pollIrisLifecycle();
        long previousShadowDraws = shadowDraws;
        int submitted = 0;
        List<IrisDraw> draws = new ArrayList<>();
        beginResidencyFrame(frame + 1);
        LIGHT_CACHE.beginFrame(Minecraft.getInstance().level);
        var savedProjection = RenderSystem.getProjectionMatrixBuffer();
        var savedProjectionType = RenderSystem.getProjectionType();
        var modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        drawingShadow = true;
        try (var shadowUv = ModelIrisShadowUv.scope()) {
            Vec3 camera = new Vec3(cameraX, cameraY, cameraZ);
            Frustum shadowFrustum = new Frustum(view, projection);
            shadowFrustum.prepare(cameraX, cameraY, cameraZ);
            // This list is independent of main-camera culling; offscreen casters remain eligible.
            for (ModelRenderInstance instance : ClientModelService.INSTANCE.renderSnapshots()) {
                if (!instance.visible() || !instance.asset().gpuResident()) continue;
                for (ModelRenderAsset.Occurrence occurrence : instance.asset().occurrences(instance.scene())) {
                    Matrix4f transform = instance.transform().relativeTo(cameraX, cameraY, cameraZ).mul(occurrence.transform());
                    boolean mirrored = transform.determinant3x3() < 0;
                    for (ModelRenderAsset.Primitive primitive : occurrence.primitives()) {
                        if (primitive.material.alphaMode() == GltfAsset.AlphaMode.BLEND) continue;
                        if (isCulled(primitive.bounds, transform, camera, shadowFrustum)) continue;
                        if (draws.size() >= budget.maxVisiblePrimitives()) throw new IllegalStateException("shadow primitive budget exceeded");
                        instance.asset().touch();
                        draws.add(new IrisDraw(instance.asset(), primitive, transform,
                                sampleLight(primitive, transform, camera).packed(), mirrored));
                    }
                }
            }
            if (!draws.isEmpty()) {
                if (shadowProjection == null) shadowProjection = new ProjectionMatrixBuffer("GeometryNode model shadows");
                GpuBufferSlice projectionBuffer = shadowProjection.getBuffer(projection);
                METRICS.dynamicUniformUpload(ModelRenderMetrics.Pass.SHADOW, projectionBuffer.length());
                RenderSystem.setProjectionMatrix(projectionBuffer, ProjectionType.ORTHOGRAPHIC);
                shadowProjectionConsumed = true;
                modelView.set(view);
                if (irisResidentEnabled) {
                    submitted = drawIrisResident(draws, view, true);
                } else {
                    try (ByteBufferBuilder arena = new ByteBufferBuilder(65536)) {
                        for (IrisDraw draw : draws) {
                            PoseStack pose = new PoseStack(); pose.mulPose(draw.transform());
                            RenderType type = draw.asset().irisShadowRenderType(draw.primitive().material);
                            BufferBuilder builder = new BufferBuilder(arena, VertexFormat.Mode.TRIANGLES, type.format());
                            for (int offset = 0; offset < draw.primitive().indices.length; offset += 3)
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
            shadowCasters = submitted;
            drawingShadow = false;
            modelView.popMatrix();
            RenderSystem.setProjectionMatrix(savedProjection, savedProjectionType);
            METRICS.recordPass(ModelRenderMetrics.Pass.SHADOW, System.nanoTime() - started, submitted, draws.size());
        }
    }

    /** Drop queued work and per-scene caches immediately; GPU buffers retire via host fences. */
    public static void clearSceneResources() {
        closeShadowResources();
        LIGHT_CACHE.invalidateAll("scene_cleared");
        shadowCasters = 0; shadowProjectionConsumed = false;
        mainCpuNanos = 0; mainDrawCount = mainPrimitiveCount = 0;
        METRICS.beginFrame(frame);
        METRICS.recordPass(ModelRenderMetrics.Pass.MAIN, 0, 0, 0);
        METRICS.recordPass(ModelRenderMetrics.Pass.SHADOW, 0, 0, 0);
    }

    public static void closeShadowResources() {
        RenderSystem.assertOnRenderThread();
        if (shadowProjection != null) { shadowProjection.close(); shadowProjection = null; }
        queuedIris = List.of(); queuedDirect = Map.of();
        ModelTransparentRenderer.clear();
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
            shadowTargetStatus = colorOverride && depthOverride ? "IRIS_OUTPUT_OVERRIDE_UNVERIFIED" : "MAIN_TARGET_FALLBACK";
            if (!(colorOverride && depthOverride)) {
                shadowLastFailure = "Iris shadow callback did not expose both output color/depth overrides";
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
                    float factor = shadowPass || draw.primitive().material.unlit() ? 1.0f : draw.light().factor();
                    GpuBufferSlice dynamic = RenderSystem.getDynamicUniforms().writeTransform(new Matrix4f(baseView).mul(draw.transform()),
                            new Vector4f(1.0f, 1.0f, 1.0f, factor), new Vector3f(), draw.transform());
                    transforms.add(dynamic);
                    METRICS.dynamicUniformUpload(ModelRenderMetrics.Pass.MAIN, dynamic.length());
                }
                try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                    () -> "GeometryNode " + label, color, OptionalInt.empty(), depth, OptionalDouble.empty())) {
                pass.setPipeline(ModelStaticPipelines.pipeline(false, binding.doubleSided()));
                RenderSystem.bindDefaultUniforms(pass);
                bind(pass, minecraft, "BaseTexture", binding.base());
                bind(pass, minecraft, "EmissionTexture", binding.emission());
                bind(pass, minecraft, "MaterialData", binding.data());
                bind(pass, minecraft, "Sampler0", binding.base());
                bind(pass, minecraft, "Sampler1", binding.overlay());
                pass.bindTexture("Sampler2", minecraft.gameRenderer.lightmap(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
                int transformIndex = 0;
                for (DirectDraw draw : entry.getValue()) {
                    pass.setUniform("DynamicTransforms", transforms.get(transformIndex++));
                    pass.setVertexBuffer(0, draw.mesh().vertices());
                    pass.setIndexBuffer(draw.mesh().indices(), VertexFormat.IndexType.INT);
                    pass.drawIndexed(0, 0, draw.mesh().indexCount(), 1);
                    if (shadowPass) { shadowDraws++; METRICS.opaqueDraw(); } else { opaqueSubmissions++; mainDrawCount++; METRICS.opaqueDraw(); }
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
                    emitTriangle(draw.primitive(), local.last(), draw.mirrored(), draw.packedLight(), 0, out, draw.iris());
                    for (int offset = 3; offset < draw.primitive().indices.length; offset += 3) emitTriangle(draw.primitive(), local.last(), draw.mirrored(), draw.packedLight(), offset, out, draw.iris());
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
        List<Triangle> triangles = new ArrayList<>();
        long started = System.nanoTime();
        drawingTransparent = true;
        try {
            if (irisResidentEnabled || resolvedWorld) {
                ModelTransparentRenderer.draw(view, camera, frustum, instances, resolvedWorld);
                return;
            }
            for (ModelRenderInstance instance : instances) {
                if (!instance.visible() || instance.asset().isClosed()) continue;
                ModelRenderAsset asset = instance.asset(); boolean assetVisible = false;
                for (ModelRenderAsset.Occurrence occurrence : asset.occurrences(instance.scene())) {
                    Matrix4f transform = instance.transform().relativeTo(camera.x, camera.y, camera.z).mul(occurrence.transform());
                    boolean mirrored = transform.determinant3x3() < 0.0f;
                    for (ModelRenderAsset.Primitive primitive : occurrence.primitives()) {
                        if (primitive.material.alphaMode() != GltfAsset.AlphaMode.BLEND) continue;
                        if (isCulled(primitive.bounds, transform, camera, frustum)) { METRICS.culled(); continue; }
                        if (!assetVisible) { asset.touch(); assetVisible = true; }
                        long triangleCount = primitive.indices.length / 3L;
                        if ((long) triangles.size() + triangleCount > budget.maxTransparentTriangles()) throw new IllegalStateException("P4 transparent sort budget exceeded: " + budget.maxTransparentTriangles());
                        ModelRenderRendererLight light = sampleLight(primitive, transform, camera);
                        Matrix4f eye = new Matrix4f(view).mul(transform); Vector3f center = new Vector3f();
                        for (int offset = 0; offset < primitive.indices.length; offset += 3) {
                            int a = primitive.indices[offset] * 3, b = primitive.indices[offset + 1] * 3, c = primitive.indices[offset + 2] * 3; float[] p = primitive.positions;
                            center.set((p[a] + p[b] + p[c]) / 3, (p[a + 1] + p[b + 1] + p[c + 1]) / 3, (p[a + 2] + p[b + 2] + p[c + 2]) / 3); eye.transformPosition(center);
                            triangles.add(new Triangle(new FallbackDraw(primitive, new Matrix4f(transform), mirrored, light.packed(), asset.renderType(primitive.material), false), offset, center.z));
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
                        MeshData mesh = builder.buildOrThrow(); type.draw(mesh); transparentDraws++; mainDrawCount++; transparentTriangles += end - begin; METRICS.transparentDraw(end - begin); begin = end;
                    }
                }
            } finally { global.popMatrix(); }
        } catch (RuntimeException error) { failed(error); }
        finally {
            drawingTransparent = false;
            mainCpuNanos += System.nanoTime() - started;
            METRICS.recordPass(ModelRenderMetrics.Pass.MAIN, mainCpuNanos, mainDrawCount, mainPrimitiveCount);
            var resident = ModelRenderAsset.residency().snapshot();
            METRICS.residency(resident.cpuBytes(), resident.gpuBytes(), resident.textureBytes());
            METRICS.endFrame();
        }
    }

    static boolean isCulled(GltfAsset.Bounds bounds, Matrix4fc transform, Vec3 camera, Frustum frustum) {
        if (bounds == null || bounds.isEmpty()) return false;
        Vector3f center = transform.transformPosition((bounds.minX() + bounds.maxX()) * .5f, (bounds.minY() + bounds.maxY()) * .5f, (bounds.minZ() + bounds.maxZ()) * .5f, new Vector3f());
        Vector3f corner = transform.transformPosition(bounds.maxX(), bounds.maxY(), bounds.maxZ(), new Vector3f());
        float radius = center.distance(corner);
        if (center.lengthSquared() > (budget.maxDrawDistance() + radius) * (budget.maxDrawDistance() + radius)) return true;
        if (frustum == null) return false;
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
        Vector3f point = new Vector3f();
        for (int ix = 0; ix < 2; ix++) for (int iy = 0; iy < 2; iy++) for (int iz = 0; iz < 2; iz++) {
            transform.transformPosition(ix == 0 ? bounds.minX() : bounds.maxX(), iy == 0 ? bounds.minY() : bounds.maxY(), iz == 0 ? bounds.minZ() : bounds.maxZ(), point);
            minX = Math.min(minX, (float) (camera.x + point.x)); minY = Math.min(minY, (float) (camera.y + point.y)); minZ = Math.min(minZ, (float) (camera.z + point.z));
            maxX = Math.max(maxX, (float) (camera.x + point.x)); maxY = Math.max(maxY, (float) (camera.y + point.y)); maxZ = Math.max(maxZ, (float) (camera.z + point.z));
        }
        return !frustum.isVisible(new AABB(minX, minY, minZ, maxX, maxY, maxZ));
    }

    static float lightFactor(ModelRenderAsset.Primitive primitive, Matrix4fc relative, Vec3 camera) {
        return primitive.material.unlit() ? 1 : sampleLight(primitive, relative, camera).factor();
    }

    static int packedLight(ModelRenderAsset.Primitive primitive, Matrix4fc relative, Vec3 camera) {
        return sampleLight(primitive, relative, camera).packed();
    }

    private static ModelRenderRendererLight sampleLight(ModelRenderAsset.Primitive primitive, Matrix4fc relative, Vec3 camera) {
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
                        .setColor(Math.clamp(Math.round(((color >>> 16) & 255) * factor[0]), 0, 255),
                                Math.clamp(Math.round(((color >>> 8) & 255) * factor[1]), 0, 255),
                                Math.clamp(Math.round((color & 255) * factor[2]), 0, 255), alpha)
                        .setUv(primitive.baseUv[uv], primitive.baseUv[uv + 1])
                        .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                        .setNormal(pose, primitive.normals[at], primitive.normals[at + 1], primitive.normals[at + 2]);
                continue;
            }
            int u = Float.floatToRawIntBits(primitive.emissionUv[uv]), v = Float.floatToRawIntBits(primitive.emissionUv[uv + 1]);
            out.addVertex(pose, primitive.positions[at], primitive.positions[at + 1], primitive.positions[at + 2]).setColor(primitive.colors[index]).setUv(primitive.baseUv[uv], primitive.baseUv[uv + 1]).setUv1(u & 65535, u >>> 16).setUv2(v & 65535, v >>> 16).setLineWidth((float) light).setNormal(pose, primitive.normals[at], primitive.normals[at + 1], primitive.normals[at + 2]);
        }
    }

    private static void failed(RuntimeException error) { String message = error.toString(); if (!message.equals(lastFailure)) GeometryNode.LOGGER.error("P4 static model renderer stopped affected pass", error); lastFailure = message; }
    public static Map<String, Object> report() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("backend", IRIS.active() ? (irisResidentEnabled ? "P5 Iris shared resident main + shadow" : "P5 Iris immediate compatibility comparison")
                : DIRECT_GPU_ENABLED ? "P4 native resident indexed opaque + sorted transparent"
                : "P4 native collector opaque + sorted transparent");
        result.put("visualVerification", "UNTESTED");
        result.put("geometryTransferScope", "Model-owned vertex/index buffers and requested dynamic UBO slices; excludes host shared sequential-index cache and driver-internal transfers");
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
        result.put("lightSampleCache", LIGHT_CACHE.report());
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
        result.put("limitations", List.of(
                "Iris/shaderpack visual pixels and per-pack capability remain UNTESTED until manual capture",
                "Iris main/shadow share entity-format resident geometry by default; new path pixels and performance remain UNTESTED",
                "Resident geometry builds and uploads triangle-aligned chunks across frames; GPU allocation and individual chunk driver calls remain non-preemptible",
                "Iris MASK/BLEND alpha adaptation recognizes inspected comparisons; fixed pack alpha floors/custom rules are reported as degraded",
                "OPAQUE/MASK disable pack blending within the draw and restore all indexed blend state; unlit uses the emissive route; full PBR is P7",
                "BLEND draws native forward into resolved world color/depth after Iris final; preserves background instead of writing packed Gbuffers; worker BSP splits intra-scene intersections, bounded centroid fallback; instance groups and vanilla transparency remain approximate",
                "P4 culling includes distance/sphere and camera frustum; shadow callback uses an independent camera without main-view culling",
                "resident geometry uses full-bright vertices and supplies spatial light through the dynamic color factor",
                "native opaque resident rendering is opt-in; explicit compatibility mode intentionally rebuilds vertices; default BLEND uploads only changed indices",
                "spatial light samples persist across frames and require explicit render-thread invalidation; geometry/GPU residency is not rebuilt"));
        return result;
    }
}
