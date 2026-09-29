package com.mine.geometry_node.client.model.render;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.core.engine.system.model.asset.GltfAsset;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
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
    /** SubmitCustomGeometry runs inside the host pass; direct RenderPass is opt-in until a safe stage adapter is installed. */
    private static final boolean DIRECT_GPU_ENABLED = Boolean.getBoolean("geometry_node.model.direct_gpu");
    private static final ModelSpatialLightCache LIGHT_CACHE = new ModelSpatialLightCache();
    private static Map<BatchKey, List<DirectDraw>> queuedDirect = Map.of();

    private record DirectDraw(ModelRenderAsset asset, ModelRenderAsset.GpuPrimitive mesh, ModelRenderAsset.MaterialBinding binding,
                              Matrix4f transform, ModelRenderAsset.Primitive primitive, ModelRenderRendererLight light) {}
    private record FallbackDraw(ModelRenderAsset.Primitive primitive, Matrix4f transform, boolean mirrored, int packedLight, RenderType type) {}
    private record Triangle(FallbackDraw draw, int offset, float depth) {}
    private record BatchKey(ModelRenderAsset.MaterialBinding binding) {}
    private record ModelRenderRendererLight(int packed, float factor) {}

    private ModelStaticRenderer() {}

    public static void setBudget(ModelRenderBudget next) { budget = Objects.requireNonNull(next); ModelRenderAsset.residency().setBudget(next); }
    public static ModelRenderBudget budget() { return budget; }
    public static ModelRenderMetrics metrics() { return METRICS; }
    /** Versioned spatial-light invalidation contract; render-thread only and absolute world block coordinates. */
    public static void invalidateLight(BlockPos position) { RenderSystem.assertOnRenderThread(); LIGHT_CACHE.invalidate(Objects.requireNonNull(position)); }
    public static void invalidateLightRegion(BlockPos center, int radius, String reason) { RenderSystem.assertOnRenderThread(); LIGHT_CACHE.invalidateRegion(Objects.requireNonNull(center), radius, reason); }
    public static void invalidateAllLight(String reason) { RenderSystem.assertOnRenderThread(); LIGHT_CACHE.invalidateAll(reason); }

    public static void submit(PoseStack ignoredHostStack, SubmitNodeCollector collector, Vec3 camera, Frustum frustum, List<ModelRenderInstance> instances) {
        RenderSystem.assertOnRenderThread();
        frame++; METRICS.beginFrame(frame); ModelRenderAsset.residency().beginFrame(); LIGHT_CACHE.beginFrame(Minecraft.getInstance().level);
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
                        METRICS.visible();
                        if (!assetVisible) { asset.touch(); assetVisible = true; }
                        if (primitive.material.alphaMode() == GltfAsset.AlphaMode.BLEND) continue;
                        ModelRenderRendererLight light = sampleLight(primitive, transform, camera);
                        ModelRenderAsset.GpuPrimitive mesh = asset.gpuPrimitive(primitive);
                        ModelRenderAsset.MaterialBinding binding = asset.binding(primitive.material);
                        if (DIRECT_GPU_ENABLED && mesh != null && binding != null) {
                            direct.computeIfAbsent(new BatchKey(binding), key -> new ArrayList<>())
                                    .add(new DirectDraw(asset, mesh, binding, new Matrix4f(transform), primitive, light));
                        } else {
                            fallback.computeIfAbsent(asset.renderType(primitive.material), key -> new ArrayList<>())
                                    .add(new FallbackDraw(primitive, new Matrix4f(transform), mirrored, light.packed(), asset.renderType(primitive.material)));
                        }
                    }
                }
            }
            queuedDirect = Map.copyOf(direct);
            submitFallbackOpaque(collector, fallback);
        } catch (RuntimeException error) {
            failed(error);
        } finally {
            METRICS.residency(ModelRenderAsset.residency().snapshot().cpuBytes(), ModelRenderAsset.residency().snapshot().gpuBytes(), ModelRenderAsset.residency().snapshot().textureBytes());
        }
    }

    /** Draws the resident opaque queue during AfterOpaqueFeatures, when the main target is writable. */
    public static void drawResidentOpaque() {
        RenderSystem.assertOnRenderThread();
        Map<BatchKey, List<DirectDraw>> batches = queuedDirect;
        queuedDirect = Map.of();
        drawDirectOpaque(batches);
    }

    private static void drawDirectOpaque(Map<BatchKey, List<DirectDraw>> batches) {
        if (batches.isEmpty()) return;
        Minecraft minecraft = Minecraft.getInstance(); var target = minecraft.getMainRenderTarget();
        GpuTextureView color = RenderSystem.outputColorTextureOverride != null ? RenderSystem.outputColorTextureOverride : target.getColorTextureView();
        GpuTextureView depth = RenderSystem.outputDepthTextureOverride != null ? RenderSystem.outputDepthTextureOverride : target.getDepthTextureView();
        if (color == null) return;
        for (Map.Entry<BatchKey, List<DirectDraw>> entry : batches.entrySet()) {
            ModelRenderAsset.MaterialBinding binding = entry.getKey().binding();
            try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                    () -> "GeometryNode P4 resident opaque", color, OptionalInt.empty(), depth, OptionalDouble.empty())) {
                pass.setPipeline(ModelStaticPipelines.pipeline(false, binding.doubleSided()));
                RenderSystem.bindDefaultUniforms(pass);
                bind(pass, minecraft, "BaseTexture", binding.base());
                bind(pass, minecraft, "EmissionTexture", binding.emission());
                bind(pass, minecraft, "MaterialData", binding.data());
                pass.bindTexture("Sampler2", minecraft.gameRenderer.lightmap(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
                for (DirectDraw draw : entry.getValue()) {
                    Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(draw.transform());
                    float factor = draw.primitive().material.unlit() ? 1.0f : draw.light().factor();
                    GpuBufferSlice dynamic = RenderSystem.getDynamicUniforms().writeTransform(modelView, new Vector4f(1.0f, 1.0f, 1.0f, factor), new Vector3f(), new Matrix4f());
                    pass.setUniform("DynamicTransforms", dynamic);
                    pass.setVertexBuffer(0, draw.mesh().vertices());
                    pass.setIndexBuffer(draw.mesh().indices(), VertexFormat.IndexType.INT);
                    pass.drawIndexed(0, 0, draw.mesh().indexCount(), 1);
                    opaqueSubmissions++; METRICS.opaqueDraw();
                }
            } catch (RuntimeException error) { failed(error); }
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
                for (FallbackDraw draw : entry.getValue()) {
                    PoseStack local = new PoseStack(); local.mulPose(draw.transform());
                    emitTriangle(draw.primitive(), local.last(), draw.mirrored(), draw.packedLight(), 0, out);
                    for (int offset = 3; offset < draw.primitive().indices.length; offset += 3) emitTriangle(draw.primitive(), local.last(), draw.mirrored(), draw.packedLight(), offset, out);
                }
            });
            opaqueSubmissions++; METRICS.opaqueDraw();
        }
    }

    /** Called after vanilla translucent particles; P2 cross-sorts model triangles, not vanilla translucent geometry. */
    public static void drawTransparent(Matrix4fc view, Vec3 camera, Frustum frustum, List<ModelRenderInstance> instances) {
        RenderSystem.assertOnRenderThread(); List<Triangle> triangles = new ArrayList<>();
        try {
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
                            triangles.add(new Triangle(new FallbackDraw(primitive, new Matrix4f(transform), mirrored, light.packed(), asset.renderType(primitive.material)), offset, center.z));
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
                        BufferBuilder builder = new BufferBuilder(arena, VertexFormat.Mode.TRIANGLES, ModelStaticPipelines.FORMAT);
                        for (int i = begin; i < end; i++) { Triangle triangle = triangles.get(i); FallbackDraw draw = triangle.draw(); PoseStack local = new PoseStack(); local.mulPose(draw.transform()); emitTriangle(draw.primitive(), local.last(), draw.mirrored(), draw.packedLight(), triangle.offset(), builder); }
                        MeshData mesh = builder.buildOrThrow(); type.draw(mesh); mesh.close(); transparentDraws++; transparentTriangles += end - begin; METRICS.transparentDraw(end - begin); begin = end;
                    }
                }
            } finally { global.popMatrix(); }
        } catch (RuntimeException error) { failed(error); }
        finally { METRICS.residency(ModelRenderAsset.residency().snapshot().cpuBytes(), ModelRenderAsset.residency().snapshot().gpuBytes(), ModelRenderAsset.residency().snapshot().textureBytes()); METRICS.endFrame(); }
    }

    private static boolean isCulled(GltfAsset.Bounds bounds, Matrix4fc transform, Vec3 camera, Frustum frustum) {
        if (bounds == null || bounds.isEmpty()) return false;
        Vector3f center = transform.transformPosition((bounds.minX() + bounds.maxX()) * .5f, (bounds.minY() + bounds.maxY()) * .5f, (bounds.minZ() + bounds.maxZ()) * .5f, new Vector3f());
        Vector3f corner = transform.transformPosition(bounds.maxX(), bounds.maxY(), bounds.maxZ(), new Vector3f());
        float radius = center.distance(corner);
        if (center.lengthSquared() > (budget.maxDrawDistance() + radius) * (budget.maxDrawDistance() + radius)) return true;
        if (frustum == null) return false;
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
        for (int ix = 0; ix < 2; ix++) for (int iy = 0; iy < 2; iy++) for (int iz = 0; iz < 2; iz++) {
            Vector3f point = transform.transformPosition(ix == 0 ? bounds.minX() : bounds.maxX(), iy == 0 ? bounds.minY() : bounds.maxY(), iz == 0 ? bounds.minZ() : bounds.maxZ(), new Vector3f());
            minX = Math.min(minX, (float) (camera.x + point.x)); minY = Math.min(minY, (float) (camera.y + point.y)); minZ = Math.min(minZ, (float) (camera.z + point.z));
            maxX = Math.max(maxX, (float) (camera.x + point.x)); maxY = Math.max(maxY, (float) (camera.y + point.y)); maxZ = Math.max(maxZ, (float) (camera.z + point.z));
        }
        return !frustum.isVisible(new AABB(minX, minY, minZ, maxX, maxY, maxZ));
    }

    private static ModelRenderRendererLight sampleLight(ModelRenderAsset.Primitive primitive, Matrix4fc relative, Vec3 camera) {
        var level = Minecraft.getInstance().level; if (level == null) return new ModelRenderRendererLight(LightCoordsUtil.pack(15, 15), 1.0f);
        GltfAsset.Bounds b = primitive.bounds; float cx = (b.minX() + b.maxX()) * .5f, cy = (b.minY() + b.maxY()) * .5f, cz = (b.minZ() + b.maxZ()) * .5f;
        float ex = (b.maxX() - b.minX()) * .5f, ey = (b.maxY() - b.minY()) * .5f, ez = (b.maxZ() - b.minZ()) * .5f;
        float[][] samples = {{cx, cy, cz}, {cx - ex, cy, cz}, {cx + ex, cy, cz}, {cx, cy - ey, cz}, {cx, cy + ey, cz}, {cx, cy, cz - ez}, {cx, cy, cz + ez}};
        int block = 0, sky = 0;
        for (float[] sample : samples) {
            Vector3f relativePoint = relative.transformPosition(sample[0], sample[1], sample[2], new Vector3f());
            BlockPos pos = BlockPos.containing(camera.x + relativePoint.x, camera.y + relativePoint.y, camera.z + relativePoint.z);
            int[] sampleLight = LIGHT_CACHE.sample(level, pos); block += sampleLight[0]; sky += sampleLight[1];
        }
        int count = samples.length; int packed = LightCoordsUtil.pack(Math.round((float) block / count), Math.round((float) sky / count));
        float factor = Math.clamp(((block / (float) count) * .35f + (sky / (float) count) * .65f) / 15.0f, .06f, 1.0f);
        return new ModelRenderRendererLight(packed, factor);
    }

    private static void emitTriangle(ModelRenderAsset.Primitive primitive, PoseStack.Pose pose, boolean mirrored, int light, int offset, VertexConsumer out) {
        for (int corner = 0; corner < 3; corner++) {
            int index = primitive.indices[offset + (mirrored && corner != 0 ? 3 - corner : corner)], at = index * 3, uv = index * 2;
            int u = Float.floatToRawIntBits(primitive.emissionUv[uv]), v = Float.floatToRawIntBits(primitive.emissionUv[uv + 1]);
            out.addVertex(pose, primitive.positions[at], primitive.positions[at + 1], primitive.positions[at + 2]).setColor(primitive.colors[index]).setUv(primitive.baseUv[uv], primitive.baseUv[uv + 1]).setUv1(u & 65535, u >>> 16).setUv2(v & 65535, v >>> 16).setLineWidth((float) light).setNormal(pose, primitive.normals[at], primitive.normals[at + 1], primitive.normals[at + 2]);
        }
    }

    private static void failed(RuntimeException error) { String message = error.toString(); if (!message.equals(lastFailure)) GeometryNode.LOGGER.error("P4 static model renderer stopped affected pass", error); lastFailure = message; }
    public static Map<String, Object> report() {
        Map<String, Object> result = new LinkedHashMap<>(); result.put("backend", DIRECT_GPU_ENABLED ? "P4 resident indexed opaque + sorted transparent fallback" : "P4 resident prepared + collector opaque fallback + sorted transparent"); result.put("visualVerification", "UNTESTED"); result.put("opaqueSubmissions", opaqueSubmissions); result.put("transparentTrianglesTotal", transparentTriangles); result.put("transparentDrawCalls", transparentDraws); result.put("lastFailure", lastFailure); result.put("budget", budget); result.put("residency", ModelRenderAsset.residency().snapshot()); result.put("metrics", METRICS.report()); result.put("lightSampleCache", LIGHT_CACHE.report()); result.put("limitations", List.of("P5 shaderpack/shadow routing is still a later compatibility stage", "BLEND remains CPU triangle-sorted and cross-sorts only model geometry", "P4 culling includes distance/sphere and camera frustum; occlusion queries are deferred", "resident geometry uses full-bright vertices and supplies spatial light through the dynamic color factor", "direct GPU RenderPass is disabled by default because SubmitCustomGeometry runs inside the host pass; collector fallback remains the safe visual path", "spatial light samples persist across frames and require explicit render-thread invalidation; geometry/GPU residency is not rebuilt")); return result;
    }
}
