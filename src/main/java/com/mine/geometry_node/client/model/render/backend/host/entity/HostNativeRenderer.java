package com.mine.geometry_node.client.model.render.backend.host.entity;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.client.model.gpu.DecodedModelImage;
import com.mine.geometry_node.client.model.gpu.minecraft.NativeImageModelDecoder;
import com.mine.geometry_node.client.model.render.backend.host.material.*;
import com.mine.geometry_node.client.model.render.backend.host.geometry.HostEntityGeometry;
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
import net.minecraft.client.renderer.culling.Frustum;
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
    private static final NativeImageModelDecoder IMAGE_DECODER = new NativeImageModelDecoder();
    private static final Object VERTEX_BUDGET_DIAGNOSTIC = new Object();
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
            lastLosses = Set.of();
            ModelIntegrationController.reportCompatibility(Set.of(), ModelIntegrationVerification.NOT_APPLICABLE,
                    List.of(), Map.of());
            runtime.recordFrame(0, 0, 0, System.nanoTime() - started, -1, 0, 0, 0);
            return;
        }
        IrisLabPbrProjector.Snapshot projectorSnapshot = IrisLabPbrProjector.snapshot(
                ModelResourceReloadListener.reloadGeneration());
        ModelProjectorCapability projector = projectorSnapshot.capability();
        NativeRenderParameters parameters = NativeRenderParameters.current();
        IrisEntityTranslucency.Snapshot translucency = parameters.transparencyPolicy()
                == NativeTransparencyPolicy.AUTO
                ? IrisEntityTranslucency.snapshot()
                : new IrisEntityTranslucency.Snapshot(false, "POLICY_" + parameters.transparencyPolicy());
        boolean preserveBlend = parameters.preservesBlend(translucency.dedicatedProgram());
        synchronizeCapability(projector);
        net.minecraft.client.Camera mainCamera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 camera = mainCamera.position();
        Frustum frustum = mainCamera.getCullFrustum();
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
        HostVertexBudget vertexBudget = new HostVertexBudget();
        List<TranslucentSubmission> translucentSubmissions = new ArrayList<>();
        for (ClientModelInstanceRegistry.ReadyInstance instance : ready) {
            if (!instance.state().visible() || !dimension.equals(instance.state().dimension())) continue;
            net.minecraft.world.phys.AABB bounds = ModelRenderBounds.worldBounds(
                    instance.pose().modelBounds(), instance.state().placement());
            if (instance.state().maxDistance() > 0 && bounds.distanceToSqr(camera)
                    > instance.state().maxDistance() * instance.state().maxDistance()) continue;
            boolean deforms = !instance.resource().definition().skins().isEmpty();
            if (!deforms && frustum != null && !frustum.isVisible(bounds)) continue;
            Optional<HostPreparedArtifact> prepared = instance.resource().existingBackendArtifact(
                    HostArtifactRepository.KEY);
            long requiredVertices = prepared.map(artifact -> artifact.drawPlan().requiredVertices())
                    .orElseGet(() -> HostDrawPlan.requiredVertices(instance.resource().definition(),
                            instance.resource().metadata()));
            if (prepared.isEmpty() && !vertexBudget.withinHardLimit(requiredVertices)) {
                reject(frameRejections, ModelDrawRejection.HOST_VERTEX_BUDGET_EXCEEDED);
                String asset = instance.resource().asset().cacheIdentity();
                if (instance.resource().reportDiagnosticOnce(VERTEX_BUDGET_DIAGNOSTIC)) {
                    GeometryNode.LOGGER.warn("Skipping HOST_NATIVE instance {}: requires {} emitted vertices; "
                                    + "per-frame limit is {}", asset, requiredVertices,
                            HostVertexBudget.MAX_VERTICES_PER_FRAME);
                }
                continue;
            }
            HostPreparedArtifact artifact = prepared.orElseGet(() -> instance.resource().backendArtifact(
                    HostArtifactRepository.KEY,
                    () -> HostArtifactRepository.INSTANCE.acquire(instance.resource().definition(),
                            instance.resource().metadata())).orElse(null));
            if (artifact == null) {
                instance.resource().backendArtifactFailureForReport(HostArtifactRepository.KEY).ifPresent(failure ->
                        GeometryNode.LOGGER.warn("HOST artifact preparation failed for {}",
                                instance.resource().asset().cacheIdentity(), failure));
                runtimeFaults.add("host-artifact-preparation-failed");
                continue;
            }
            submitInstance(root, collector, camera, instance, artifact, frameLosses, frameRejections,
                    runtimeFaults, statistics, projector, preserveBlend,
                    translucentSubmissions, frustum, vertexBudget);
        }
        submitTranslucent(root, collector, translucentSubmissions);
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
                System.nanoTime() - started, -1, statistics.candidateDraws,
                statistics.culledDraws, statistics.submittedVertices);
    }

    public static Set<ModelCompatibilityLoss> lastLosses() { return lastLosses; }

    public static void clear() {
        HostArtifactRepository.INSTANCE.invalidateBindings();
        lastProjectorCapability = null; lastLosses = Set.of();
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
        HostArtifactRepository.INSTANCE.invalidateBindings();
        lastProjectorCapability = capability;
    }

    private static void submitInstance(PoseStack root, SubmitNodeCollector collector, Vec3 camera,
                                       ClientModelInstanceRegistry.ReadyInstance instance,
                                       HostPreparedArtifact artifact,
                                       EnumSet<ModelCompatibilityLoss> losses,
                                       EnumMap<ModelDrawRejection, Integer> rejections,
                                       List<String> runtimeFaults, FrameStatistics statistics,
                                       ModelProjectorCapability projector, boolean preserveBlend,
                                       List<TranslucentSubmission> translucentSubmissions,
                                       Frustum frustum, HostVertexBudget vertexBudget) {
        LoadedModelResource loaded = instance.resource();
        ModelInstancePlacement placement = instance.state().placement();
        List<VisibleDraw> visibleDraws = visibleDraws(instance, artifact.drawPlan(), placement, frustum, statistics);
        if (visibleDraws.isEmpty()) return;
        long visibleVertices = 0;
        for (VisibleDraw visible : visibleDraws) {
            long triangles = visible.draw().triangleCount();
            visibleVertices = triangles > (Long.MAX_VALUE - visibleVertices) / 4
                    ? Long.MAX_VALUE : visibleVertices + triangles * 4;
        }
        if (!vertexBudget.reserve(visibleVertices)) {
            reject(rejections, ModelDrawRejection.HOST_VERTEX_BUDGET_EXCEEDED);
            if (loaded.reportDiagnosticOnce(VERTEX_BUDGET_DIAGNOSTIC)) {
                GeometryNode.LOGGER.warn("Skipping HOST_NATIVE instance {}: visible draws require {} emitted "
                                + "vertices; remaining per-frame budget is insufficient",
                        loaded.asset().cacheIdentity(), visibleVertices);
            }
            return;
        }
        statistics.submittedVertices += visibleVertices;
        for (VisibleDraw visible : visibleDraws) {
                HostDrawPlan.Draw draw = visible.draw();
                int nodeIndex = draw.nodeIndex();
                int primitiveIndex = draw.primitiveIndex();
                if (!instance.state().nodeState().visible(nodeIndex)) continue;
                StaticModelMaterial material = draw.material();
                boolean labPbr = projector.auxiliaryEnabled();
                HostMaterialProjection projection = draw.projection(projector.profile());
                losses.addAll(projection.losses());
                if (material.alphaMode() == ModelAlphaMode.BLEND || placement.alpha() < 0.999F) {
                    losses.add(ModelCompatibilityLoss.BLEND_SHADOW_APPROXIMATED);
                }
                HostMaterialAnalyzer.addInstanceLosses(material, placement.alpha(),
                        placement.forceDoubleSided(), losses);
                boolean effectiveTranslucent = material.alphaMode() == ModelAlphaMode.BLEND
                        || placement.alpha() < 0.999F;
                boolean opaqueFallback = effectiveTranslucent && !preserveBlend;
                if (opaqueFallback) losses.add(ModelCompatibilityLoss.ENTITY_TRANSLUCENCY_FALLBACK_OPAQUE);
                if (!projection.selectable()) {
                    reject(rejections, ModelDrawRejection.UNSUPPORTED_SKINNING);
                    continue;
                }
                HostEntityGeometry geometry = draw.geometry();
                if (geometry == null) {
                    reject(rejections, ModelDrawRejection.GEOMETRY_PROJECTION_FAILED);
                    runtimeFaults.add("geometry-projection:" + draw.geometryFailure());
                    String failureKey = draw.meshIndex() + ":" + primitiveIndex;
                    if (artifact.loggedGeometryFailures.add(failureKey)) {
                        GeometryNode.LOGGER.warn("Skipping compatibility draw whose geometry could not be projected: {} {}",
                                loaded.asset().cacheIdentity(), draw.geometryFailure());
                    }
                    continue;
                }
                HostPreparedArtifact.CompatibilityTexture texture;
                try {
                    texture = texture(artifact, loaded, material, labPbr, opaqueFallback);
                } catch (TextureProjectionFailure exception) {
                    HostPreparedArtifact.TextureKey failed = textureKey(material, labPbr, opaqueFallback);
                    if (exception.cacheForAssetLifetime()) {
                        runtimeFaults.add("texture-decode:" + exception.getClass().getSimpleName());
                        if (artifact.failedTextures.add(failed)) {
                            GeometryNode.LOGGER.warn("Skipping compatibility draw whose base-color asset is invalid: {}",
                                    loaded.asset().cacheIdentity(), exception);
                        }
                    } else {
                        runtimeFaults.add("texture-projection:" + exception.getClass().getSimpleName());
                        losses.add(ModelCompatibilityLoss.PROJECTOR_RUNTIME_UNAVAILABLE);
                        if (artifact.loggedRuntimeTextureFailures.add(failed)) {
                            GeometryNode.LOGGER.warn("Compatibility texture runtime projection failed; this generation may retry: {}",
                                    loaded.asset().cacheIdentity(), exception);
                        }
                    }
                    reject(rejections, ModelDrawRejection.TEXTURE_PROJECTION_FAILED);
                    continue;
                }
                mergeTextureLosses(losses, texture.losses());
                boolean materialFallback = texture.defaultMaterialFallback();
                if (labPbr && !materialFallback) IrisLabPbrProjector.reportHolderState(texture.texture(), losses);
                RenderType renderType = renderType(material, placement, texture.identifier(),
                        opaqueFallback || materialFallback);
                Matrix4f transform = new Matrix4f().translate(
                        (float) (placement.position().x - camera.x), (float) (placement.position().y - camera.y),
                        (float) (placement.position().z - camera.z)).rotate(placement.rotation()).scale(placement.scale())
                        .mul(visible.nodeWorld());
                float determinant = transform.determinant3x3();
                if (!Float.isFinite(determinant) || Math.abs(determinant) <= 1.0E-8F) {
                    reject(rejections, ModelDrawRejection.SINGULAR_TRANSFORM);
                    statistics.singularTransformSkips++;
                    continue;
                }
                boolean mirrored = determinant < 0;
                Vector3f drawPosition = transform.transformPosition(draw.localCenter());
                Vector3d worldPosition = new Vector3d(camera.x + drawPosition.x,
                        camera.y + drawPosition.y, camera.z + drawPosition.z);
                int light = placement.fullBright() ? FULL_BRIGHT : LevelRenderer.getLightCoords(
                        Minecraft.getInstance().level, BlockPos.containing(worldPosition.x, worldPosition.y, worldPosition.z));
                float red = materialFallback ? 1 : material.red() * placement.red();
                float green = materialFallback ? 1 : material.green() * placement.green();
                float blue = materialFallback ? 1 : material.blue() * placement.blue();
                float alpha = opaqueFallback || materialFallback ? 1
                        : (material.alphaMode() == ModelAlphaMode.OPAQUE ? 1 : material.alpha()) * placement.alpha();
                if (effectiveTranslucent && !opaqueFallback && !materialFallback) {
                    translucentSubmissions.add(new TranslucentSubmission(
                            new HostTransparentOrderKey(drawPosition.lengthSquared(), loaded.asset().cacheIdentity(),
                                    nodeIndex, primitiveIndex, instance.id().value()),
                            new Matrix4f(transform), renderType, geometry,
                            red, green, blue, alpha, light, mirrored));
                } else {
                    submitGeometry(root, collector, transform, renderType, geometry,
                            red, green, blue, alpha, light, mirrored);
                }
                statistics.drawCalls++;
                statistics.triangles += draw.triangleCount();
        }
    }

    private static List<VisibleDraw> visibleDraws(ClientModelInstanceRegistry.ReadyInstance instance,
                                                  HostDrawPlan plan, ModelInstancePlacement placement,
                                                  Frustum frustum, FrameStatistics statistics) {
        List<VisibleDraw> result = new ArrayList<>();
        int currentNode = -1;
        boolean nodeVisible = false;
        boolean skinned = false;
        Matrix4f nodeWorld = null;
        for (HostDrawPlan.Draw draw : plan.draws()) {
            statistics.candidateDraws++;
            if (draw.nodeIndex() != currentNode) {
                currentNode = draw.nodeIndex();
                nodeVisible = instance.state().nodeState().visible(currentNode);
                skinned = instance.resource().definition().nodes().get(currentNode).skinIndex() >= 0;
                nodeWorld = nodeVisible ? instance.pose().worldMatrix(currentNode) : null;
                if (nodeVisible && !skinned && frustum != null) {
                    ModelBounds nodeBounds = instance.pose().nodeWorldBounds(currentNode);
                    nodeVisible = nodeBounds == null
                            || frustum.isVisible(ModelRenderBounds.worldBounds(nodeBounds, placement));
                }
            }
            if (!nodeVisible) {
                statistics.culledDraws++;
                continue;
            }
            if (!skinned && frustum != null
                    && !frustum.isVisible(ModelRenderBounds.worldBounds(draw.localBounds(), nodeWorld, placement))) {
                statistics.culledDraws++;
                continue;
            }
            result.add(new VisibleDraw(draw, nodeWorld));
        }
        return result;
    }

    private static void submitTranslucent(PoseStack root, SubmitNodeCollector collector,
                                          List<TranslucentSubmission> submissions) {
        submissions.sort(TranslucentSubmission.ORDER);
        for (int index = 0; index < submissions.size(); index++) {
            TranslucentSubmission submission = submissions.get(index);
            submitGeometry(root, collector.order(index + 1), submission.transform(), submission.renderType(),
                    submission.geometry(), submission.red(), submission.green(), submission.blue(), submission.alpha(),
                    submission.light(), submission.mirrored());
        }
    }

    private static void submitGeometry(PoseStack root,
                                       net.minecraft.client.renderer.OrderedSubmitNodeCollector collector,
                                       Matrix4f transform, RenderType renderType, HostEntityGeometry geometry,
                                       float red, float green, float blue, float alpha, int light, boolean mirrored) {
        root.pushPose();
        try {
            root.mulPose(transform);
            collector.submitCustomGeometry(root, renderType,
                    (pose, vertices) -> geometry.emit(pose, vertices, red, green, blue, alpha, light, mirrored));
        } finally {
            root.popPose();
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

    private static HostPreparedArtifact.CompatibilityTexture texture(HostPreparedArtifact artifact,
                                                LoadedModelResource loaded, StaticModelMaterial material,
                                                boolean labPbr, boolean opaqueFallback) {
        HostPreparedArtifact.TextureKey key = textureKey(material, labPbr, opaqueFallback);
        if (artifact.failedTextures.contains(key)) {
            throw TextureProjectionFailure.asset("compatibility base-color asset previously failed", null);
        }
        return artifact.textures.computeIfAbsent(key, ignored -> {
            Identifier id = Identifier.fromNamespaceAndPath(GeometryNode.MODID,
                    "model_compat/texture_" + nextTextureId++);
            IrisLabPbrProjector.LabPbrAlbedoTexture dynamic = null;
            boolean registered = false;
            List<NativeImage> inputs = new ArrayList<>();
            try {
                EnumSet<ModelCompatibilityLoss> textureLosses = EnumSet.noneOf(ModelCompatibilityLoss.class);
                boolean defaultMaterialFallback = false;
                NativeImage image;
                try {
                    image = decode(loaded, material.baseColorTexture(), inputs);
                } catch (IOException | RuntimeException assetFailure) {
                    image = null;
                    defaultMaterialFallback = true;
                    textureLosses.add(ModelCompatibilityLoss.MATERIAL_FALLBACK_DEFAULT);
                    GeometryNode.LOGGER.warn("Compatibility material fell back to DEFAULT_MATERIAL for {}: {}",
                            loaded.asset().cacheIdentity(), assetFailure.getMessage());
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
                if (key.labPbr() && !defaultMaterialFallback) {
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
                if (key.labPbr() && !defaultMaterialFallback) IrisLabPbrProjector.afterAlbedoRegistration(dynamic);
                return new HostPreparedArtifact.CompatibilityTexture(id, dynamic, textureLosses,
                        defaultMaterialFallback);
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

    private static HostPreparedArtifact.TextureKey textureKey(StaticModelMaterial material, boolean labPbr,
                                                              boolean opaqueFallback) {
        return new HostPreparedArtifact.TextureKey(material, labPbr, opaqueFallback);
    }

    private static NativeImage decode(LoadedModelResource loaded, StaticModelTexture texture, List<NativeImage> owned)
            throws IOException {
        if (!texture.present()) return null;
        ModelImageSource source = loaded.definition().images().get(texture.imageIndex());
        DecodedModelImage decoded = IMAGE_DECODER.decode(source);
        NativeImage image = new NativeImage(decoded.width(), decoded.height(), false);
        byte[] rgba = decoded.rgba();
        for (int y = 0; y < decoded.height(); y++) for (int x = 0; x < decoded.width(); x++) {
            int offset = (y * decoded.width() + x) * 4;
            int argb = (rgba[offset + 3] & 0xFF) << 24
                    | (rgba[offset] & 0xFF) << 16
                    | (rgba[offset + 1] & 0xFF) << 8
                    | rgba[offset + 2] & 0xFF;
            image.setPixel(x, y, argb);
        }
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

    private record TranslucentSubmission(HostTransparentOrderKey orderKey,
                                         Matrix4f transform, RenderType renderType,
                                         HostEntityGeometry geometry, float red, float green, float blue, float alpha,
                                         int light, boolean mirrored) {
        private static final Comparator<TranslucentSubmission> ORDER =
                Comparator.comparing(TranslucentSubmission::orderKey);
    }
    private record VisibleDraw(HostDrawPlan.Draw draw, Matrix4f nodeWorld) {}
    private static final class FrameStatistics {
        private int drawCalls;
        private long triangles;
        private int singularTransformSkips;
        private int candidateDraws;
        private int culledDraws;
        private long submittedVertices;
    }

}
