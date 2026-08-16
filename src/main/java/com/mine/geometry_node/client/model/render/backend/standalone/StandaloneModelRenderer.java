package com.mine.geometry_node.client.model.render.backend.standalone;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.client.model.gpu.*;
import com.mine.geometry_node.client.model.gpu.minecraft.MinecraftModelGpuAccess;
import com.mine.geometry_node.client.model.gpu.minecraft.MinecraftModelSamplerCache;
import com.mine.geometry_node.client.model.render.backend.common.ModelRenderBounds;
import com.mine.geometry_node.client.model.runtime.*;
import com.mine.geometry_node.core.engine.system.model.domain.*;
import com.mojang.blaze3d.buffers.*;
import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.systems.*;
import com.mojang.blaze3d.textures.*;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.*;

import java.util.*;

/** Frame-level world renderer for every READY model instance in the shared registry. */
public final class StandaloneModelRenderer {
    private static GpuQuery pendingGpuQuery;
    private static final ModelSkinPaletteArena SKIN_PALETTES = new ModelSkinPaletteArena();
    private static final ModelMaterialUniformArena MATERIALS = new ModelMaterialUniformArena();
    private static final ModelProjectionUniformArena PROJECTIONS = new ModelProjectionUniformArena();
    private static final MinecraftModelSamplerCache SAMPLERS = new MinecraftModelSamplerCache();
    private static final ModelFallbackTextures FALLBACK_TEXTURES = new ModelFallbackTextures();

    private StandaloneModelRenderer() {}

    public static void render(Matrix4fc modelViewMatrix, net.minecraft.client.Camera camera) {
        ClientModelRuntime runtime = ClientModelRuntime.INSTANCE;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            pollGpuTime(runtime);
            return;
        }
        long started = System.nanoTime();
        pollGpuTime(runtime);
        PreparedFrame frame = prepareDraws(minecraft, modelViewMatrix, camera.position(), camera.getCullFrustum(),
                runtime.instances().readySnapshot(), null, false);
        List<PreparedDraw> prepared = frame.draws();
        if (prepared.isEmpty()) {
            runtime.recordFrame(0, 0, frame.singularTransformSkips(), System.nanoTime() - started, -1);
            return;
        }

