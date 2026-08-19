package com.mine.geometry_node.client.model.render.backend.host.entity;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.client.model.gpu.DecodedModelImage;
import com.mine.geometry_node.client.model.render.backend.host.material.*;
import com.mine.geometry_node.client.model.render.backend.host.geometry.HostClusterVisibility;
import com.mine.geometry_node.client.model.render.backend.host.geometry.HostEntityGeometry;
import com.mine.geometry_node.client.model.render.backend.host.iris.labpbr.IrisLabPbrProjector;
import com.mine.geometry_node.client.model.render.backend.host.iris.labpbr.ModelProjectorCapability;
import com.mine.geometry_node.client.model.render.backend.host.iris.entity.IrisEntityTranslucency;
import com.mine.geometry_node.client.model.render.backend.host.iris.shadow.IrisShadowAdapter;
import com.mine.geometry_node.client.model.render.backend.host.light.integration.HostLightingEnvironment;
import com.mine.geometry_node.client.model.render.backend.host.light.integration.HostLightingEnvironmentSnapshot;
import com.mine.geometry_node.client.model.render.backend.host.lod.HostModelLodPlan;
import com.mine.geometry_node.client.model.render.backend.host.light.contract.HostLightBinding;
import com.mine.geometry_node.client.model.render.backend.host.light.contract.HostScalarLightField;
import com.mine.geometry_node.client.model.render.backend.host.light.integration.HostLightingDomain;
import com.mine.geometry_node.client.model.render.backend.host.light.integration.HostLightingOwner;
import com.mine.geometry_node.client.model.render.backend.host.light.integration.HostLightingPolicy;
import com.mine.geometry_node.client.model.render.backend.host.lod.HostModelLodSelector;
import com.mine.geometry_node.client.model.render.backend.common.ModelRenderBounds;
import com.mine.geometry_node.client.model.render.integration.*;
import com.mine.geometry_node.client.model.runtime.*;
import com.mine.geometry_node.client.model.debug.ModelLoadProgressTracker;
import com.mine.geometry_node.core.engine.system.model.domain.*;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix3f;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.io.IOException;
import java.util.*;

/** Minimum-loss projection into Minecraft's standard entity pipeline. */
public final class HostNativeRenderer {
    private static final int FULL_BRIGHT = 15728880;
    private static final Object VERTEX_BUDGET_DIAGNOSTIC = new Object();
    private static final Map<ModelInstanceId, Integer> IMMEDIATE_DRAW_CURSORS = new HashMap<>();
    private static final Map<ModelInstanceId, Integer> MODEL_LOD_LEVELS = new HashMap<>();
    private static int nextInstanceStart;
    private static long nextTextureId;
    private static ModelProjectorCapability lastProjectorCapability;
    private static Set<ModelCompatibilityLoss> lastLosses = Set.of();

    private HostNativeRenderer() { }

