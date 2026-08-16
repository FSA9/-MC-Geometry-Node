package com.mine.geometry_node.client.model.render.backend.host.entity;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.client.model.render.backend.host.material.*;
import com.mine.geometry_node.client.model.render.backend.host.geometry.HostEntityGeometry;
import com.mine.geometry_node.client.model.render.backend.host.geometry.HostGeometryProjector;
import com.mine.geometry_node.client.model.render.backend.host.iris.labpbr.IrisLabPbrProjector;
import com.mine.geometry_node.client.model.render.backend.host.iris.labpbr.LabPbrProjectionEncoder;
import com.mine.geometry_node.client.model.render.backend.host.iris.labpbr.ModelProjectorCapability;
import com.mine.geometry_node.client.model.render.backend.host.iris.entity.IrisEntityTranslucency;
import com.mine.geometry_node.client.model.render.backend.host.iris.shadow.IrisShadowAdapter;
import com.mine.geometry_node.client.model.render.backend.common.ModelRenderBounds;
import com.mine.geometry_node.client.model.render.integration.*;
import com.mine.geometry_node.client.model.runtime.*;
import com.mine.geometry_node.core.engine.system.model.domain.*;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.io.IOException;
import java.util.*;

/** Minimum-loss projection into Minecraft's standard entity pipeline. */
public final class HostNativeRenderer {
    private static final int FULL_BRIGHT = 15728880;
    private static final Map<GeometryKey, HostEntityGeometry> GEOMETRY = new HashMap<>();
    private static final Set<GeometryKey> FAILED_GEOMETRY = new HashSet<>();
    private static final Map<TextureKey, CompatibilityTexture> TEXTURES = new HashMap<>();
    private static final Set<TextureKey> FAILED_TEXTURES = new HashSet<>();
    private static final Set<TextureKey> LOGGED_RUNTIME_TEXTURE_FAILURES = new HashSet<>();
    private static long nextTextureId;
    private static ModelProjectorCapability lastProjectorCapability;
    private static Set<ModelCompatibilityLoss> lastLosses = Set.of();

    private HostNativeRenderer() { }

    public static void submit(PoseStack root, SubmitNodeCollector collector) {
        if (Minecraft.getInstance().level == null) return;
        long started = System.nanoTime();
        ClientModelRuntime runtime = ClientModelRuntime.INSTANCE;
        List<ClientModelInstanceRegistry.ReadyInstance> ready = runtime.instances().readySnapshot();
        if (ready.isEmpty()) {
            prune(ready);
            lastLosses = Set.of();
            ModelIntegrationController.reportCompatibility(Set.of(), ModelIntegrationVerification.NOT_APPLICABLE,
                    List.of(), Map.of());
            runtime.recordFrame(0, 0, 0, System.nanoTime() - started, -1);
            return;
        }
        IrisLabPbrProjector.Snapshot projectorSnapshot = IrisLabPbrProjector.snapshot(
                ModelResourceReloadListener.reloadGeneration());
        ModelProjectorCapability projector = projectorSnapshot.capability();
        IrisEntityTranslucency.Snapshot translucency = IrisEntityTranslucency.snapshot();
        synchronizeCapability(projector);
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().position();
        ModelDimensionId dimension = new ModelDimensionId(Minecraft.getInstance().level.dimension().identifier().toString());
        EnumSet<ModelCompatibilityLoss> frameLosses = EnumSet.noneOf(ModelCompatibilityLoss.class);
        EnumMap<ModelDrawRejection, Integer> frameRejections = new EnumMap<>(ModelDrawRejection.class);
        List<String> runtimeFaults = new ArrayList<>();
        if (projector.runtimeFault()) {
            runtimeFaults.add(projectorSnapshot.diagnostic());
        }
        if (!IrisShadowAdapter.failure().isEmpty() && !"IRIS_ABSENT".equals(IrisShadowAdapter.failure())) {
            runtimeFaults.add("shadow-adapter:" + IrisShadowAdapter.failure());
        }
        if (translucency.diagnostic().startsWith("IRIS_TRANSLUCENCY_PROBE_FAILED:")) {
            runtimeFaults.add("entity-translucency:" + translucency.diagnostic());
        }
        FrameStatistics statistics = new FrameStatistics();
        prune(ready);
        for (ClientModelInstanceRegistry.ReadyInstance instance : ready) {
            if (!instance.state().visible() || !dimension.equals(instance.state().dimension())) continue;
            if (instance.state().maxDistance() > 0 && ModelRenderBounds.worldBounds(
                    instance.pose().modelBounds(), instance.state().placement()).distanceToSqr(camera)
                    > instance.state().maxDistance() * instance.state().maxDistance()) continue;
            submitInstance(root, collector, camera, instance, frameLosses, frameRejections,
                    runtimeFaults, statistics, projector, translucency.dedicatedProgram());
        }
        ModelIntegrationVerification verification = frameLosses.remove(ModelCompatibilityLoss.PROJECTOR_HOLDER_PENDING)
                ? ModelIntegrationVerification.PENDING
                : projector.verification();
        if (frameLosses.remove(ModelCompatibilityLoss.TEXTURE_DECODE_FAILED)) {
            runtimeFaults.add("AUXILIARY_TEXTURE_DECODE_FAILED");
        }
        if (frameLosses.remove(ModelCompatibilityLoss.PROJECTOR_RUNTIME_UNAVAILABLE)) {
            runtimeFaults.add("PROJECTOR_RUNTIME_UNAVAILABLE");
        }
        lastLosses = Set.copyOf(frameLosses);
        Set<ModelIntegrationCapability> capabilities = EnumSet.of(ModelIntegrationCapability.HOST_ENTITY_SHADER,
                ModelIntegrationCapability.BASE_COLOR, ModelIntegrationCapability.ALPHA_MODES);
        if (projector.auxiliaryEnabled()) {
            capabilities.add(ModelIntegrationCapability.LABPBR_AUXILIARY_TEXTURES);
        }
        if (IrisShadowAdapter.installed() && IrisShadowAdapter.failure().isEmpty()
                && IrisShadowAdapter.lastSubmittedDraws() > 0) {
            capabilities.add(ModelIntegrationCapability.SHADOW_CASTER_SUBMITTED);
        }
        ModelIntegrationController.reportCompatibility(projector.profile(), capabilities, lastLosses,
                verification, runtimeFaults, frameRejections);
        runtime.recordFrame(statistics.drawCalls, statistics.triangles, statistics.singularTransformSkips,
                System.nanoTime() - started, -1);
    }