        RenderTarget target = minecraft.getMainRenderTarget();
        GpuTextureView color = RenderSystem.outputColorTextureOverride != null
                ? RenderSystem.outputColorTextureOverride : target.getColorTextureView();
        GpuTextureView depth = RenderSystem.outputDepthTextureOverride != null
                ? RenderSystem.outputDepthTextureOverride : target.getDepthTextureView();
        if (color == null || depth == null) return;
        drawPrepared(prepared, color, depth, null, true);
        long triangles = prepared.stream().mapToLong(draw -> draw.indexCount() / 3L).sum();
        runtime.recordFrame(prepared.size(), triangles, frame.singularTransformSkips(),
                System.nanoTime() - started, -1);
    }

    /** Called only from Iris' public shadow callback while its shadow targets are bound. */
    public static int renderShadow(Matrix4fc modelViewMatrix, Matrix4fc projectionMatrix,
                                   double cameraX, double cameraY, double cameraZ,
                                   GpuTextureView color, GpuTextureView depth, ModelShadowPhase phase,
                                   boolean opaqueTranslucencyFallback) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return 0;
        PreparedFrame frame = prepareDraws(minecraft, modelViewMatrix, new Vec3(cameraX, cameraY, cameraZ), null,
                ClientModelRuntime.INSTANCE.instances().readySnapshot(), phase, opaqueTranslucencyFallback);
        if (frame.draws().isEmpty()) return 0;
        drawPrepared(frame.draws(), color, depth, projectionMatrix, false);
        return frame.draws().size();
    }

    private static void drawPrepared(List<PreparedDraw> prepared, GpuTextureView color,
                                     GpuTextureView depth, Matrix4fc projection, boolean measureGpuTime) {
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        GpuBufferSlice projectionUniform = projection == null ? null : PROJECTIONS.upload(encoder, projection);
        Map<ModelSkinPaletteArena.PaletteKey, float[]> paletteData = new LinkedHashMap<>();
        for (PreparedDraw draw : prepared) {
            if (draw.paletteKey() != null) paletteData.putIfAbsent(draw.paletteKey(), draw.skinPalette());
        }
        Map<ModelSkinPaletteArena.PaletteKey, GpuBufferSlice> palettes = SKIN_PALETTES.upload(encoder, paletteData);
        List<GpuBufferSlice> materials = MATERIALS.upload(encoder, prepared.stream().map(PreparedDraw::material).toList());
        GpuQuery query = measureGpuTime && pendingGpuQuery == null ? encoder.timerQueryBegin() : null;
        try (RenderPass pass = encoder.createRenderPass(
                () -> "GeometryNode model instances", color, OptionalInt.empty(), depth, OptionalDouble.empty())) {
            for (int drawIndex = 0; drawIndex < prepared.size(); drawIndex++) {
                PreparedDraw draw = prepared.get(drawIndex);
                pass.setPipeline(draw.pipeline());
                RenderSystem.bindDefaultUniforms(pass);
                if (projectionUniform != null) pass.setUniform("Projection", projectionUniform);
                pass.setUniform("DynamicTransforms", draw.dynamicTransforms());
                pass.setUniform("ModelMaterial", materials.get(drawIndex));
                pass.setVertexBuffer(0, draw.vertexBuffer());
                pass.setIndexBuffer(draw.indexBuffer(), VertexFormat.IndexType.INT);
                pass.bindTexture("Sampler0", draw.baseColorTexture(), draw.baseSampler());
                pass.bindTexture("Sampler1", draw.metallicRoughnessTexture(), draw.metallicRoughnessSampler());
                pass.bindTexture("Sampler2", draw.normalTexture(), draw.normalSampler());
                pass.bindTexture("Sampler3", draw.occlusionTexture(), draw.occlusionSampler());
                pass.bindTexture("Sampler4", draw.emissiveTexture(), draw.emissiveSampler());
                if (draw.paletteKey() != null) pass.setUniform("SkinPalette", palettes.get(draw.paletteKey()));
                pass.drawIndexed(0, draw.firstIndex(), draw.indexCount(), 1);
            }
        } finally {
            if (query != null) {
                encoder.timerQueryEnd(query);
                pendingGpuQuery = query;
            }
        }
    }

    private static PreparedFrame prepareDraws(Minecraft minecraft, Matrix4fc viewMatrix, Vec3 cameraPos,
                                               Frustum frustum,
                                               List<ClientModelInstanceRegistry.ReadyInstance> instances,
                                               ModelShadowPhase shadowPhase, boolean opaqueTranslucencyFallback) {
        ModelDimensionId dimension = new ModelDimensionId(minecraft.level.dimension().identifier().toString());
        List<DrawCandidate> candidates = new ArrayList<>();
        int singularSkips = 0;
        for (ClientModelInstanceRegistry.ReadyInstance instance : instances) {
            ModelInstanceState state = instance.state();
            if (!state.visible() || !dimension.equals(state.dimension())) continue;
            AABB bounds = ModelRenderBounds.worldBounds(instance.pose().modelBounds(), state.placement());
            if (state.maxDistance() > 0 && bounds.distanceToSqr(cameraPos) > state.maxDistance() * state.maxDistance()) continue;
            boolean deforms = !instance.resource().definition().skins().isEmpty();
            if (!deforms && frustum != null && !frustum.isVisible(bounds)) continue;
            singularSkips += collectCandidates(minecraft, viewMatrix, cameraPos, frustum, instance, candidates,
                    shadowPhase, opaqueTranslucencyFallback);
        }
        candidates.sort(Comparator.comparing(DrawCandidate::sortKey));
        List<PreparedDraw> prepared = new ArrayList<>(candidates.size());
        for (DrawCandidate candidate : candidates) prepared.add(candidate.prepare(shadowPhase != null));
        return new PreparedFrame(List.copyOf(prepared), singularSkips);
    }

    private static int collectCandidates(Minecraft minecraft, Matrix4fc viewMatrix, Vec3 camera, Frustum frustum,
                                          ClientModelInstanceRegistry.ReadyInstance instance,
                                          List<DrawCandidate> output, ModelShadowPhase shadowPhase,
                                          boolean opaqueTranslucencyFallback) {
        boolean shadowPass = shadowPhase != null;
        LoadedModelResource loaded = instance.resource();
        ModelGpuResource resource = loaded.standaloneGpuResource().orElse(null);
        if (resource == null) {
            loaded.standaloneGpuFailureForReport().ifPresent(failure -> GeometryNode.LOGGER.error(
                    "Standalone GPU artifact failed for {}: {}", loaded.asset().normalizedPath(),
                    failure.getMessage(), failure));
            return 0;
        }
        ModelInstanceState state = instance.state();
        ModelInstancePlacement placement = state.placement();
        Matrix4f base = instanceMatrix(viewMatrix, placement, camera);
        float light = lightFactor(minecraft, placement);
        Vector3f lightDirection = ModelDrawContract.lightDirectionInView(viewMatrix);
        int singularSkips = 0;
        for (int rangeIndex = 0; rangeIndex < resource.drawRanges().size(); rangeIndex++) {
            ModelGpuDrawRange draw = resource.drawRanges().get(rangeIndex);
            if (!loaded.metadata().nodeVisible(draw.nodeIndex()) || !state.nodeState().visible(draw.nodeIndex())) continue;
            boolean skinned = loaded.definition().nodes().get(draw.nodeIndex()).skinIndex() >= 0;
            if (shadowPass && skinned) continue;
            ModelBounds nodeBounds = instance.pose().nodeWorldBounds(draw.nodeIndex());
            if (!skinned && nodeBounds != null) {
                AABB partBounds = ModelRenderBounds.worldBounds(nodeBounds, placement);
                if (frustum != null && !frustum.isVisible(partBounds)) continue;
                if (state.maxDistance() > 0 && partBounds.distanceToSqr(camera) > state.maxDistance() * state.maxDistance()) continue;
            }
            ModelGpuLayoutGroup group = resource.layoutGroups().get(draw.layoutGroupIndex());
            StaticModelMaterial material = loaded.metadata().material(draw.materialIndex());
            ModelAlphaMode effectiveAlphaMode = shadowPass
                    ? ModelShadowPolicy.effectiveAlphaMode(
                            material.alphaMode(), placement.alpha(), opaqueTranslucencyFallback)
                    : material.alphaMode();
            if (shadowPass && !ModelShadowPolicy.castsShadow(
                    effectiveAlphaMode, placement.alpha(), opaqueTranslucencyFallback, shadowPhase)) continue;
            Matrix4f nodeWorld = instance.pose().worldMatrix(draw.nodeIndex());
            Matrix4f transform = new Matrix4f(base).mul(nodeWorld);
            Matrix4f modelTransform = new Matrix4f().rotate(placement.rotation()).scale(placement.scale())
                    .mul(nodeWorld);
            double determinant = ModelTransformMath.normalizedDeterminant(modelTransform);
            if (!Double.isFinite(determinant)
                    || java.lang.Math.abs(determinant) <= ModelTransformMath.MIN_NORMALIZED_DETERMINANT) {
                singularSkips++;
                continue;
            }
            boolean forceOpaqueAlpha = shadowPass && effectiveAlphaMode == ModelAlphaMode.OPAQUE
                    && (material.alphaMode() == ModelAlphaMode.BLEND || placement.alpha() < 0.999F);
            ModelDrawContract contract = ModelDrawContract.resolve(
                    group.layout(), material, placement, light, determinant < 0.0,
                    effectiveAlphaMode, forceOpaqueAlpha);
            ModelPipelineKey pipelineKey = contract.pipeline();
            float depth = pipelineKey.translucent() ? ModelDrawOrdering.viewDepth(draw.localBounds(), transform) : 0.0F;
            DrawSortKey sortKey = new DrawSortKey(pipelineKey.translucent() ? 1 : 0,
                    depth,
                    pipelineKey.alphaMode().ordinal(), material.baseColorTexture().present(), pipelineKey.doubleSided(),
                    pipelineKey.mirrored(),
                    pipelineKey.translucent(), pipelineKey.layout().elements().toString(),
                    loaded.asset().cacheIdentity(), draw.layoutGroupIndex(), draw.materialIndex(), rangeIndex,
                    instance.id().value());
            GpuTextureView texture = ModelMaterialBindings.baseColor(resource, material, material.baseColorTexture().present());
            if (shadowPhase == ModelShadowPhase.TRANSLUCENT && material.baseColorTexture().present()) {
                texture = ModelMaterialBindings.shadowOpacity(resource, material);
            }
            GpuTextureView emissiveTexture = ModelMaterialBindings.emissive(resource, material, material.emissiveTexture().present());
            GpuTextureView metallicRoughnessTexture = ModelMaterialBindings.metallicRoughness(resource, material);
            GpuTextureView normalTexture = ModelMaterialBindings.normal(resource, material);
            GpuTextureView occlusionTexture = ModelMaterialBindings.occlusion(resource, material);
            ModelSkinPaletteArena.PaletteKey paletteKey = null;
            float[] skinPalette = null;
            if (pipelineKey.skinned()) {
                try {
                    int skinIndex = loaded.definition().nodes().get(draw.nodeIndex()).skinIndex();
                    paletteKey = new ModelSkinPaletteArena.PaletteKey(instance.id().value(), skinIndex,
                            draw.nodeIndex(), instance.pose().revision());
                    skinPalette = instance.pose().skinPalette(draw.nodeIndex());
                } catch (IllegalStateException exception) {
                    singularSkips++;
                    continue;
                }
            }
            output.add(new DrawCandidate(sortKey, pipelineKey, transform, lightDirection, contract.worldLight(), contract.fullBright(),
                    MinecraftModelGpuAccess.buffer(group.vertexBuffer()),
                    MinecraftModelGpuAccess.buffer(group.indexBuffer()), texture, metallicRoughnessTexture,
                    normalTexture, occlusionTexture, emissiveTexture, materialUniform(contract, material, draw.physicalUvSlots()),
                    material.baseColorTexture().sampler(), material.metallicRoughnessTexture().sampler(),
                    material.normalTexture().sampler(), material.occlusionTexture().sampler(), material.emissiveTexture().sampler(),
                    draw.firstIndex(), draw.indexCount(), paletteKey, skinPalette));
        }
        return singularSkips;
    }

    public static void clear() {
        if (pendingGpuQuery != null) {
            pendingGpuQuery.close();
            pendingGpuQuery = null;
        }
        SKIN_PALETTES.close();
        MATERIALS.close();
        PROJECTIONS.close();
        SAMPLERS.close();
        FALLBACK_TEXTURES.close();
    }

    private static GpuTextureView orNeutral(GpuTextureView texture) { return texture == null ? FALLBACK_TEXTURES.neutral() : texture; }
    private static GpuTextureView orNormal(GpuTextureView texture) { return texture == null ? FALLBACK_TEXTURES.normal() : texture; }

    private static ModelMaterialUniform materialUniform(ModelDrawContract contract, StaticModelMaterial material,
                                                        Map<Integer, Integer> uvSlots) {
        return new ModelMaterialUniform(contract.color(), new Vector4f(contract.emissive(), contract.alphaCutoff()),
                new Vector4f(material.metallicFactor(), material.roughnessFactor(), material.normalScale(), material.occlusionStrength()),
                new Vector4f(present(material.baseColorTexture()), present(material.metallicRoughnessTexture()),
                        present(material.normalTexture()), present(material.occlusionTexture())),
                new Vector4f(present(material.emissiveTexture()), 0, 0, 0),
                uvSlots0(material, uvSlots), new Vector4f(physicalUv(uvSlots, material.emissiveTexture()), 0, 0, 0),
                List.of(material.baseColorTexture().transform(), material.metallicRoughnessTexture().transform(),
                        material.normalTexture().transform(), material.occlusionTexture().transform(),
                        material.emissiveTexture().transform()));
    }

    private static float present(StaticModelTexture texture) { return texture.present() ? 1.0F : 0.0F; }

    private static Vector4f uvSlots0(StaticModelMaterial material, Map<Integer, Integer> uvSlots) {
        return new Vector4f(physicalUv(uvSlots, material.baseColorTexture()),
                physicalUv(uvSlots, material.metallicRoughnessTexture()),
                physicalUv(uvSlots, material.normalTexture()), physicalUv(uvSlots, material.occlusionTexture()));
    }

    private static float physicalUv(Map<Integer, Integer> uvSlots, StaticModelTexture selected) {
        if (!selected.present()) return 0;
        Integer slot = uvSlots.get(selected.texCoord());
        if (slot == null) throw new IllegalStateException("draw is missing projected texture coordinates " + selected.texCoord());
        return slot;
    }

    private static Matrix4f instanceMatrix(Matrix4fc view, ModelInstancePlacement placement, Vec3 camera) {
        Vector3d position = placement.position();
        return new Matrix4f(view).translate((float) (position.x - camera.x), (float) (position.y - camera.y),
                (float) (position.z - camera.z)).rotate(placement.rotation()).scale(placement.scale());
    }

    private static float lightFactor(Minecraft minecraft, ModelInstancePlacement placement) {
        if (placement.fullBright()) return 1;
        Vector3d position = placement.position();
        int light = minecraft.level.getLightEngine().getRawBrightness(
                BlockPos.containing(position.x, position.y, position.z), 0);
        return 0.2F + 0.8F * light / 15F;
    }

    private static void pollGpuTime(ClientModelRuntime runtime) {
        if (pendingGpuQuery == null) return;
        var value = pendingGpuQuery.getValue();
        if (value.isPresent()) {
            LocalModelStatus status = runtime.status();
            runtime.recordFrame(status.drawCalls(), status.submittedTriangles(), status.singularTransformSkips(),
                    status.lastRenderCpuNanos(), value.getAsLong());
            pendingGpuQuery.close();
            pendingGpuQuery = null;
        }
    }

    private record DrawSortKey(int layer, float depth, int alphaMode, boolean textured, boolean doubleSided, boolean mirrored,
                               boolean translucent, String layout, String asset, int layoutGroup,
                               int material, int range, String instance) implements Comparable<DrawSortKey> {
        private static final Comparator<DrawSortKey> TIES = Comparator.comparingInt(DrawSortKey::alphaMode)
                .thenComparing(DrawSortKey::textured)
                .thenComparing(DrawSortKey::doubleSided).thenComparing(DrawSortKey::translucent)
                .thenComparing(DrawSortKey::mirrored)
                .thenComparing(DrawSortKey::layout).thenComparing(DrawSortKey::asset)
                .thenComparingInt(DrawSortKey::layoutGroup).thenComparingInt(DrawSortKey::material)
                .thenComparingInt(DrawSortKey::range).thenComparing(DrawSortKey::instance);
        @Override public int compareTo(DrawSortKey other) {
            int layerOrder = Integer.compare(layer, other.layer);
            if (layerOrder != 0) return layerOrder;
            if (layer != 0) {
                int depthOrder = ModelDrawOrdering.compareTransparentDepth(depth, other.depth);
                if (depthOrder != 0) return depthOrder;
            }
            return TIES.compare(this, other);
        }
    }

    private record DrawCandidate(DrawSortKey sortKey, ModelPipelineKey pipelineKey, Matrix4f transform,
                                 Vector3f lightDirection, float worldLight, boolean fullBright, GpuBuffer vertexBuffer,
                                 GpuBuffer indexBuffer, GpuTextureView texture, GpuTextureView metallicRoughnessTexture,
                                 GpuTextureView normalTexture, GpuTextureView occlusionTexture, GpuTextureView emissiveTexture,
                                 ModelMaterialUniform material, ModelTextureSampler baseSampler,
                                 ModelTextureSampler metallicRoughnessSampler, ModelTextureSampler normalSampler,
                                 ModelTextureSampler occlusionSampler, ModelTextureSampler emissiveSampler,
                                 int firstIndex, int indexCount, ModelSkinPaletteArena.PaletteKey paletteKey,
                                 float[] skinPalette) {
        PreparedDraw prepare(boolean shadowPass) {
            GpuBufferSlice dynamic = ModelDynamicUniformWriter.write(transform, lightDirection, worldLight, fullBright);
            RenderPipeline pipeline = shadowPass ? StandaloneRenderPipelines.getShadow(pipelineKey)
                    : StandaloneRenderPipelines.get(pipelineKey);
            return new PreparedDraw(pipeline, dynamic, vertexBuffer,
                    indexBuffer, orNeutral(texture), orNeutral(metallicRoughnessTexture), orNormal(normalTexture),
                    orNeutral(occlusionTexture), orNeutral(emissiveTexture), material,
                    SAMPLERS.get(baseSampler), SAMPLERS.get(metallicRoughnessSampler), SAMPLERS.get(normalSampler),
                    SAMPLERS.get(occlusionSampler), SAMPLERS.get(emissiveSampler),
                    firstIndex, indexCount, paletteKey, skinPalette);
        }
    }

    private record PreparedDraw(RenderPipeline pipeline, GpuBufferSlice dynamicTransforms,
                                GpuBuffer vertexBuffer, GpuBuffer indexBuffer, GpuTextureView baseColorTexture,
                                GpuTextureView metallicRoughnessTexture, GpuTextureView normalTexture,
                                GpuTextureView occlusionTexture, GpuTextureView emissiveTexture,
                                ModelMaterialUniform material, GpuSampler baseSampler,
                                GpuSampler metallicRoughnessSampler, GpuSampler normalSampler,
                                GpuSampler occlusionSampler, GpuSampler emissiveSampler, int firstIndex, int indexCount,
                                ModelSkinPaletteArena.PaletteKey paletteKey, float[] skinPalette) {}
    private record PreparedFrame(List<PreparedDraw> draws, int singularTransformSkips) {}
}