    public static void submit(PoseStack root, SubmitNodeCollector collector) {
        if (Minecraft.getInstance().level == null) return;
        HostStaticEntityRenderer.beginFrame();
        long started = System.nanoTime();
        ClientModelRuntime runtime = ClientModelRuntime.INSTANCE;
        List<ClientModelInstanceRegistry.ReadyInstance> ready = runtime.instances().readySnapshot();
        if (ready.isEmpty()) {
            IMMEDIATE_DRAW_CURSORS.clear();
            MODEL_LOD_LEVELS.clear();
            nextInstanceStart = 0;
            lastLosses = Set.of();
            ModelIntegrationController.reportCompatibility(Set.of(), ModelIntegrationVerification.NOT_APPLICABLE,
                    List.of(), Map.of());
            runtime.recordFrame(0, 0, 0, System.nanoTime() - started, -1, 0, 0, 0);
            return;
        }
        HostLightingEnvironmentSnapshot lightingEnvironment = HostLightingEnvironment.snapshot();
        IrisLabPbrProjector.Snapshot projectorSnapshot = lightingEnvironment.projector();
        ModelProjectorCapability projector = projectorSnapshot.capability();
        NativeRenderParameters parameters = NativeRenderParameters.current();
        IrisEntityTranslucency.Snapshot translucency = parameters.transparencyPolicy()
                == NativeTransparencyPolicy.AUTO
                ? lightingEnvironment.translucency()
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
        String shadowFailure = lightingEnvironment.shadow().failure();
        if (!shadowFailure.isEmpty() && !"IRIS_ABSENT".equals(shadowFailure)
                && !"LIGHTING_ENVIRONMENT_INVALIDATED".equals(shadowFailure)) {
            runtimeFaults.add("shadow-adapter:" + shadowFailure);
        }
        if (translucency.diagnostic().startsWith("IRIS_TRANSLUCENCY_PROBE_FAILED:")) {
            runtimeFaults.add("entity-translucency:" + translucency.diagnostic());
        }
        FrameStatistics statistics = new FrameStatistics();
        HostVertexBudget vertexBudget = new HostVertexBudget();
        List<TranslucentSubmission> translucentSubmissions = new ArrayList<>();
        Set<ModelInstanceId> liveInstanceIds = new HashSet<>();
        Set<ModelInstanceId> initialAdmissionCandidates = new HashSet<>();
        int instanceStart = Math.floorMod(nextInstanceStart, ready.size());
        nextInstanceStart = (instanceStart + 1) % ready.size();
        for (int instanceOffset = 0; instanceOffset < ready.size(); instanceOffset++) {
            ClientModelInstanceRegistry.ReadyInstance instance = ready.get(
                    (instanceStart + instanceOffset) % ready.size());
            liveInstanceIds.add(instance.id());
            if (!instance.state().visible() || !dimension.equals(instance.state().dimension())) continue;
            net.minecraft.world.phys.AABB bounds = ModelRenderBounds.worldBounds(
                    instance.pose().modelBounds(), instance.state().placement());
            if (instance.state().maxDistance() > 0 && bounds.distanceToSqr(camera)
                    > instance.state().maxDistance() * instance.state().maxDistance()) continue;
            boolean deforms = !instance.resource().definition().skins().isEmpty();
            if (!deforms && frustum != null && !frustum.isVisible(bounds)) continue;
            Optional<HostPreparedArtifact> prepared = instance.resource().existingBackendArtifact(
                    HostArtifactRepository.KEY);
            String progressKey = instance.resource().asset().normalizedPath();
            if (prepared.isPresent()) ModelLoadProgressTracker.finish(progressKey);
            HostPreparedArtifact artifact = prepared.orElseGet(() -> instance.resource().backendArtifactAsync(
                    HostArtifactRepository.KEY, () -> {
                        ModelLoadProgressTracker.begin(progressKey);
                        ModelLoadProgressTracker.update(progressKey, "Preparing HOST", 0.70);
                        var future = HostArtifactRepository.INSTANCE.acquireAsync(instance.resource().definition(),
                                instance.resource().metadata(), runtime.modelWorkers(), fraction ->
                                        ModelLoadProgressTracker.update(progressKey, "Preparing HOST",
                                                0.70 + fraction * 0.28));
                        future.whenComplete((lease, failure) -> ModelLoadProgressTracker.finish(progressKey));
                        return future;
                    }).orElse(null));
            if (artifact == null) {
                Optional<RuntimeException> preparationFailure = instance.resource()
                        .backendArtifactFailureForReport(HostArtifactRepository.KEY);
                preparationFailure.ifPresent(failure -> GeometryNode.LOGGER.warn(
                        "HOST artifact preparation failed for {}",
                        instance.resource().asset().cacheIdentity(), failure));
                if (preparationFailure.isPresent()) {
                    ModelLoadProgressTracker.finish(progressKey);
                    runtimeFaults.add("host-artifact-preparation-failed");
                }
                continue;
            }
            HostArtifactRepository.INSTANCE.touchStatic(artifact, instance.id(), System.nanoTime());
            double extentX = bounds.maxX - bounds.minX;
            double extentY = bounds.maxY - bounds.minY;
            double extentZ = bounds.maxZ - bounds.minZ;
            double worldExtent = Math.sqrt(extentX * extentX + extentY * extentY + extentZ * extentZ);
            int requestedLod = HostModelLodSelector.select(
                    bounds.minX, bounds.minY, bounds.minZ, bounds.maxX, bounds.maxY, bounds.maxZ,
                    camera.x, camera.y, camera.z, mainCamera.getFov(),
                    Minecraft.getInstance().getWindow().getHeight(), worldExtent,
                    artifact.drawPlan().modelLodErrors(), MODEL_LOD_LEVELS.getOrDefault(instance.id(), -1));
            MODEL_LOD_LEVELS.put(instance.id(), requestedLod);
            initialAdmissionCandidates.add(instance.id());
            submitInstance(root, collector, camera, instance, artifact, requestedLod,
                    frameLosses, frameRejections,
                    runtimeFaults, statistics, projector, preserveBlend,
                    translucentSubmissions, frustum, vertexBudget);
        }
        IMMEDIATE_DRAW_CURSORS.keySet().retainAll(liveInstanceIds);
        MODEL_LOD_LEVELS.keySet().retainAll(liveInstanceIds);
        HostArtifactRepository.INSTANCE.retainInitialStaticAdmissions(initialAdmissionCandidates);
        submitTranslucent(root, collector, translucentSubmissions);
        HostArtifactRepository.INSTANCE.maintainStaticCache(System.nanoTime());
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
        if (lightingEnvironment.shadow().installed() && lightingEnvironment.shadow().failure().isEmpty()
                && lightingEnvironment.shadow().submittedDraws() > 0) {
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
        IMMEDIATE_DRAW_CURSORS.clear();
        nextInstanceStart = 0;
        lastProjectorCapability = null; lastLosses = Set.of();
        IrisEntityTranslucency.clear();
        IrisShadowAdapter.invalidateEnvironment();
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
                                       int requestedLod,
                                       EnumSet<ModelCompatibilityLoss> losses,
                                       EnumMap<ModelDrawRejection, Integer> rejections,
                                       List<String> runtimeFaults, FrameStatistics statistics,
                                       ModelProjectorCapability projector, boolean preserveBlend,
                                       List<TranslucentSubmission> translucentSubmissions,
                                       Frustum frustum, HostVertexBudget vertexBudget) {
        LoadedModelResource loaded = instance.resource();
        ModelInstancePlacement placement = instance.state().placement();
        BindingPlan bindingPlan = bindingPlan(artifact.drawPlan(), placement, projector.auxiliaryEnabled(),
                preserveBlend);
        if (!artifact.bindingsReady(bindingPlan.request())) {
            if (bindingPlan.keys().isEmpty()) {
                if (artifact.beginBindings(bindingPlan.request())) {
                    artifact.completeBindings(bindingPlan.request(), artifact.bindingGeneration());
                }
            }
            if (artifact.bindingsFailed(bindingPlan.request())) {
                runtimeFaults.add("host-binding-upload-failed");
                return;
            }
            if (artifact.beginBindings(bindingPlan.request())) {
                HostBindingUpload work = HostBindingUpload.tryCreate(artifact, loaded, bindingPlan);
                if (work == null) {
                    artifact.cancelBindings(bindingPlan.request());
                    runtimeFaults.add("host-binding-budget-waiting");
                } else if (!ClientModelRuntime.INSTANCE.uploadScheduler().enqueue(work)) {
                    work.cancelledByScheduler();
                }
            }
            return;
        }
        HostPreparedArtifact.InitialWorksetStatus initialStatus =
                artifact.initialStaticWorksetStatus(instance.id());
        boolean fieldReplacement = initialStatus == HostPreparedArtifact.InitialWorksetStatus.READY
                || initialStatus == HostPreparedArtifact.InitialWorksetStatus.REPLACING;
        List<HostPreparedArtifact.InitialStaticRequirement> initialWorkset = initialStaticWorkset(
                instance, artifact, loaded, placement, projector, requestedLod, fieldReplacement);
        if (initialWorkset.isEmpty() && initialStatus == HostPreparedArtifact.InitialWorksetStatus.READY
                && artifact.activeStaticWorksetBytes(instance.id()) > 0) {
            HostStaticVariantUpload.retire(artifact.detachStaticVariantsForInstance(instance.id()));
            initialStatus = HostPreparedArtifact.InitialWorksetStatus.EMPTY;
        }
        if ((initialStatus == HostPreparedArtifact.InitialWorksetStatus.BUILDING
                || initialStatus == HostPreparedArtifact.InitialWorksetStatus.REPLACING)
                && !artifact.initialStaticWorksetStructureMatches(instance.id(), initialWorkset)) {
            HostStaticVariantUpload.retire(artifact.restartInitialStaticWorkset(instance.id()));
            initialStatus = artifact.initialStaticWorksetStatus(instance.id());
        }
        if (!initialWorkset.isEmpty() && (initialStatus == HostPreparedArtifact.InitialWorksetStatus.EMPTY
                || initialStatus == HostPreparedArtifact.InitialWorksetStatus.WAITING)) {
            initialStatus = HostArtifactRepository.INSTANCE.requestInitialStaticWorkset(
                    artifact, instance.id(), initialWorkset, System.nanoTime());
        } else if (!initialWorkset.isEmpty()
                && initialStatus == HostPreparedArtifact.InitialWorksetStatus.READY
                && !artifact.activeStaticWorksetMatches(instance.id(), initialWorkset)) {
            initialStatus = HostArtifactRepository.INSTANCE.requestStaticWorksetReplacement(
                    artifact, instance.id(), initialWorkset);
        } else if (initialWorkset.isEmpty()
                && initialStatus == HostPreparedArtifact.InitialWorksetStatus.EMPTY) {
            initialStatus = HostPreparedArtifact.InitialWorksetStatus.READY;
        }
        if (initialStatus == HostPreparedArtifact.InitialWorksetStatus.WAITING
                || initialStatus == HostPreparedArtifact.InitialWorksetStatus.FAILED) {
            runtimeFaults.add(initialStatus == HostPreparedArtifact.InitialWorksetStatus.FAILED
                    ? "static-instance-working-set-oversize" : "static-instance-working-set-waiting");
            if (initialStatus == HostPreparedArtifact.InitialWorksetStatus.FAILED) {
                String diagnosticKey = "static-workset-oversize:" + instance.id().value();
                if (artifact.loggedGeometryFailures.add(diagnosticKey)) {
                    HostStaticCacheMetrics.INSTANCE.recordBudgetReject();
                    long required = initialWorkset.stream().mapToLong(
                            HostPreparedArtifact.InitialStaticRequirement::bytes).sum();
                    GeometryNode.LOGGER.warn("Skipping HOST_NATIVE instance {}: complete initial static working "
                                    + "set requires {} bytes in {} variants; steady limits are {}/{}",
                            instance.id().value(), required, initialWorkset.size(),
                            HostStaticVariantBudget.PER_ARTIFACT_BYTES, HostStaticVariantBudget.GLOBAL_BYTES);
                }
            } else {
                String diagnosticKey = "static-workset-waiting:" + instance.id().value();
                if (artifact.loggedGeometryFailures.add(diagnosticKey)) {
                    HostStaticCacheMetrics.INSTANCE.recordBudgetWait();
                    long required = initialWorkset.stream().mapToLong(
                            HostPreparedArtifact.InitialStaticRequirement::bytes).sum();
                    GeometryNode.LOGGER.warn("Deferring HOST_NATIVE instance {}: complete initial static working "
                                    + "set requires {} bytes in {} variants, but steady cache capacity is not "
                                    + "currently available; the instance stays hidden until admitted",
                            instance.id().value(), required, initialWorkset.size());
                }
            }
            return;
        }
        boolean warmingInitialWorkset = initialStatus == HostPreparedArtifact.InitialWorksetStatus.BUILDING
                || initialStatus == HostPreparedArtifact.InitialWorksetStatus.REPLACING;
        boolean preserveActiveWorkset = initialStatus == HostPreparedArtifact.InitialWorksetStatus.REPLACING
                || initialStatus == HostPreparedArtifact.InitialWorksetStatus.READY
                && !initialWorkset.isEmpty()
                && !artifact.activeStaticWorksetMatches(instance.id(), initialWorkset);
        if (preserveActiveWorkset && initialStatus != HostPreparedArtifact.InitialWorksetStatus.REPLACING) {
            submitActiveWorkset(artifact, instance, placement, camera, statistics);
            return;
        }
        List<VisibleDraw> visibleDraws = visibleDraws(instance, artifact.drawPlan(), placement,
                warmingInitialWorkset ? null : frustum, warmingInitialWorkset, statistics);
        if (visibleDraws.isEmpty()) {
            if (preserveActiveWorkset) submitActiveWorkset(artifact, instance, placement, camera, statistics);
            return;
        }
        int drawCount = visibleDraws.size();
        int start = Math.floorMod(IMMEDIATE_DRAW_CURSORS.getOrDefault(instance.id(), 0), drawCount);
        int firstDeferred = -1;
        for (int drawOffset = 0; drawOffset < drawCount; drawOffset++) {
                int visibleIndex = (start + drawOffset) % drawCount;
                VisibleDraw visible = visibleDraws.get(visibleIndex);
                HostDrawPlan.Draw draw = visible.draw();
                int nodeIndex = draw.nodeIndex();
                int primitiveIndex = draw.primitiveIndex();
                HostPreparedArtifact.StaticDrawSlot drawSlot =
                        new HostPreparedArtifact.StaticDrawSlot(nodeIndex, primitiveIndex);
                HostPreparedArtifact.InitialStaticRequirement frozenRequirement = warmingInitialWorkset
                        ? artifact.initialStaticRequirement(instance.id(), drawSlot) : null;
                if (warmingInitialWorkset && frozenRequirement == null) continue;
                if (!warmingInitialWorkset && !instance.state().nodeState().visible(nodeIndex)) continue;
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
                Optional<HostResolvedDraw> resolvedResult = HostDrawFrameResolver.resolve(
                        draw, placement, visible.nodeWorld(), camera.x, camera.y, camera.z,
                        requestedLod, preserveBlend, materialFallback,
                        fallback -> lightBinding(instance, artifact, draw, fallback),
                        world -> LevelRenderer.getLightCoords(Minecraft.getInstance().level,
                                BlockPos.containing(world.x, world.y, world.z)));
                if (resolvedResult.isEmpty()) {
                    reject(rejections, ModelDrawRejection.SINGULAR_TRANSFORM);
                    statistics.singularTransformSkips++;
                    continue;
                }
                HostResolvedDraw resolved = resolvedResult.get();
                Matrix4f bakedTransform = resolved.transform().baked();
                Matrix4f transform = resolved.transform().cameraRelative();
                boolean mirrored = resolved.transform().mirrored();
                float red = resolved.red();
                float green = resolved.green();
                float blue = resolved.blue();
                float alpha = resolved.alpha();
                StaticSubmission staticSubmission = trySubmitStatic(instance, artifact, loaded, draw, geometry, texture,
                        renderType, placement, camera, visible.nodeWorld(), frustum,
                        resolved, effectiveTranslucent, requestedLod,
                        frozenRequirement);
                if (warmingInitialWorkset) continue;
                if (staticSubmission.status() == StaticSubmissionStatus.CULLED) {
                    statistics.culledDraws++;
                    continue;
                }
                long drawVertices = saturatedMultiply(draw.triangleCount(), 4);
                if (effectiveTranslucent && !opaqueFallback && !materialFallback) {
                    if (!reserveImmediate(vertexBudget, drawVertices, loaded, rejections)) {
                        if (firstDeferred < 0) firstDeferred = visibleIndex;
                        continue;
                    }
                    Vector3d drawPosition = resolved.transform().worldCenter().sub(camera.x, camera.y, camera.z);
                    translucentSubmissions.add(new TranslucentSubmission(
                            new HostTransparentOrderKey((float) drawPosition.lengthSquared(), loaded.asset().cacheIdentity(),
                                    nodeIndex, primitiveIndex, instance.id().value()),
                            new Matrix4f(transform), renderType, geometry,
                            red, green, blue, alpha, resolved.lightBinding(), mirrored));
                } else if (staticSubmission.status() != StaticSubmissionStatus.SUBMITTED) {
                    if (!reserveImmediate(vertexBudget, drawVertices, loaded, rejections)) {
                        if (firstDeferred < 0) firstDeferred = visibleIndex;
                        continue;
                    }
                    if (staticSubmission.status() == StaticSubmissionStatus.FALLBACK) {
                        HostStaticEntityRenderer.recordFallback();
                    }
                    submitGeometry(root, collector, transform, renderType, geometry,
                            red, green, blue, alpha, resolved.lightBinding(), mirrored);
                }
                if (staticSubmission.status() != StaticSubmissionStatus.SUBMITTED) {
                    statistics.submittedVertices += drawVertices;
                    HostStaticEntityRenderer.recordImmediate(drawVertices);
                    statistics.drawCalls++;
                    statistics.triangles += draw.triangleCount();
                } else {
                    statistics.drawCalls += staticSubmission.drawCalls();
                    statistics.triangles += staticSubmission.triangles();
                }
        }
        if (firstDeferred < 0) IMMEDIATE_DRAW_CURSORS.remove(instance.id());
        else IMMEDIATE_DRAW_CURSORS.put(instance.id(), firstDeferred);
        if (preserveActiveWorkset) submitActiveWorkset(artifact, instance, placement, camera, statistics);
    }

    private static List<HostPreparedArtifact.InitialStaticRequirement> initialStaticWorkset(
            ClientModelInstanceRegistry.ReadyInstance instance, HostPreparedArtifact artifact,
            LoadedModelResource loaded, ModelInstancePlacement placement,
            ModelProjectorCapability projector, int requestedLod, boolean allowField) {
        if (instance.pose().animated() || !HostStaticEntityRenderer.available()) return List.of();
        List<HostPreparedArtifact.InitialStaticRequirement> requirements = new ArrayList<>();
        boolean labPbr = projector.auxiliaryEnabled();
        long layoutGeneration = ModelResourceReloadListener.reloadGeneration();
        for (HostDrawPlan.Draw draw : artifact.drawPlan().draws()) {
            if (!instance.state().nodeState().visible(draw.nodeIndex()) || draw.geometry() == null
                    || !draw.projection(projector.profile()).selectable()
                    || loaded.definition().nodes().get(draw.nodeIndex()).skinIndex() >= 0) continue;
            boolean effectiveTranslucent = draw.material().alphaMode() == ModelAlphaMode.BLEND
                    || placement.alpha() < 0.999F;
            if (effectiveTranslucent) continue;
            HostPreparedArtifact.CompatibilityTexture texture;
            try {
                texture = texture(artifact, loaded, draw.material(), labPbr, false);
            } catch (TextureProjectionFailure failure) {
                continue;
            }
            RenderType renderType = renderType(draw.material(), placement, texture.identifier(),
                    texture.defaultMaterialFallback());
            VertexFormat format;
            try {
                format = renderType.pipeline().getVertexFormat();
            } catch (RuntimeException failure) {
                continue;
            }
            if (format == null || format.getVertexSize() < 1) continue;
            boolean materialFallback = texture.defaultMaterialFallback();
            Optional<HostResolvedDraw> resolvedResult = HostDrawFrameResolver.resolve(
                    draw, placement, instance.pose().worldMatrix(draw.nodeIndex()), 0, 0, 0,
                    requestedLod, true, materialFallback,
                    fallback -> allowField
                            ? lightBinding(instance, artifact, draw, fallback)
                            : HostLightBinding.constant(fallback),
                    world -> LevelRenderer.getLightCoords(Minecraft.getInstance().level,
                            BlockPos.containing(world.x, world.y, world.z)));
            if (resolvedResult.isEmpty()) continue;
            HostResolvedDraw resolved = resolvedResult.get();
            HostStaticVariantKey key = resolved.staticVariantKey(instance.id(), instance.pose().revision(),
                    OverlayTexture.NO_OVERLAY, format, layoutGeneration);
            long bytes = Math.multiplyExact(Math.multiplyExact((long) resolved.lod().triangleCount(), 3L),
                    format.getVertexSize());
            requirements.add(new HostPreparedArtifact.InitialStaticRequirement(
                    new HostPreparedArtifact.StaticDrawSlot(draw.nodeIndex(), draw.primitiveIndex()),
                    draw.geometry(), key, resolved.lightBinding(), bytes, renderType, texture));
        }
        return List.copyOf(requirements);
    }

    private static HostLightBinding lightBinding(ClientModelInstanceRegistry.ReadyInstance instance,
                                                 HostPreparedArtifact artifact,
                                                 HostDrawPlan.Draw draw,
                                                 int fallbackPackedLight) {
        if (HostLightingPolicy.snapshot().decision(HostLightingDomain.PLACED_BLOCK).effectiveOwner()
                != HostLightingOwner.HOST_UV2) {
            return HostLightBinding.constant(fallbackPackedLight);
        }
        ClientModelRuntime runtime = ClientModelRuntime.INSTANCE;
        var active = runtime.localLights().active(instance.id());
        if (!(active instanceof HostScalarLightField scalar)
                || !runtime.localLightCompatible(instance, scalar.identity())) {
            return HostLightBinding.constant(fallbackPackedLight);
        }
        return artifact.preparedAsset().lightProjectionPlan().binding(draw, scalar, fallbackPackedLight);
    }

    private static void submitActiveWorkset(HostPreparedArtifact artifact,
                                            ClientModelInstanceRegistry.ReadyInstance instance,
                                            ModelInstancePlacement placement, Vec3 camera,
                                            FrameStatistics statistics) {
        List<ActiveStaticSubmission> submissions = new ArrayList<>();
        long generation = artifact.staticGeneration();
        for (HostDrawPlan.Draw draw : artifact.drawPlan().draws()) {
            HostPreparedArtifact.InitialStaticRequirement required = artifact.activeStaticRequirement(
                    instance.id(), new HostPreparedArtifact.StaticDrawSlot(
                            draw.nodeIndex(), draw.primitiveIndex()));
            if (required == null) continue;
            HostStaticGeometryVariant variant = artifact.staticVariant(
                    required.geometry(), required.key(), generation);
            if (variant == null || !(required.key().layoutIdentity() instanceof VertexFormat format)
                    || !(required.renderType() instanceof RenderType renderType)
                    || !(required.texture() instanceof HostPreparedArtifact.CompatibilityTexture texture)) {
                return;
            }
            submissions.add(new ActiveStaticSubmission(required, variant, format, renderType, texture));
        }
        Vector3f translation = new Vector3f(
                (float) (placement.position().x - camera.x),
                (float) (placement.position().y - camera.y),
                (float) (placement.position().z - camera.z));
        for (ActiveStaticSubmission submission : submissions) {
            HostPreparedArtifact.InitialStaticRequirement required = submission.required();
            int triangles = required.key().triangleCount();
            HostClusterVisibility.Result visibility = HostClusterVisibility.fullRange(
                    new HostClusterVisibility.TriangleRange(0, triangles));
            HostStaticEntityRenderer.submit(submission.variant(), submission.format(), submission.renderType(),
                    submission.texture(), translation, visibility);
            statistics.drawCalls++;
            statistics.triangles += triangles;
        }
    }

    private static boolean reserveImmediate(HostVertexBudget budget, long vertices, LoadedModelResource loaded,
                                            EnumMap<ModelDrawRejection, Integer> rejections) {
        if (budget.reserve(vertices)) return true;
        reject(rejections, ModelDrawRejection.HOST_VERTEX_BUDGET_EXCEEDED);
        HostStaticEntityRenderer.recordDeferredImmediate();
        if (loaded.reportDiagnosticOnce(VERTEX_BUDGET_DIAGNOSTIC)) {
            GeometryNode.LOGGER.warn("Deferring HOST_NATIVE immediate draws for {}: a draw requires {} vertices; "
                            + "frame limit is {}",
                    loaded.asset().cacheIdentity(), vertices, HostVertexBudget.MAX_VERTICES_PER_FRAME);
        }
        return false;
    }

    private static long saturatedMultiply(long value, long multiplier) {
        return value < 0 || value > Long.MAX_VALUE / multiplier ? Long.MAX_VALUE : value * multiplier;
    }

    private static List<VisibleDraw> visibleDraws(ClientModelInstanceRegistry.ReadyInstance instance,
                                                  HostDrawPlan plan, ModelInstancePlacement placement,
                                                  Frustum frustum, boolean ignoreNodeVisibility,
                                                  FrameStatistics statistics) {
        List<VisibleDraw> result = new ArrayList<>();
        int currentNode = -1;
        boolean nodeVisible = false;
        boolean skinned = false;
        Matrix4f nodeWorld = null;
        for (HostDrawPlan.Draw draw : plan.draws()) {
            statistics.candidateDraws++;
            if (draw.nodeIndex() != currentNode) {
                currentNode = draw.nodeIndex();
                nodeVisible = ignoreNodeVisibility || instance.state().nodeState().visible(currentNode);
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
                    submission.lightBinding(), submission.mirrored());
        }
    }

    private static void submitGeometry(PoseStack root,
                                       net.minecraft.client.renderer.OrderedSubmitNodeCollector collector,
                                       Matrix4f transform, RenderType renderType, HostEntityGeometry geometry,
                                       float red, float green, float blue, float alpha,
                                       HostLightBinding lightBinding, boolean mirrored) {
        root.pushPose();
        try {
            root.mulPose(transform);
            collector.submitCustomGeometry(root, renderType,
                    (pose, vertices) -> geometry.emit(
                            pose, vertices, red, green, blue, alpha, lightBinding, mirrored));
        } finally {
            root.popPose();
        }
    }

    private static StaticSubmission trySubmitStatic(ClientModelInstanceRegistry.ReadyInstance instance,
                                           HostPreparedArtifact artifact, LoadedModelResource loaded,
                                           HostDrawPlan.Draw draw, HostEntityGeometry geometry,
                                           HostPreparedArtifact.CompatibilityTexture texture,
                                           RenderType renderType, ModelInstancePlacement placement, Vec3 camera,
                                           Matrix4f nodeWorld, Frustum frustum,
                                           HostResolvedDraw resolved,
                                           boolean effectiveTranslucent, int requestedLod,
                                           HostPreparedArtifact.InitialStaticRequirement frozenRequirement) {
        if (frozenRequirement == null && (effectiveTranslucent
                || draw.material().alphaMode() == ModelAlphaMode.BLEND
                || placement.alpha() < 0.999F || instance.pose().animated())
                || loaded.definition().nodes().get(draw.nodeIndex()).skinIndex() >= 0
                || !HostStaticEntityRenderer.available()) {
            return StaticSubmission.ineligible();
        }
        VertexFormat format;
        try {
            format = renderType.pipeline().getVertexFormat();
        } catch (RuntimeException failure) {
            return StaticSubmission.ineligible();
        }
        if (format == null || format.getVertexSize() < 1) return StaticSubmission.ineligible();
        HostModelLodPlan.Level level;
        try {
            level = frozenRequirement == null ? resolved.lod() : geometry.lod().level(requestedLod);
        } catch (RuntimeException failure) {
            return StaticSubmission.fallback();
        }
        long layoutGeneration = ModelResourceReloadListener.reloadGeneration();
        HostPreparedArtifact.StaticDrawSlot drawSlot = new HostPreparedArtifact.StaticDrawSlot(
                draw.nodeIndex(), draw.primitiveIndex());
        HostStaticVariantKey key = frozenRequirement == null
                ? resolved.staticVariantKey(instance.id(), instance.pose().revision(),
                        OverlayTexture.NO_OVERLAY, format, layoutGeneration)
                : frozenRequirement.key();
        long generation = artifact.staticGeneration();
        HostStaticGeometryVariant variant = artifact.staticVariant(geometry, key, generation);
        if (variant == null) HostStaticCacheMetrics.INSTANCE.recordMiss();
        else HostStaticCacheMetrics.INSTANCE.recordHit();
        HostStaticVariantAdmissionGate gate = artifact.staticVariantGate(geometry, instance.id());
        HostStaticAdmissionKey admissionKey = key.admissionKey();
        HostStaticVariantAdmissionGate.Decision decision = gate.evaluate(
                admissionKey, variant != null, generation, System.nanoTime());
        if (decision == HostStaticVariantAdmissionGate.Decision.HIT && variant != null) {
            HostClusterVisibility.Result visibility;
            try {
                HostStaticEntityRenderer.recordModelLod(geometry, requestedLod, level.generatedLevel());
                visibility = level.generatedLevel() == 0
                        ? HostClusterVisibility.evaluate(geometry.clusters(), bounds -> frustum == null
                                || frustum.isVisible(ModelRenderBounds.worldBounds(bounds, nodeWorld, placement)))
                        : HostClusterVisibility.fullRange(new HostClusterVisibility.TriangleRange(
                                0, level.triangleCount()));
            } catch (RuntimeException failure) {
                return StaticSubmission.fallback();
            }
            if (visibility.fullyCulled()) {
                HostStaticEntityRenderer.recordCulled(visibility);
                return StaticSubmission.culled();
            }
            HostStaticEntityRenderer.submit(variant, format, renderType, texture, new Vector3f(
                    (float) (placement.position().x - camera.x),
                    (float) (placement.position().y - camera.y),
                    (float) (placement.position().z - camera.z)), visibility);
            return StaticSubmission.submitted(visibility.submittedTriangles(), visibility.drawCalls());
        }
        if (decision != HostStaticVariantAdmissionGate.Decision.BUILD) {
            if (decision == HostStaticVariantAdmissionGate.Decision.BUILDING) {
                HostStaticEntityRenderer.recordBuilding();
            }
            return StaticSubmission.fallback();
        }

        HostStaticVariantUpload upload;
        try {
            HostLightBinding uploadLight = frozenRequirement == null
                    ? resolved.lightBinding() : frozenRequirement.lightBinding();
            upload = HostStaticVariantUpload.tryCreate(artifact, geometry, drawSlot, key, gate, admissionKey,
                    uploadLight, format,
                    () -> artifact.staticGeneration(generation)
                            && ModelResourceReloadListener.reloadGeneration() == layoutGeneration
                            && renderType.pipeline().getVertexFormat() == format,
                    loaded.asset().cacheIdentity() + ':' + draw.nodeIndex() + ':' + draw.primitiveIndex());
        } catch (RuntimeException failure) {
            gate.recordFailure(admissionKey, generation);
            return StaticSubmission.fallback();
        }
        if (upload == null) {
            if (artifact.initialStaticWorksetBuilding(instance.id())) {
                HostStaticVariantUpload.retire(artifact.failInitialStaticWorkset(instance.id()));
                gate.recordFailure(admissionKey, generation);
                String diagnosticKey = "initial-static-workset-mismatch:" + instance.id().value();
                if (artifact.loggedGeometryFailures.add(diagnosticKey)) {
                    GeometryNode.LOGGER.error("Initial static working-set reservation did not match the actual "
                                    + "upload for {} node={} primitive={}; the instance remains hidden",
                            loaded.asset().cacheIdentity(), draw.nodeIndex(), draw.primitiveIndex());
                }
                return StaticSubmission.fallback();
            }
            List<HostStaticGeometryVariant> retired = artifact.detachStaticVariantForBudget(
                    geometry, key, generation);
            HostStaticVariantUpload.retire(retired);
            if (retired.isEmpty()) {
                long requiredBytes = Math.multiplyExact(
                        Math.multiplyExact((long) level.triangleCount(), 3L), format.getVertexSize());
                if (requiredBytes > HostStaticVariantBudget.PER_ARTIFACT_BYTES
                        || requiredBytes > HostStaticVariantBudget.GLOBAL_BYTES) {
                    HostStaticCacheMetrics.INSTANCE.recordBudgetReject();
                    gate.recordFailure(admissionKey, generation);
                } else {
                    HostStaticCacheMetrics.INSTANCE.recordBudgetWait();
                    long nowNanos = System.nanoTime();
                    HostArtifactRepository.INSTANCE.requestStaticCapacity(artifact, nowNanos);
                    gate.recordBudgetWait(admissionKey, generation, nowNanos);
                }
                String diagnosticKey = "static-budget:" + draw.nodeIndex() + ':' + draw.primitiveIndex();
                if (artifact.loggedGeometryFailures.add(diagnosticKey)) {
                    GeometryNode.LOGGER.warn("Static HOST budget rejected {} node={} primitive={}: required={} "
                                    + "artifact-resident={} global-resident={} limits={}/{}",
                            loaded.asset().cacheIdentity(), draw.nodeIndex(), draw.primitiveIndex(), requiredBytes,
                            HostStaticVariantBudget.INSTANCE.artifactBytes(artifact),
                            HostStaticVariantBudget.INSTANCE.reservedBytes(),
                            HostStaticVariantBudget.PER_ARTIFACT_BYTES,
                            HostStaticVariantBudget.GLOBAL_BYTES);
                }
            } else {
                gate.recordCancelled(admissionKey, generation);
            }
            return StaticSubmission.fallback();
        }
        if (!ClientModelRuntime.INSTANCE.uploadScheduler().enqueue(upload)) upload.cancelledByScheduler();
        else {
            HostStaticCacheMetrics.INSTANCE.recordBuildStarted();
            HostStaticEntityRenderer.recordBuilding();
        }
        return StaticSubmission.fallback();
    }

    private enum StaticSubmissionStatus { SUBMITTED, CULLED, FALLBACK, INELIGIBLE }

    private record StaticSubmission(StaticSubmissionStatus status, long triangles, int drawCalls) {
        private static StaticSubmission submitted(long triangles, int drawCalls) {
            return new StaticSubmission(StaticSubmissionStatus.SUBMITTED, triangles, drawCalls);
        }
        private static StaticSubmission culled() {
            return new StaticSubmission(StaticSubmissionStatus.CULLED, 0, 0);
        }
        private static StaticSubmission fallback() {
            return new StaticSubmission(StaticSubmissionStatus.FALLBACK, 0, 0);
        }
        private static StaticSubmission ineligible() {
            return new StaticSubmission(StaticSubmissionStatus.INELIGIBLE, 0, 0);
        }
    }

    private record ActiveStaticSubmission(HostPreparedArtifact.InitialStaticRequirement required,
                                          HostStaticGeometryVariant variant, VertexFormat format,
                                          RenderType renderType,
                                          HostPreparedArtifact.CompatibilityTexture texture) {}

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
        return texture(artifact, loaded, material, labPbr, opaqueFallback, null);
    }