    public static Set<ModelCompatibilityLoss> lastLosses() { return lastLosses; }

    public static void clear() {
        var manager = Minecraft.getInstance().getTextureManager();
        TEXTURES.values().forEach(texture -> release(manager, texture));
        TEXTURES.clear(); FAILED_TEXTURES.clear(); LOGGED_RUNTIME_TEXTURE_FAILURES.clear();
        GEOMETRY.clear(); FAILED_GEOMETRY.clear();
        nextTextureId = 0; lastProjectorCapability = null; lastLosses = Set.of();
        IrisEntityTranslucency.clear();
    }

    private static void synchronizeCapability(ModelProjectorCapability capability) {
        if (lastProjectorCapability == null) {
            lastProjectorCapability = capability;
            return;
        }
        if (lastProjectorCapability.auxiliaryEnabled() == capability.auxiliaryEnabled()) {
            lastProjectorCapability = capability;
            return;
        }
        var manager = Minecraft.getInstance().getTextureManager();
        TEXTURES.values().forEach(texture -> release(manager, texture));
        TEXTURES.clear();
        FAILED_TEXTURES.clear();
        LOGGED_RUNTIME_TEXTURE_FAILURES.clear();
        lastProjectorCapability = capability;
    }

    private static void prune(List<ClientModelInstanceRegistry.ReadyInstance> ready) {
        Set<String> assets = new HashSet<>();
        for (ClientModelInstanceRegistry.ReadyInstance instance : ready) {
            assets.add(instance.resource().asset().cacheIdentity());
        }
        GEOMETRY.keySet().removeIf(key -> !assets.contains(key.asset()));
        FAILED_GEOMETRY.removeIf(key -> !assets.contains(key.asset()));
        FAILED_TEXTURES.removeIf(key -> !assets.contains(key.asset()));
        LOGGED_RUNTIME_TEXTURE_FAILURES.removeIf(key -> !assets.contains(key.asset()));
        var manager = Minecraft.getInstance().getTextureManager();
        Iterator<Map.Entry<TextureKey, CompatibilityTexture>> iterator = TEXTURES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<TextureKey, CompatibilityTexture> entry = iterator.next();
            if (!assets.contains(entry.getKey().asset())) {
                release(manager, entry.getValue());
                iterator.remove();
            }
        }
    }

    private static void submitInstance(PoseStack root, SubmitNodeCollector collector, Vec3 camera,
                                       ClientModelInstanceRegistry.ReadyInstance instance,
                                       EnumSet<ModelCompatibilityLoss> losses,
                                       EnumMap<ModelDrawRejection, Integer> rejections,
                                       List<String> runtimeFaults, FrameStatistics statistics,
                                       ModelProjectorCapability projector, boolean dedicatedTranslucentProgram) {
        LoadedModelResource loaded = instance.resource();
        ModelDefinition definition = loaded.definition();
        ModelInstancePlacement placement = instance.state().placement();
        for (int nodeIndex = 0; nodeIndex < definition.nodes().size(); nodeIndex++) {
            ModelNode node = definition.nodes().get(nodeIndex);
            if (node.meshIndex() < 0 || !loaded.metadata().nodeVisible(nodeIndex)
                    || !instance.state().nodeState().visible(nodeIndex)) continue;
            ModelMesh mesh = definition.meshes().get(node.meshIndex());
            for (int primitiveIndex = 0; primitiveIndex < mesh.primitives().size(); primitiveIndex++) {
                ModelPrimitive primitive = mesh.primitives().get(primitiveIndex);
                StaticModelMaterial material = loaded.metadata().material(primitive.materialIndex());
                boolean labPbr = projector.auxiliaryEnabled();
                HostMaterialProjection projection = HostMaterialAnalyzer.analyze(
                        projector.profile(), material, node.skinIndex() >= 0);
                losses.addAll(projection.losses());
                if (material.alphaMode() == ModelAlphaMode.BLEND || placement.alpha() < 0.999F) {
                    losses.add(ModelCompatibilityLoss.BLEND_SHADOW_APPROXIMATED);
                }
                HostMaterialAnalyzer.addInstanceLosses(material, placement.alpha(),
                        placement.forceDoubleSided(), losses);
                boolean effectiveTranslucent = material.alphaMode() == ModelAlphaMode.BLEND
                        || placement.alpha() < 0.999F;
                boolean opaqueFallback = effectiveTranslucent && !dedicatedTranslucentProgram;
                if (opaqueFallback) losses.add(ModelCompatibilityLoss.ENTITY_TRANSLUCENCY_FALLBACK_OPAQUE);
                if (!projection.selectable()) {
                    reject(rejections, ModelDrawRejection.UNSUPPORTED_SKINNING);
                    continue;
                }
                StaticModelTexture coordinateSource = coordinateSource(material);
                GeometryKey key = new GeometryKey(loaded.asset().cacheIdentity(), node.meshIndex(), primitiveIndex,
                        coordinateSource.texCoord(), coordinateSource.transform());
                if (FAILED_GEOMETRY.contains(key)) {
                    reject(rejections, ModelDrawRejection.GEOMETRY_PROJECTION_FAILED);
                    continue;
                }
                HostEntityGeometry geometry;
                try {
                    geometry = GEOMETRY.computeIfAbsent(key, ignored -> HostGeometryProjector.project(primitive, coordinateSource));
                } catch (RuntimeException exception) {
                    reject(rejections, ModelDrawRejection.GEOMETRY_PROJECTION_FAILED);
                    runtimeFaults.add("geometry-projection:" + exception.getClass().getSimpleName());
                    if (FAILED_GEOMETRY.add(key)) {
                        GeometryNode.LOGGER.warn("Skipping compatibility draw whose geometry could not be projected: {}",
                                loaded.asset().cacheIdentity(), exception);
                    }
                    continue;
                }
                CompatibilityTexture texture;
                try {
                    texture = texture(loaded, material, labPbr, opaqueFallback);
                } catch (TextureProjectionFailure exception) {
                    TextureKey failed = textureKey(loaded, material, labPbr, opaqueFallback);
                    if (exception.cacheForAssetLifetime()) {
                        runtimeFaults.add("texture-decode:" + exception.getClass().getSimpleName());
                        if (FAILED_TEXTURES.add(failed)) {
                            GeometryNode.LOGGER.warn("Skipping compatibility draw whose base-color asset is invalid: {}",
                                    loaded.asset().cacheIdentity(), exception);
                        }
                    } else {
                        runtimeFaults.add("texture-projection:" + exception.getClass().getSimpleName());
                        losses.add(ModelCompatibilityLoss.PROJECTOR_RUNTIME_UNAVAILABLE);
                        if (LOGGED_RUNTIME_TEXTURE_FAILURES.add(failed)) {
                            GeometryNode.LOGGER.warn("Compatibility texture runtime projection failed; this generation may retry: {}",
                                    loaded.asset().cacheIdentity(), exception);
                        }
                    }
                    reject(rejections, ModelDrawRejection.TEXTURE_PROJECTION_FAILED);
                    continue;
                }
                mergeTextureLosses(losses, texture.losses());
                if (labPbr) IrisLabPbrProjector.reportHolderState(texture.texture(), losses);
                RenderType renderType = renderType(material, placement, texture.identifier(), opaqueFallback);
                Matrix4f transform = new Matrix4f().translate(
                        (float) (placement.position().x - camera.x), (float) (placement.position().y - camera.y),
                        (float) (placement.position().z - camera.z)).rotate(placement.rotation()).scale(placement.scale())
                        .mul(instance.pose().worldMatrix(nodeIndex));
                float determinant = transform.determinant3x3();
                if (!Float.isFinite(determinant) || Math.abs(determinant) <= 1.0E-8F) {
                    reject(rejections, ModelDrawRejection.SINGULAR_TRANSFORM);
                    statistics.singularTransformSkips++;
                    continue;
                }
                boolean mirrored = determinant < 0;
                root.pushPose();
                try {
                    root.mulPose(transform);
                    Vector3f localCenter = HostGeometryProjector.boundsCenter(primitive.bounds());
                    Vector3f drawPosition = transform.transformPosition(localCenter);
                    Vector3d worldPosition = new Vector3d(camera.x + drawPosition.x,
                            camera.y + drawPosition.y, camera.z + drawPosition.z);
                    int light = placement.fullBright() ? FULL_BRIGHT : LevelRenderer.getLightCoords(
                            Minecraft.getInstance().level, BlockPos.containing(worldPosition.x, worldPosition.y, worldPosition.z));
                    float red = material.red() * placement.red(), green = material.green() * placement.green();
                    float blue = material.blue() * placement.blue();
                    float alpha = opaqueFallback ? 1
                            : (material.alphaMode() == ModelAlphaMode.OPAQUE ? 1 : material.alpha()) * placement.alpha();
                    collector.submitCustomGeometry(root, renderType,
                            (pose, vertices) -> geometry.emit(pose, vertices, red, green, blue, alpha, light, mirrored));
                    statistics.drawCalls++;
                    statistics.triangles += geometry.triangleCount();
                } finally {
                    root.popPose();
                }
            }
        }
    }

    private static RenderType renderType(StaticModelMaterial material, ModelInstancePlacement placement,
                                         Identifier texture, boolean opaqueFallback) {
        boolean doubleSided = material.doubleSided() || placement.forceDoubleSided();
        if (opaqueFallback) return RenderTypes.entityCutout(texture, false);
        if (material.alphaMode() == ModelAlphaMode.BLEND || placement.alpha() < 0.999F) {
            return HostEntityRenderTypes.translucent(texture);
        }
        if (material.alphaMode() == ModelAlphaMode.MASK || doubleSided) {
            return RenderTypes.entityCutout(texture, false);
        }
        return RenderTypes.entityCutoutCull(texture);
    }

    private static CompatibilityTexture texture(LoadedModelResource loaded, StaticModelMaterial material,
                                                boolean labPbr, boolean opaqueFallback) {
        TextureKey key = textureKey(loaded, material, labPbr, opaqueFallback);
        if (FAILED_TEXTURES.contains(key)) {
            throw TextureProjectionFailure.asset("compatibility base-color asset previously failed", null);
        }
        return TEXTURES.computeIfAbsent(key, ignored -> {
            Identifier id = Identifier.fromNamespaceAndPath(GeometryNode.MODID,
                    "model_compat/texture_" + nextTextureId++);
            IrisLabPbrProjector.LabPbrAlbedoTexture dynamic = null;
            boolean registered = false;
            List<NativeImage> inputs = new ArrayList<>();
            try {
                NativeImage image;
                try {
                    image = decode(loaded, material.baseColorTexture(), inputs);
                } catch (IOException | RuntimeException assetFailure) {
                    throw TextureProjectionFailure.asset("failed to decode compatibility base-color asset", assetFailure);
                }
                if (image == null) {
                    image = new NativeImage(1, 1, false);
                    image.setPixel(0, 0, 0xFFFFFFFF);
                    inputs.add(image);
                }
                if (key.alphaMode() == ModelAlphaMode.OPAQUE || key.opaqueFallback()) {
                    for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
                        image.setPixel(x, y, image.getPixel(x, y) | 0xFF000000);
                    }
                }
                NativeImage normal = null, specular = null;
                EnumSet<ModelCompatibilityLoss> textureLosses = EnumSet.noneOf(ModelCompatibilityLoss.class);
                if (key.labPbr()) {
                    NativeImage mr = auxiliaryInput(loaded, material.metallicRoughnessTexture(), material, inputs,
                            textureLosses, ModelCompatibilityLoss.METALLIC_UNREPRESENTABLE,
                            ModelCompatibilityLoss.ROUGHNESS_UNREPRESENTABLE);
                    NativeImage normalInput = auxiliaryInput(loaded, material.normalTexture(), material, inputs,
                            textureLosses, ModelCompatibilityLoss.NORMAL_TEXTURE_UNREPRESENTABLE);
                    NativeImage ao = auxiliaryInput(loaded, material.occlusionTexture(), material, inputs,
                            textureLosses, ModelCompatibilityLoss.OCCLUSION_TEXTURE_UNREPRESENTABLE);
                    try {
                        int specWidth = dimension(mr, null), specHeight = dimensionHeight(mr, null);
                        specular = LabPbrProjectionEncoder.buildSpecular(mr, specWidth, specHeight,
                                material.metallicFactor(), material.roughnessFactor());
                        inputs.add(specular);
                        if (!LabPbrProjectionEncoder.metallicEndpointsOnly(mr, material.metallicFactor())) {
                            textureLosses.add(ModelCompatibilityLoss.METALLIC_UNREPRESENTABLE);
                        }
                        int normalWidth = dimension(normalInput, ao), normalHeight = dimensionHeight(normalInput, ao);
                        normal = LabPbrProjectionEncoder.buildNormal(normalInput, ao, normalWidth, normalHeight,
                                material.normalScale(), material.occlusionStrength());
                        if (normal != null) inputs.add(normal);
                    } catch (RuntimeException auxiliaryFailure) {
                        textureLosses.add(ModelCompatibilityLoss.PROJECTOR_RUNTIME_UNAVAILABLE);
                        textureLosses.add(ModelCompatibilityLoss.METALLIC_UNREPRESENTABLE);
                        if (material.roughnessFactor() != 1 || material.metallicRoughnessTexture().present()) {
                            textureLosses.add(ModelCompatibilityLoss.ROUGHNESS_UNREPRESENTABLE);
                        }
                        if (material.normalTexture().present()) {
                            textureLosses.add(ModelCompatibilityLoss.NORMAL_TEXTURE_UNREPRESENTABLE);
                        }
                        if (material.occlusionTexture().present()) {
                            textureLosses.add(ModelCompatibilityLoss.OCCLUSION_TEXTURE_UNREPRESENTABLE);
                        }
                        normal = null;
                        specular = null;
                    }
                }
                // The caller retains failure cleanup until the composite constructor succeeds.
                // NativeImage.close is idempotent, so partial DynamicTexture construction remains safe.
                dynamic = new IrisLabPbrProjector.LabPbrAlbedoTexture(image, normal, specular,
                        material.baseColorTexture().sampler());
                inputs.remove(image);
                inputs.remove(normal);
                inputs.remove(specular);
                Minecraft.getInstance().getTextureManager().register(id, dynamic);
                registered = true;
                if (key.labPbr()) IrisLabPbrProjector.afterAlbedoRegistration(dynamic);
                return new CompatibilityTexture(id, dynamic, textureLosses);
            } catch (TextureProjectionFailure exception) {
                if (registered) Minecraft.getInstance().getTextureManager().release(id);
                else if (dynamic != null) dynamic.close();
                throw exception;
            } catch (RuntimeException exception) {
                if (registered) Minecraft.getInstance().getTextureManager().release(id);
                else if (dynamic != null) dynamic.close();
                throw TextureProjectionFailure.runtime("compatibility texture host operation failed", exception);
            } finally {
                inputs.forEach(NativeImage::close);
            }
        });
    }

    private static TextureKey textureKey(LoadedModelResource loaded, StaticModelMaterial material, boolean labPbr,
                                         boolean opaqueFallback) {
        return new TextureKey(loaded.asset().cacheIdentity(), material, labPbr, opaqueFallback);
    }

    private static NativeImage decode(LoadedModelResource loaded, StaticModelTexture texture, List<NativeImage> owned)
            throws IOException {
        if (!texture.present()) return null;
        NativeImage image = NativeImage.read(loaded.definition().images().get(texture.imageIndex()).encodedData());
        owned.add(image);
        return image;
    }

    private static NativeImage compatibleInput(LoadedModelResource loaded, StaticModelTexture texture,
                                                StaticModelMaterial material, List<NativeImage> owned) throws IOException {
        if (!texture.present()) return null;
        StaticModelTexture coordinates = coordinateSource(material);
        if (texture.texCoord() != coordinates.texCoord()
                || !texture.transform().equals(coordinates.transform())
                || !texture.sampler().equals(coordinates.sampler())) return null;
        return decode(loaded, texture, owned);
    }

    private static NativeImage auxiliaryInput(LoadedModelResource loaded, StaticModelTexture texture,
                                              StaticModelMaterial material, List<NativeImage> owned,
                                              EnumSet<ModelCompatibilityLoss> losses,
                                              ModelCompatibilityLoss... roleLosses) {
        try {
            return compatibleInput(loaded, texture, material, owned);
        } catch (IOException | RuntimeException exception) {
            losses.add(ModelCompatibilityLoss.TEXTURE_DECODE_FAILED);
            losses.addAll(Arrays.asList(roleLosses));
            return null;
        }
    }

    private static void mergeTextureLosses(EnumSet<ModelCompatibilityLoss> target,
                                           Set<ModelCompatibilityLoss> textureLosses) {
        if (textureLosses.contains(ModelCompatibilityLoss.ROUGHNESS_UNREPRESENTABLE)) {
            target.remove(ModelCompatibilityLoss.ROUGHNESS_APPROXIMATED);
        }
        if (textureLosses.contains(ModelCompatibilityLoss.NORMAL_TEXTURE_UNREPRESENTABLE)) {
            target.remove(ModelCompatibilityLoss.NORMAL_TEXTURE_APPROXIMATED);
        }
        if (textureLosses.contains(ModelCompatibilityLoss.OCCLUSION_TEXTURE_UNREPRESENTABLE)) {
            target.remove(ModelCompatibilityLoss.OCCLUSION_TEXTURE_APPROXIMATED);
        }
        target.addAll(textureLosses);
    }

    private static StaticModelTexture coordinateSource(StaticModelMaterial material) {
        StaticModelTexture[] textures = {material.baseColorTexture(), material.metallicRoughnessTexture(),
                material.normalTexture(), material.occlusionTexture(), material.emissiveTexture()};
        for (StaticModelTexture texture : textures) if (texture.present()) return texture;
        return StaticModelTexture.absent();
    }

    private static int dimension(NativeImage first, NativeImage second) {
        return first != null ? first.getWidth() : second != null ? second.getWidth() : 1;
    }

    private static void reject(EnumMap<ModelDrawRejection, Integer> rejections, ModelDrawRejection reason) {
        rejections.merge(reason, 1, Integer::sum);
    }
    private static int dimensionHeight(NativeImage first, NativeImage second) {
        return first != null ? first.getHeight() : second != null ? second.getHeight() : 1;
    }

    private record GeometryKey(String asset, int mesh, int primitive, int uvSet, ModelTextureTransform transform) { }
    private record TextureKey(String asset, StaticModelMaterial material, boolean labPbr, boolean opaqueFallback) {
        ModelAlphaMode alphaMode() { return material.alphaMode(); }
    }
    private static void release(net.minecraft.client.renderer.texture.TextureManager manager,
                                CompatibilityTexture texture) {
        IrisLabPbrProjector.beforeAlbedoRelease(texture.texture());
        manager.release(texture.identifier());
    }

    private record CompatibilityTexture(Identifier identifier,
                                        IrisLabPbrProjector.LabPbrAlbedoTexture texture,
                                        Set<ModelCompatibilityLoss> losses) {
        private CompatibilityTexture {
            losses = Set.copyOf(losses);
        }
    }
    private static final class FrameStatistics {
        private int drawCalls;
        private long triangles;
        private int singularTransformSkips;
    }

}