    private static HostPreparedArtifact.CompatibilityTexture texture(HostPreparedArtifact artifact,
                                                LoadedModelResource loaded, StaticModelMaterial material,
                                                boolean labPbr, boolean opaqueFallback,
                                                HostTextureBindingBudget.Reservation reservation) {
        HostPreparedArtifact.TextureKey key = textureKey(material, labPbr, opaqueFallback);
        if (artifact.failedTextures.contains(key)) {
            throw TextureProjectionFailure.asset("compatibility base-color asset previously failed", null);
        }
        HostPreparedArtifact.CompatibilityTexture existing = artifact.textures.get(key);
        if (existing != null) return existing;
        if (reservation == null) {
            throw new IllegalStateException("missing HOST texture binding reservation");
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
                    image = decode(artifact, material.baseColorTexture(), inputs);
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
                    HostPreparedArtifact.LabPbrImages prepared = artifact.labPbrImages(material);
                    try {
                        specular = nativeImage(prepared.specular(), inputs);
                        normal = nativeImage(prepared.normal(), inputs);
                        if (prepared.metallicRoughnessDecodeFailed()) {
                            textureLosses.add(ModelCompatibilityLoss.TEXTURE_DECODE_FAILED);
                            textureLosses.add(ModelCompatibilityLoss.METALLIC_UNREPRESENTABLE);
                            textureLosses.add(ModelCompatibilityLoss.ROUGHNESS_UNREPRESENTABLE);
                        }
                        if (prepared.normalDecodeFailed()) {
                            textureLosses.add(ModelCompatibilityLoss.TEXTURE_DECODE_FAILED);
                            textureLosses.add(ModelCompatibilityLoss.NORMAL_TEXTURE_UNREPRESENTABLE);
                        }
                        if (prepared.occlusionDecodeFailed()) {
                            textureLosses.add(ModelCompatibilityLoss.TEXTURE_DECODE_FAILED);
                            textureLosses.add(ModelCompatibilityLoss.OCCLUSION_TEXTURE_UNREPRESENTABLE);
                        }
                        if (!prepared.metallicEndpointsOnly()) {
                            textureLosses.add(ModelCompatibilityLoss.METALLIC_UNREPRESENTABLE);
                        }
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
                long residentBytes = imageBytes(image);
                int residentObjects = 1;
                if (normal != null) {
                    residentBytes = Math.addExact(residentBytes, imageBytes(normal));
                    residentObjects++;
                }
                if (specular != null) {
                    residentBytes = Math.addExact(residentBytes, imageBytes(specular));
                    residentObjects++;
                }
                dynamic = new IrisLabPbrProjector.LabPbrAlbedoTexture(image, normal, specular,
                        material.baseColorTexture().sampler());
                inputs.remove(image);
                inputs.remove(normal);
                inputs.remove(specular);
                Minecraft.getInstance().getTextureManager().register(id, dynamic);
                registered = true;
                if (key.labPbr() && !defaultMaterialFallback) IrisLabPbrProjector.afterAlbedoRegistration(dynamic);
                reservation.markResident();
                return new HostPreparedArtifact.CompatibilityTexture(id, dynamic, textureLosses,
                        defaultMaterialFallback, residentBytes, residentObjects, reservation);
            } catch (TextureProjectionFailure exception) {
                if (registered) Minecraft.getInstance().getTextureManager().release(id);
                else if (dynamic != null) dynamic.close();
                reservation.close();
                throw exception;
            } catch (RuntimeException exception) {
                if (registered) Minecraft.getInstance().getTextureManager().release(id);
                else if (dynamic != null) dynamic.close();
                reservation.close();
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

    private static NativeImage decode(HostPreparedArtifact artifact, StaticModelTexture texture,
                                      List<NativeImage> owned)
            throws IOException {
        if (!texture.present()) return null;
        DecodedModelImage decoded = artifact.decodedImage(texture.imageIndex());
        return nativeImage(decoded, owned);
    }

    private static long imageBytes(NativeImage image) {
        return Math.multiplyExact(Math.multiplyExact((long) image.getWidth(), image.getHeight()), 4L);
    }

    private static BindingPlan bindingPlan(HostDrawPlan drawPlan, ModelInstancePlacement placement,
                                           boolean labPbr, boolean preserveBlend) {
        LinkedHashSet<HostPreparedArtifact.TextureKey> keys = new LinkedHashSet<>();
        for (HostDrawPlan.Draw draw : drawPlan.draws()) {
            StaticModelMaterial material = draw.material();
            boolean translucent = material.alphaMode() == ModelAlphaMode.BLEND || placement.alpha() < 0.999F;
            keys.add(textureKey(material, labPbr, translucent && !preserveBlend));
        }
        List<HostPreparedArtifact.TextureKey> ordered = List.copyOf(keys);
        return new BindingPlan(new HostPreparedArtifact.BindingRequest(keys), ordered);
    }

    private static NativeImage nativeImage(DecodedModelImage decoded, List<NativeImage> owned) {
        if (decoded == null) return null;
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

    private static void reject(EnumMap<ModelDrawRejection, Integer> rejections, ModelDrawRejection reason) {
        rejections.merge(reason, 1, Integer::sum);
    }
    private record TranslucentSubmission(HostTransparentOrderKey orderKey,
                                         Matrix4f transform, RenderType renderType,
                                         HostEntityGeometry geometry, float red, float green, float blue, float alpha,
                                         HostLightBinding lightBinding, boolean mirrored) {
        private static final Comparator<TranslucentSubmission> ORDER =
                Comparator.comparing(TranslucentSubmission::orderKey);
    }
    private record VisibleDraw(HostDrawPlan.Draw draw, Matrix4f nodeWorld) {}
    private record BindingPlan(HostPreparedArtifact.BindingRequest request,
                               List<HostPreparedArtifact.TextureKey> keys) {}

    private static final class HostBindingUpload implements com.mine.geometry_node.client.model.gpu.ModelUploadScheduler.WorkItem {
        private final HostPreparedArtifact artifact;
        private final LoadedModelResource loaded;
        private final BindingPlan plan;
        private final long generation;
        private final HostTextureBindingBudget.BatchReservation reservation;
        private final List<HostPreparedArtifact.TextureKey> created = new ArrayList<>();
        private int index;

        private static HostBindingUpload tryCreate(HostPreparedArtifact artifact, LoadedModelResource loaded,
                                                   BindingPlan plan) {
            List<HostTextureBindingBudget.Footprint> missing = new ArrayList<>();
            for (HostPreparedArtifact.TextureKey key : plan.keys()) {
                if (!artifact.textures.containsKey(key)) {
                    missing.add(new HostTextureBindingBudget.Footprint(
                            artifact.bindingBytes(key), artifact.bindingObjects(key)));
                }
            }
            if (missing.isEmpty()) return new HostBindingUpload(artifact, loaded, plan, null);
            HostTextureBindingBudget.BatchReservation reservation =
                    HostTextureBindingBudget.INSTANCE.tryReserveBatch(artifact, missing);
            return reservation == null ? null : new HostBindingUpload(artifact, loaded, plan, reservation);
        }

        private HostBindingUpload(HostPreparedArtifact artifact, LoadedModelResource loaded, BindingPlan plan,
                                  HostTextureBindingBudget.BatchReservation reservation) {
            this.artifact = artifact;
            this.loaded = loaded;
            this.plan = plan;
            this.generation = artifact.bindingGeneration();
            this.reservation = reservation;
        }

        @Override public long nextBytes() {
            return index < plan.keys().size() ? artifact.bindingBytes(plan.keys().get(index)) : 0;
        }
        @Override public int nextObjects() {
            return index < plan.keys().size() ? artifact.bindingObjects(plan.keys().get(index)) : 0;
        }
        @Override public long remainingBytes() {
            long bytes = 0;
            for (int pending = index; pending < plan.keys().size(); pending++) {
                bytes = Math.addExact(bytes, artifact.bindingBytes(plan.keys().get(pending)));
            }
            return bytes;
        }
        @Override public int remainingObjects() {
            int objects = 0;
            for (int pending = index; pending < plan.keys().size(); pending++) {
                objects = Math.addExact(objects, artifact.bindingObjects(plan.keys().get(pending)));
            }
            return objects;
        }
        @Override public long stagingBytes() { return 0; }
        @Override public boolean cancelled() { return !artifact.bindingGeneration(generation); }
        @Override public boolean runStep() {
            HostPreparedArtifact.TextureKey key = plan.keys().get(index);
            boolean existed = artifact.textures.containsKey(key);
            HostTextureBindingBudget.Reservation claimed = null;
            if (!existed) {
                HostTextureBindingBudget.Footprint footprint = new HostTextureBindingBudget.Footprint(
                        artifact.bindingBytes(key), artifact.bindingObjects(key));
                claimed = Objects.requireNonNull(reservation, "HOST binding batch reservation").claim(footprint);
                if (claimed == null) throw new IllegalStateException("HOST binding reservation did not match plan");
            }
            texture(artifact, loaded, key.material(), key.labPbr(), key.opaqueFallback(), claimed);
            if (!existed) created.add(key);
            return ++index == plan.keys().size();
        }
        @Override public void completed() {
            if (reservation != null) reservation.close();
            artifact.completeBindings(plan.request(), generation);
        }
        @Override public void cancelledByScheduler() { rollback(false); }
        @Override public void failed(Throwable failure) {
            rollback(true);
            GeometryNode.LOGGER.warn("HOST binding upload failed for {}",
                    loaded.asset().cacheIdentity(), failure);
        }
        private void rollback(boolean failed) {
            List<HostPreparedArtifact.CompatibilityTexture> bindings = artifact.removeBindings(created);
            HostPreparedArtifact.releaseBindings(Minecraft.getInstance().getTextureManager(), bindings);
            if (reservation != null) reservation.close();
            if (artifact.bindingGeneration(generation)) {
                if (failed) artifact.failBindings(plan.request());
                else artifact.cancelBindings(plan.request());
            }
        }
    }
    private static final class FrameStatistics {
        private int drawCalls;
        private long triangles;
        private int singularTransformSkips;
        private int candidateDraws;
        private int culledDraws;
        private long submittedVertices;
    }

}
