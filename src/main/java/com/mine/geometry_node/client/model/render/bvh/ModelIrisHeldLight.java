package com.mine.geometry_node.client.model.render.bvh;

import com.mine.geometry_node.client.model.render.geometry.ModelRenderAsset;
import com.mine.geometry_node.client.model.render.geometry.ModelRenderInstance;
import com.mine.geometry_node.client.model.render.geometry.ModelQualityDistance;
import com.mine.geometry_node.client.model.render.resource.ModelIrisUploadBudget;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.opengl.GlStateManager;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.FloatBuffer;
import java.util.*;
import java.util.function.IntSupplier;

/** Optional Iris-owned sampler; static asset-local BVHs are shared by transformed instances. */
public final class ModelIrisHeldLight {
    private static final int MAX_INSTANCES = 8, MAX_RECEIVERS = 128, WIDTH = 2048;
    private static final long MAX_ATLAS_BYTES = 128L << 20, UPLOAD_BYTES = 2L << 20, UPLOAD_NANOS = 2_000_000;
    private record Uniforms(int count, int inverse, int ranges, int placedReady, int placedCount, int placedSources, int blockOrigin, int placedRanges,
                             int dynamicCount, int dynamicSources, int heldSource, int bvhEnabled, int bvhRevision,
                             int heldEnabled, int placedEnabled, int dynamicEnabled, int boundsMin, int boundsMax,
                             int worldBoundsMin, int worldBoundsMax, int primaryGiEnabled, int secondaryGiEnabled,
                             int heldOcclusionEnabled, int worldOcclusionEnabled, int seusPlacedReady, int seusAreaQuality,
                             int autoQuality, int manualSamples,
                             int receiverQuality, int globalLocal, int globalGi, int manualResolution, int receiverCount) {}
    private record Region(ModelLightOcclusionScene scene, int base) {}
    private static final Map<Integer, Uniforms> PROGRAMS = new HashMap<>();
    private static final Map<Integer, Uniforms> NATIVE_PROGRAMS = new HashMap<>();
    private static final ModelSeusProgramRegistry SEUS_PROGRAMS = new ModelSeusProgramRegistry();
    private static int lastSeusProgram;
    private static final float[] heldSource = new float[4];
    private static Method itemEmission;
    private static boolean itemProviderResolved;
    private static List<ModelLightOcclusionScene> keys = List.of();
    private static List<Region> regions = List.of();
    private static final float[] inverses = new float[MAX_INSTANCES * 16];
    private static final int[] ranges = new int[MAX_INSTANCES * 4];
    /** Local-space root AABBs for the uploaded model instances; used to reject rays before atlas fetches. */
    private static final float[] boundsMin = new float[MAX_INSTANCES * 4];
    private static final float[] boundsMax = new float[MAX_INSTANCES * 4];
    /** Camera-relative world-space bounds; rejects rays before inverse transforms and local atlas access. */
    private static final float[] worldBoundsMin = new float[MAX_INSTANCES * 4];
    private static final float[] worldBoundsMax = new float[MAX_INSTANCES * 4];
    /** Per receiver: local samples, local scale, GI enabled, nearest model distance. */
    private static final float[] receiverQuality = new float[MAX_RECEIVERS * 4];
    private static final String[] receiverIds = new String[MAX_RECEIVERS];
    private static final boolean[] receiverVisible = new boolean[MAX_RECEIVERS];
    private static final boolean[] receiverAutoDisabled = new boolean[MAX_RECEIVERS];
    private static int receiverCount, excludedReceivers;
    private static int texture, height, regionIndex, regionFloat, count, excluded, opaqueAnyHitInstances, transmissionInstances;
    private static long gpuBytes, retiredBytes, uploadBytes, totalUploadBytes, updateNanos, peakUpdateNanos, generation;
    private static boolean ready, retryBudget;
    private static final long RETRY_NANOS = 1_000_000_000L;
    private static long retryAfterNanos;
    private static boolean lightingEnabled=true;
    private static boolean bvhEnabled=true, heldEnabled=true, placedEnabled=true, dynamicEnabled=true;
    private static boolean primaryGiEnabled=true, secondaryGiEnabled=true, heldOcclusionEnabled=true, worldOcclusionEnabled=true;
    public record ModuleState(boolean lighting, boolean bvh, boolean held, boolean placed, boolean dynamic,
                              boolean primaryGi, boolean secondaryGi, boolean heldOcclusion, boolean worldOcclusion) {}
    /** Diagnostic A/B toggle: keep resident resources, disable only custom illumination queries. */
    public static void setLightingEnabled(boolean enabled) {RenderSystem.assertOnRenderThread();lightingEnabled=enabled;}
    /** Diagnostic A/B toggle: keep local light sources active, disable only model BVH visibility queries. */
    public static void setBvhEnabled(boolean enabled) {RenderSystem.assertOnRenderThread();bvhEnabled=enabled;}
    public static ModuleState moduleState() {
        RenderSystem.assertOnRenderThread();
        return new ModuleState(lightingEnabled,bvhEnabled,heldEnabled,placedEnabled,dynamicEnabled,
                primaryGiEnabled,secondaryGiEnabled,heldOcclusionEnabled,worldOcclusionEnabled);
    }
    public static void applyModuleState(ModuleState state) {
        RenderSystem.assertOnRenderThread(); Objects.requireNonNull(state);
        lightingEnabled=state.lighting(); bvhEnabled=state.bvh(); heldEnabled=state.held(); placedEnabled=state.placed(); dynamicEnabled=state.dynamic();
        primaryGiEnabled=state.primaryGi(); secondaryGiEnabled=state.secondaryGi();
        heldOcclusionEnabled=state.heldOcclusion(); worldOcclusionEnabled=state.worldOcclusion();
    }

    private static String failure = "", adapterFailure = "", status = "NO_COMPATIBLE_PROGRAM";

    /** Called while Iris builds a program, before its sampler layout becomes immutable. */
    public static void registerProgram(Object builder) {
        RenderSystem.assertOnRenderThread();
        try {
            Class<?> type = builder.getClass();
            if (!(boolean) type.getMethod("hasSampler", String.class).invoke(builder, "geometry_node_lightBvh")) return;
            Field programField = type.getDeclaredField("program"); programField.setAccessible(true);
            int program = programField.getInt(builder);
            if (PROGRAMS.containsKey(program)) return;
            int countLocation = GL20C.glGetUniformLocation(program, "geometry_node_lightCount");
            int inverse = GL20C.glGetUniformLocation(program, "geometry_node_lightInverse[0]");
            int range = GL20C.glGetUniformLocation(program, "geometry_node_lightRange[0]");
            if (countLocation < 0 || inverse < 0 || range < 0) throw new IllegalStateException("Incomplete held-light uniform interface");
            Method add = Arrays.stream(type.getMethods()).filter(m -> m.getName().equals("addDynamicSampler")
                    && m.getParameterCount() == 3 && m.getParameterTypes()[0] == IntSupplier.class).findFirst().orElseThrow();
            boolean registered = (boolean) add.invoke(builder, (IntSupplier) () -> texture, null, new String[]{"geometry_node_lightBvh"});
            if (!registered) throw new IllegalStateException("Iris did not allocate the occlusion sampler");
            boolean needsPlaced = (boolean) type.getMethod("hasSampler", String.class).invoke(builder, "geometry_node_blockWorld");
            boolean placedRegistered = !needsPlaced || (boolean) add.invoke(builder, (IntSupplier) ModelPlacedLightField::texture, null,
                    new String[]{"geometry_node_blockWorld"});
            if (!placedRegistered) throw new IllegalStateException("Iris did not allocate placed-light sampler");
            Uniforms uniforms = locations(program);
            PROGRAMS.put(program, uniforms);
            if (uniforms.seusPlacedReady >= 0) SEUS_PROGRAMS.register(program, uniforms.primaryGiEnabled >= 0);
            ModelBvhLightResolution.registerProgram(builder, program);
            adapterFailure = "";
            status = "WAITING_FOR_SCENE";
        } catch (ReflectiveOperationException | RuntimeException e) {
            adapterFailure = "held-light sampler registration: " + e;
            status = "ADAPTER_UNAVAILABLE";
        }
    }

    private static Uniforms locations(int program) {
        return new Uniforms(GL20C.glGetUniformLocation(program,"geometry_node_lightCount"),
            GL20C.glGetUniformLocation(program,"geometry_node_lightInverse[0]"),GL20C.glGetUniformLocation(program,"geometry_node_lightRange[0]"),
            GL20C.glGetUniformLocation(program,"geometry_node_placedReady"),GL20C.glGetUniformLocation(program,"geometry_node_placedFieldCount"),
            GL20C.glGetUniformLocation(program,"geometry_node_placedSources[0]"),GL20C.glGetUniformLocation(program,"geometry_node_blockOrigins[0]"),
            GL20C.glGetUniformLocation(program,"geometry_node_placedRanges[0]"),GL20C.glGetUniformLocation(program,"geometry_node_dynamicCount"),
            GL20C.glGetUniformLocation(program,"geometry_node_dynamicSources[0]"),GL20C.glGetUniformLocation(program,"geometry_node_heldSource"),
            GL20C.glGetUniformLocation(program,"geometry_node_bvhEnabled"),GL20C.glGetUniformLocation(program,"geometry_node_bvhRevision"),
            GL20C.glGetUniformLocation(program,"geometry_node_heldEnabled"),
            GL20C.glGetUniformLocation(program,"geometry_node_placedEnabled"),GL20C.glGetUniformLocation(program,"geometry_node_dynamicEnabled"),
            GL20C.glGetUniformLocation(program,"geometry_node_lightBoundsMin[0]"),GL20C.glGetUniformLocation(program,"geometry_node_lightBoundsMax[0]"),
            GL20C.glGetUniformLocation(program,"geometry_node_lightWorldBoundsMin[0]"),GL20C.glGetUniformLocation(program,"geometry_node_lightWorldBoundsMax[0]"),
            GL20C.glGetUniformLocation(program,"geometry_node_primaryGiEnabled"),GL20C.glGetUniformLocation(program,"geometry_node_secondaryGiEnabled"),
            GL20C.glGetUniformLocation(program,"geometry_node_heldOcclusionEnabled"),GL20C.glGetUniformLocation(program,"geometry_node_worldOcclusionEnabled"),
            GL20C.glGetUniformLocation(program,"geometry_node_seusPlacedReady"),
            GL20C.glGetUniformLocation(program,"bvhAreaSamples"),
            GL20C.glGetUniformLocation(program,"bvhAutoQuality"),
            GL20C.glGetUniformLocation(program,"bvhManualSamples"),
            GL20C.glGetUniformLocation(program,"bvhReceiverQuality[0]"),
            GL20C.glGetUniformLocation(program,"bvhGlobalLocalEnabled"),
            GL20C.glGetUniformLocation(program,"bvhGlobalGiEnabled"),
            GL20C.glGetUniformLocation(program,"bvhManualResolution"),
            GL20C.glGetUniformLocation(program,"bvhReceiverCount"));
    }
    public static int receiverSlot(String id) {
        for (int i = 0; i < receiverCount; i++) if (Objects.equals(receiverIds[i], id)) return i;
        return -1;
    }
    public static int nativeTexture() {return texture;}
    static float autoMinimumScale() {
        float minimum = 1;
        for (int i = 0; i < receiverCount; i++) if (receiverVisible[i] && receiverQuality[i * 4 + 1] > 0)
            minimum = Math.min(minimum, receiverQuality[i * 4 + 1]);
        return minimum;
    }
    static float autoMaximumScale() {
        float maximum = 0;
        for (int i = 0; i < receiverCount; i++) if (receiverVisible[i]) maximum = Math.max(maximum, receiverQuality[i * 4 + 1]);
        return maximum;
    }
    static boolean hasReducedAutoReceivers() {
        if (!ready || !lightingEnabled || !heldEnabled && !placedEnabled) return false;
        for (int instance = 0; instance < receiverCount; instance++) {
            float scale = receiverQuality[instance * 4 + 1];
            if (receiverVisible[instance] && scale > 0 && scale < 1) return true;
        }
        var global = ModelLightingSettings.global();
        return global.localEnabled() && !global.quality().automatic()
                && global.quality().areaSamples() > 0 && global.quality().resolutionScale() > 0
                && global.quality().resolutionScale() < 1;
    }
    static int heldEmission() { return (int) heldSource[3]; }
    public static void uploadNativeUniforms(int program) {upload(NATIVE_PROGRAMS.computeIfAbsent(program,ModelIrisHeldLight::locations), false);}
    public static Object collectPipelinePrograms(java.util.function.Supplier<Object> factory) {
        RenderSystem.assertOnRenderThread();
        try (var scope = SEUS_PROGRAMS.begin()) {
            Object pipeline = factory.get();
            if (pipeline != null && pipeline.getClass().getName().equals("net.irisshaders.iris.pipeline.IrisRenderingPipeline")) scope.commit();
            return pipeline;
        }
    }
    private static void updateHeldSource(Vec3 camera) {
        heldSource[3]=0;
        var mc=net.minecraft.client.Minecraft.getInstance();var player=mc.player;if(player==null)return;
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        var eye=player.getEyePosition(partialTick);
        if(!itemProviderResolved) {
            itemProviderResolved=true;
            try {itemEmission=Class.forName("net.irisshaders.iris.api.v0.item.IrisItemLightProvider").getMethod("getLightEmission",
                net.minecraft.world.entity.player.Player.class,net.minecraft.world.item.ItemStack.class);}catch(ReflectiveOperationException ignored) {}
        }
        boolean rightHand = player.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT;
        int hand = 0;
        for(var stack:java.util.List.of(player.getMainHandItem(),player.getOffhandItem())) {
            int value=0;
            if(itemEmission!=null&&itemEmission.getDeclaringClass().isInstance(stack.getItem())) {
                try {value=((Number)itemEmission.invoke(stack.getItem(),player,stack)).intValue();}catch(ReflectiveOperationException ignored) {}
            }else if(stack.getItem() instanceof net.minecraft.world.item.BlockItem block) {
                var state=block.getBlock().defaultBlockState();var properties=stack.get(net.minecraft.core.component.DataComponents.BLOCK_STATE);
                if(properties!=null)state=properties.apply(state);
                value=state.getLightEmission();
            }
            value = Math.clamp(value, 0, 15);
            if (value > heldSource[3]) {
                heldSource[3] = value;
                rightHand = (player.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT) == (hand == 0);
            }
            hand++;
        }
        var offset = ModelHeldLightPosition.offset(player.getViewYRot(partialTick), player.getViewXRot(partialTick), rightHand);
        heldSource[0]=(float)(eye.x-camera.x+offset.x);
        heldSource[1]=(float)(eye.y-camera.y+offset.y);
        heldSource[2]=(float)(eye.z-camera.z+offset.z);
    }
    /** Called after Iris's own uniforms/samplers, only matched programs perform GL operations. */
    public static void uploadUniforms(int program) {
        Uniforms uniforms = PROGRAMS.get(program);
        if (uniforms == null) return;
        if (uniforms.seusPlacedReady >= 0) lastSeusProgram = program;
        upload(uniforms, SEUS_PROGRAMS.ready(program));
    }
    private static void upload(Uniforms uniforms, boolean seusPlacedReady) {
        GL20C.glUniform4fv(uniforms.heldSource, heldSource);
        var quality = ModelLightingQuality.active();
        // Receiver policy gates local light, independently from GI and atlas membership.
        boolean localDirectEnabled = true;
        boolean anyModule = heldEnabled || placedEnabled || dynamicEnabled || primaryGiEnabled || secondaryGiEnabled;
        int activeCount = ready && lightingEnabled && anyModule ? count : 0;
        GL20C.glUniform1i(uniforms.count, activeCount);
        if (uniforms.bvhEnabled >= 0) GL20C.glUniform1i(uniforms.bvhEnabled,
                activeCount > 0 && bvhEnabled ? 1 : 0);
        if (uniforms.bvhRevision >= 0) GL20C.glUniform1i(uniforms.bvhRevision, (int) generation);
        if (uniforms.heldEnabled >= 0) GL20C.glUniform1i(uniforms.heldEnabled, activeCount > 0 && heldEnabled && localDirectEnabled ? 1 : 0);
        if (uniforms.placedEnabled >= 0) GL20C.glUniform1i(uniforms.placedEnabled, activeCount > 0 && placedEnabled && localDirectEnabled ? 1 : 0);
        if (uniforms.dynamicEnabled >= 0) GL20C.glUniform1i(uniforms.dynamicEnabled, activeCount > 0 && dynamicEnabled ? 1 : 0);
        GL20C.glUniform1i(uniforms.primaryGiEnabled, primaryGiEnabled ? 1 : 0);
        GL20C.glUniform1i(uniforms.secondaryGiEnabled, secondaryGiEnabled && anyGiReceivers() ? 1 : 0);
        GL20C.glUniform1i(uniforms.heldOcclusionEnabled, heldOcclusionEnabled ? 1 : 0);
        GL20C.glUniform1i(uniforms.worldOcclusionEnabled, worldOcclusionEnabled ? 1 : 0);
        GL20C.glUniform1i(uniforms.seusPlacedReady, seusPlacedReady && localDirectEnabled ? 1 : 0);
        GL20C.glUniform1i(uniforms.seusAreaQuality, ModelLightingQuality.active().uniformValue());
        GL20C.glUniform1i(uniforms.autoQuality, quality.automatic() ? 1 : 0);
        GL20C.glUniform1i(uniforms.manualSamples, quality.areaSamples());
        GL20C.glUniform1f(uniforms.manualResolution, quality.resolutionScale());
        GL20C.glUniform1i(uniforms.globalLocal, ModelLightingSettings.global().localEnabled() ? 1 : 0);
        GL20C.glUniform1i(uniforms.globalGi, ModelLightingSettings.global().giEnabled() ? 1 : 0);
        GL20C.glUniform4fv(uniforms.receiverQuality, receiverQuality);
        GL20C.glUniform1i(uniforms.receiverCount, receiverCount);
        // The world blocker snapshot is shared by held/provider queries even when placed reconstruction is off.
        boolean placed = activeCount > 0 && ModelPlacedLightField.ready();
        GL20C.glUniform1i(uniforms.placedReady, placed ? 1 : 0);
        if (placed) {
            GL20C.glUniform1i(uniforms.placedCount, ModelPlacedLightField.fieldCount());
            GL20C.glUniform4fv(uniforms.placedSources, ModelPlacedLightField.sourceUniforms());
            GL20C.glUniform4fv(uniforms.blockOrigin, ModelPlacedLightField.originUniforms());
            GL20C.glUniform2iv(uniforms.placedRanges, ModelPlacedLightField.rangeUniforms());
        }
        GL20C.glUniform1i(uniforms.dynamicCount, activeCount > 0 ? ModelLocalLightProviders.count() : 0);
        if (activeCount > 0 && ModelLocalLightProviders.count() > 0) GL20C.glUniform4fv(uniforms.dynamicSources, ModelLocalLightProviders.uniforms());
        if (activeCount > 0) {
            GL20C.glUniformMatrix4fv(uniforms.inverse, false, inverses);
            GL20C.glUniform4iv(uniforms.ranges, ranges);
            if (uniforms.boundsMin >= 0) GL20C.glUniform4fv(uniforms.boundsMin, boundsMin);
            if (uniforms.boundsMax >= 0) GL20C.glUniform4fv(uniforms.boundsMax, boundsMax);
            if (uniforms.worldBoundsMin >= 0) GL20C.glUniform4fv(uniforms.worldBoundsMin, worldBoundsMin);
            if (uniforms.worldBoundsMax >= 0) GL20C.glUniform4fv(uniforms.worldBoundsMax, worldBoundsMax);
        }
    }
    public static void deleted(int program) {
        PROGRAMS.remove(program); NATIVE_PROGRAMS.remove(program); SEUS_PROGRAMS.remove(program);
        ModelBvhLightResolution.deleted(program);
        if (lastSeusProgram == program) lastSeusProgram = 0;
    }

    /** Input is the entire visible scene, before camera culling: offscreen walls remain occluders. */
    public static void update(List<ModelRenderInstance> instances, Vec3 camera, boolean irisActive) {
        update(instances, camera, irisActive, instance -> true);
    }
    public static void update(List<ModelRenderInstance> instances, Vec3 camera, boolean irisActive,
                              java.util.function.Predicate<ModelRenderInstance> inFrustum) {
        receiverCount = excludedReceivers = 0;
        lastSeusProgram = 0;
        if (!irisActive || PROGRAMS.isEmpty() || instances.isEmpty()) {
            ModelPlacedLightField.clear();
            ModelLocalLightProviders.resetFrame();
            if (texture != 0 || !keys.isEmpty()) clear();
            count = excluded = opaqueAnyHitInstances = transmissionInstances = 0; updateNanos = uploadBytes = 0;
            status = !adapterFailure.isEmpty() && irisActive ? "ADAPTER_UNAVAILABLE"
                    : !irisActive || PROGRAMS.isEmpty() ? "NO_COMPATIBLE_PROGRAM" : "EMPTY";
            return;
        }
        if (!lightingEnabled) {opaqueAnyHitInstances = transmissionInstances = 0; updateNanos=uploadBytes=0;status="DISABLED";return;}
        long start = System.nanoTime(); uploadBytes = 0; count = 0; excluded = 0; opaqueAnyHitInstances = transmissionInstances = 0;
        try {
            updateReceivers(instances, camera, inFrustum);
            List<ModelLightOcclusionScene> next = new ArrayList<>();
            List<ModelRenderInstance> selected = new ArrayList<>();
            for (var instance : instances) {
                if (!instance.visible() || instance.asset().isClosed()) continue;
                var scene = instance.asset().lightOcclusion(instance.scene());
                if (scene.nodeCount() == 0) { excluded++; continue; }
                if (selected.size() >= MAX_INSTANCES) { excluded++; continue; }
                selected.add(instance);
                if (!next.contains(scene)) next.add(scene);
            }
            if (!sameIdentity(keys, next) || (retryBudget && System.nanoTime() >= retryAfterNanos)) rebuild(next);
            if (texture != 0) upload();
            for (var instance : selected) {
                var scene = instance.asset().lightOcclusion(instance.scene());
                Region region = null;
                for (var candidate : regions) if (candidate.scene == scene) { region = candidate; break; }
                if (region == null) continue;
                Matrix4f relative = instance.transform().relativeTo(camera.x, camera.y, camera.z);
                Matrix4f inverse = new Matrix4f(relative).invert();
                if (!inverse.isFinite()) { excluded++; continue; }
                inverse.get(inverses, count * 16);
                int offset = count * 4;
                ranges[offset] = region.base; ranges[offset + 1] = scene.nodeCount();
                ranges[offset + 2] = region.base + scene.triangleOffset();
                // MASK coverage is tessellated and cut out on the worker; surviving MASK
                // microtriangles are solid blockers. Only BLEND requires transmission traversal.
                long blendTriangles = ((Number) scene.report().getOrDefault("blendSourceTriangles", 0L)).longValue();
                boolean anyHit = blendTriangles == 0;
                ranges[offset + 3] = anyHit ? 1 : 0;
                if (anyHit) opaqueAnyHitInstances++; else transmissionInstances++;
                // The first BVH node is the exact scene-local root AABB. Upload it separately so
                // shaders can reject a segment before fetching even the root node texels.
                float[] texels = scene.texels();
                int root = 0;
                boundsMin[offset] = texels[root]; boundsMin[offset + 1] = texels[root + 1]; boundsMin[offset + 2] = texels[root + 2]; boundsMin[offset + 3] = 0f;
                boundsMax[offset] = texels[root + 4]; boundsMax[offset + 1] = texels[root + 5]; boundsMax[offset + 2] = texels[root + 6]; boundsMax[offset + 3] = 0f;
                worldBounds(relative, boundsMin[offset], boundsMin[offset + 1], boundsMin[offset + 2],
                        boundsMax[offset], boundsMax[offset + 1], boundsMax[offset + 2], worldBoundsMin, worldBoundsMax, offset);
                count++;
            }
            ModelPlacedLightField.update(camera, selected, ready && count > 0 && failure.isEmpty());
            ModelLocalLightProviders.update(instances, camera, ready && count > 0 && failure.isEmpty());
            updateHeldSource(camera);
            status = !failure.isEmpty() ? "DEGRADED" : next.isEmpty() ? "NO_ELIGIBLE_OCCLUDERS" : !ready ? "UPLOADING" : excluded > 0 ? "PARTIAL_UNVERIFIED" : "READY_UNVERIFIED";
        } catch (RuntimeException e) {
            // A failed allocation/upload must not leave cached scene keys pointing at an
            // incomplete atlas forever. Retire its storage and retry after a bounded delay.
            retire(); regions = List.of(); regionIndex = regionFloat = 0;
            failure = "held-light scene upload: " + e; ready = false; count = 0; status = "DEGRADED";
            retryBudget = true; retryAfterNanos = System.nanoTime() + RETRY_NANOS;
        } finally {
            updateNanos = System.nanoTime() - start;
            peakUpdateNanos = Math.max(peakUpdateNanos, updateNanos);
        }
    }
    private static void updateReceivers(List<ModelRenderInstance> instances, Vec3 camera,
                                        java.util.function.Predicate<ModelRenderInstance> inFrustum) {
        for (var instance : instances) {
            if (!instance.visible() || instance.asset().isClosed()) continue;
            if (receiverCount >= MAX_RECEIVERS) { excludedReceivers++; continue; }
            int offset = receiverCount * 4;
            var relative = instance.transform().relativeTo(camera.x, camera.y, camera.z);
            var bounds = instance.asset().sceneBounds(instance.scene());
            double distance = ModelQualityDistance.distance(bounds, relative);
            receiverIds[receiverCount] = instance.id();
            receiverVisible[receiverCount] = inFrustum.test(instance);
            receiverAutoDisabled[receiverCount] = ModelLightingSettings.autoDisabled(receiverVisible[receiverCount], distance);
            var settings = ModelLightingSettings.effective(instance.id());
            int samples = settings.quality().samplesAt(distance);
            float scale = settings.quality().resolutionAt(distance);
            boolean local = settings.localEnabled() && !receiverAutoDisabled[receiverCount] && samples > 0 && scale > 0;
            receiverQuality[offset] = local ? samples : 0;
            receiverQuality[offset + 1] = local ? scale : 0;
            receiverQuality[offset + 2] = settings.giEnabled() && !receiverAutoDisabled[receiverCount] ? 1 : 0;
            receiverQuality[offset + 3] = (float) distance;
            receiverCount++;
        }
    }
    private static boolean sameIdentity(List<ModelLightOcclusionScene> a, List<ModelLightOcclusionScene> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) if (a.get(i) != b.get(i)) return false;
        return true;
    }

    private static void worldBounds(Matrix4f transform, float minX, float minY, float minZ,
                                    float maxX, float maxY, float maxZ,
                                    float[] minimum, float[] maximum, int offset) {
        float outMinX = Float.POSITIVE_INFINITY, outMinY = Float.POSITIVE_INFINITY, outMinZ = Float.POSITIVE_INFINITY;
        float outMaxX = Float.NEGATIVE_INFINITY, outMaxY = Float.NEGATIVE_INFINITY, outMaxZ = Float.NEGATIVE_INFINITY;
        var point = new org.joml.Vector3f();
        for (int corner = 0; corner < 8; corner++) {
            transform.transformPosition((corner & 1) == 0 ? minX : maxX,
                    (corner & 2) == 0 ? minY : maxY,
                    (corner & 4) == 0 ? minZ : maxZ, point);
            outMinX = Math.min(outMinX, point.x); outMinY = Math.min(outMinY, point.y); outMinZ = Math.min(outMinZ, point.z);
            outMaxX = Math.max(outMaxX, point.x); outMaxY = Math.max(outMaxY, point.y); outMaxZ = Math.max(outMaxZ, point.z);
        }
        minimum[offset] = outMinX; minimum[offset + 1] = outMinY; minimum[offset + 2] = outMinZ; minimum[offset + 3] = 0f;
        maximum[offset] = outMaxX; maximum[offset + 1] = outMaxY; maximum[offset + 2] = outMaxZ; maximum[offset + 3] = 0f;
    }
    private static void rebuild(List<ModelLightOcclusionScene> next) {
        retire(); keys = List.copyOf(next); regions = List.of(); count = 0; ready = false;
        regionIndex = regionFloat = 0; failure = ""; retryBudget = false; retryAfterNanos = 0; generation++;
        if (next.isEmpty()) { ready = true; return; }
        long texels = 0;
        List<Region> layout = new ArrayList<>();
        for (var scene : next) {
            // Each region starts on a row, permitting bounded row uploads with no atlas-sized copy.
            texels = (texels + WIDTH - 1) / WIDTH * WIDTH;
            layout.add(new Region(scene, Math.toIntExact(texels)));
            texels += scene.texels().length / 4;
        }
        height = Math.toIntExact((texels + WIDTH - 1) / WIDTH);
        long bytes = (long) WIDTH * height * 16;
        if (bytes > MAX_ATLAS_BYTES || Math.max(WIDTH, height) > GL11C.glGetInteger(GL11C.GL_MAX_TEXTURE_SIZE)) {
            failure = "occlusion atlas exceeds 128MiB or GL texture extent"; return;
        }
        if (!ModelRenderAsset.residency().reserveTransientGpu(bytes)) {
            failure = "occlusion GPU residency budget exhausted; retry after fenced retirement";
            retryBudget = true; retryAfterNanos = System.nanoTime() + RETRY_NANOS; return;
        }
        gpuBytes = bytes;
        long allocationStarted = System.nanoTime();
        int previous = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
        int pbo = GL11C.glGetInteger(GL21C.GL_PIXEL_UNPACK_BUFFER_BINDING);
        try {
            texture = GL11C.glGenTextures();
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, texture);
            GL15C.glBindBuffer(GL21C.GL_PIXEL_UNPACK_BUFFER, 0);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_NEAREST);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_NEAREST);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL12C.GL_TEXTURE_MAX_LEVEL, 0);
            GL11C.glTexImage2D(GL11C.GL_TEXTURE_2D, 0, GL30C.GL_RGBA32F, WIDTH, height, 0, GL11C.GL_RGBA, GL11C.GL_FLOAT, 0L);
            if (GL11C.glGetTexLevelParameteri(GL11C.GL_TEXTURE_2D, 0, GL11C.GL_TEXTURE_WIDTH) != WIDTH
                    || GL11C.glGetTexLevelParameteri(GL11C.GL_TEXTURE_2D, 0, GL11C.GL_TEXTURE_HEIGHT) != height)
                throw new IllegalStateException("Occlusion texture allocation failed");
            regions = List.copyOf(layout);
        } catch (RuntimeException | Error e) {
            retire(); regions = List.of();
            throw e;
        } finally {
            GL15C.glBindBuffer(GL21C.GL_PIXEL_UNPACK_BUFFER, pbo);
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, previous);
            ModelIrisUploadBudget.INSTANCE.recordElapsed(System.nanoTime() - allocationStarted);
        }
    }
    private static void upload() {
        if (ready || regions.isEmpty()) return;
        long started = System.nanoTime();
        int previous = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
        int pbo = GL11C.glGetInteger(GL21C.GL_PIXEL_UNPACK_BUFFER_BINDING);
        int rowLength = GL11C.glGetInteger(GL12C.GL_UNPACK_ROW_LENGTH);
        int swapBytes = GL11C.glGetInteger(GL11C.GL_UNPACK_SWAP_BYTES);
        int skipRows = GL11C.glGetInteger(GL11C.GL_UNPACK_SKIP_ROWS), skipPixels = GL11C.glGetInteger(GL11C.GL_UNPACK_SKIP_PIXELS);
        int chunkRows = (int) (ModelIrisUploadBudget.MAX_OCCLUSION_CHUNK_BYTES / (WIDTH * 16L));
        FloatBuffer buffer = MemoryUtil.memAllocFloat(WIDTH * 4 * chunkRows);
        try {
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, texture); GL15C.glBindBuffer(GL21C.GL_PIXEL_UNPACK_BUFFER, 0);
            GL11C.glPixelStorei(GL11C.GL_UNPACK_SWAP_BYTES, 0);
            GL11C.glPixelStorei(GL12C.GL_UNPACK_ROW_LENGTH, 0); GL11C.glPixelStorei(GL11C.GL_UNPACK_SKIP_ROWS, 0); GL11C.glPixelStorei(GL11C.GL_UNPACK_SKIP_PIXELS, 0);
            while (regionIndex < regions.size() && uploadBytes < UPLOAD_BYTES && System.nanoTime() - started < UPLOAD_NANOS) {
                Region region = regions.get(regionIndex); float[] data = region.scene.texels();
                int rows = Math.min(chunkRows, (data.length - regionFloat + WIDTH * 4 - 1) / (WIDTH * 4));
                long chunkBytes = (long) rows * WIDTH * 16;
                if (!ModelIrisUploadBudget.INSTANCE.tryAcquireOcclusion(chunkBytes)) break;
                long chunkStarted = System.nanoTime();
                int values = Math.min(data.length - regionFloat, rows * WIDTH * 4);
                buffer.clear(); buffer.put(data, regionFloat, values);
                while (buffer.position() < rows * WIDTH * 4) buffer.put(0);
                buffer.flip();
                GL11C.glTexSubImage2D(GL11C.GL_TEXTURE_2D, 0, 0, region.base / WIDTH + regionFloat / (WIDTH * 4), WIDTH, rows, GL11C.GL_RGBA, GL11C.GL_FLOAT, buffer);
                ModelIrisUploadBudget.INSTANCE.recordElapsed(System.nanoTime() - chunkStarted);
                regionFloat += values; uploadBytes += chunkBytes;
                if (regionFloat == data.length) { regionIndex++; regionFloat = 0; }
            }
            totalUploadBytes += uploadBytes; ready = regionIndex == regions.size();
        } finally {
            MemoryUtil.memFree(buffer);
            GL11C.glPixelStorei(GL11C.GL_UNPACK_SWAP_BYTES, swapBytes);
            GL11C.glPixelStorei(GL12C.GL_UNPACK_ROW_LENGTH, rowLength); GL11C.glPixelStorei(GL11C.GL_UNPACK_SKIP_ROWS, skipRows); GL11C.glPixelStorei(GL11C.GL_UNPACK_SKIP_PIXELS, skipPixels);
            GL15C.glBindBuffer(GL21C.GL_PIXEL_UNPACK_BUFFER, pbo); GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, previous);
        }
    }
    private static void retire() {
        int oldTexture = texture; long oldBytes = gpuBytes; texture = 0; gpuBytes = 0;
        if (oldTexture == 0 && oldBytes == 0) return;
        retiredBytes += oldBytes;
        RenderSystem.queueFencedTask(() -> {
            if (oldTexture != 0) GlStateManager._deleteTexture(oldTexture);
            ModelRenderAsset.residency().releaseTransientGpu(oldBytes); retiredBytes -= oldBytes;
        });
    }
    public static void clear() {
        ModelBvhLightResolution.clear();
        ModelPlacedLightField.clear();
        ModelLocalLightProviders.resetFrame();
        retire(); keys = List.of(); regions = List.of(); count = excluded = opaqueAnyHitInstances = transmissionInstances = regionIndex = regionFloat = 0;
        ready = retryBudget = false; retryAfterNanos = 0; updateNanos = uploadBytes = 0; failure = ""; status = "EMPTY";
        receiverCount = excludedReceivers = 0;
    }
    public static Map<String, Object> report() {
        var out = new LinkedHashMap<String, Object>();
        out.put("status", status); out.put("enabled", lightingEnabled); out.put("bvhEnabled", bvhEnabled); out.put("heldEnabled", heldEnabled); out.put("placedEnabled", placedEnabled); out.put("dynamicEnabled", dynamicEnabled); out.put("matchedPrograms", PROGRAMS.size()); out.put("adapterSourceMatchesTotal", ModelPackLightAdapters.matches());
        out.put("primaryGiEnabled", primaryGiEnabled); out.put("secondaryGiEnabled", secondaryGiEnabled);
        out.put("heldOcclusionEnabled", heldOcclusionEnabled); out.put("worldOcclusionEnabled", worldOcclusionEnabled);
        out.put("heldLightEmission", heldSource[3]);
        out.put("seusPlacedDirectReady", SEUS_PROGRAMS.ready(lastSeusProgram));
        out.put("quality", ModelLightingQuality.active().report());
        var receiverQuality = new ArrayList<Map<String, Object>>();
        for (int instance = 0; instance < receiverCount; instance++) {
            float distance = ModelIrisHeldLight.receiverQuality[instance * 4 + 3];
            int offset = instance * 4;
            receiverQuality.add(Map.of("instance", receiverIds[instance], "distance", distance,
                    "visible", receiverVisible[instance], "autoDisabled", receiverAutoDisabled[instance],
                    "overridden", ModelLightingSettings.hasLocalOverride(receiverIds[instance]) || ModelLightingSettings.hasGiOverride(receiverIds[instance]),
                    "localEnabled", ModelIrisHeldLight.receiverQuality[offset] > 0,
                    "giEnabled", ModelIrisHeldLight.receiverQuality[offset + 2] > 0,
                    "samples", (int) ModelIrisHeldLight.receiverQuality[offset],
                    "resolution", ModelIrisHeldLight.receiverQuality[offset + 1]));
        }
        out.put("receiverQuality", receiverQuality);
        var receiverTag = new LinkedHashMap<String, Object>(ModelLightingReceiverTag.report());
        receiverTag.put("decoderPrograms", PROGRAMS.values().stream()
                .filter(uniforms -> uniforms.receiverQuality >= 0 && uniforms.receiverCount >= 0).count());
        out.put("receiverTag", receiverTag);
        var lighting = lightingHudSnapshot();
        out.put("localLight", Map.of("enabled", ModelLightingSettings.global().localEnabled(),
                "activeReceivers", lighting.localActiveReceivers(), "disabledReceivers", receiverCount - lighting.localActiveReceivers()));
        out.put("giOcclusion", Map.of("enabled", ModelLightingSettings.global().giEnabled(),
                "activeReceivers", lighting.giActiveReceivers(), "disabledReceivers", receiverCount - lighting.giActiveReceivers(),
                "sharedProbes", "retained while any global or instance GI receiver is enabled; instance switches preserve transport to other receivers"));
        out.put("receiverPolicy", Map.of("receivers", receiverCount, "localActiveReceivers", lighting.localActiveReceivers(),
                "giActiveReceivers", lighting.giActiveReceivers(), "autoDisabledReceivers", lighting.autoDisabledReceivers(),
                "overrideCount", lighting.overrideCount(), "offscreenDistance", 32,
                "excludedReceivers", excludedReceivers, "occludersPreserved", true, "scope", "SEUS tagged opaque/mask receivers and native forward; other packs inherit global settings"));
        out.put("autoScope", "outside main frustum AND nearest model AABB distance >32 disables local/primary GI receivers; shared secondary GI retains occlusion");
        out.put("resolution", ModelBvhLightResolution.report());
        out.put("placedAreaQualityPrograms", PROGRAMS.values().stream().filter(u -> u.seusAreaQuality >= 0).count());
        out.put("primaryGiPrograms", PROGRAMS.values().stream().filter(u -> u.primaryGiEnabled >= 0).count());
        out.put("secondaryGiPrograms", PROGRAMS.values().stream().filter(u -> u.secondaryGiEnabled >= 0).count());
        out.put("heldOcclusionPrograms", PROGRAMS.values().stream().filter(u -> u.heldOcclusionEnabled >= 0).count());
        out.put("worldOcclusionPrograms", PROGRAMS.values().stream().filter(u -> u.worldOcclusionEnabled >= 0).count());
        out.put("activeInstances", ready ? count : 0); out.put("excludedInstances", excluded);
        out.put("opaqueAnyHitInstances", ready ? opaqueAnyHitInstances : 0);
        out.put("transmissionInstances", ready ? transmissionInstances : 0);
        out.put("rootAabbCoarseCulling", true);
        out.put("worldAabbCoarseCulling", true);
        out.put("sameInvocationExactCacheSlots", 4);
        out.put("sameInvocationExactCache", "receiver/source exact match; invalidated by BVH atlas generation and active instance count; fragment-local only");
        out.put("receiverLocalExactCacheSlots", 8);
        out.put("sourceLocalExactCacheSlots", 8);
        out.put("atlasAddressMode", "fixed_2048_shift_mask");
        out.put("triangleDataLayout", "v0_v1_v2");
        out.put("traversalMode", "opaque_any_hit_stack_near_entry_transmission_escape");
        out.put("bvhStackSlots", ModelLightOcclusionBvh.MAX_TRAVERSAL_STACK);
        out.put("bvhNodeBytes", ModelLightOcclusionBvh.NODE_TEXELS * 16);
        out.put("bvhMaxDepth", keys.stream().mapToInt(scene ->
                ((Number) scene.report().getOrDefault("bvhMaxDepth", 0)).intValue()).max().orElse(0));
        out.put("bvhNodes", keys.stream().mapToLong(ModelLightOcclusionScene::nodeCount).sum());
        out.put("bvhSahSplits", keys.stream().mapToLong(scene ->
                ((Number) scene.report().getOrDefault("bvhSahSplits", 0L)).longValue()).sum());
        out.put("bvhMedianSplits", keys.stream().mapToLong(scene ->
                ((Number) scene.report().getOrDefault("bvhMedianSplits", 0L)).longValue()).sum());
        out.put("bvhBuildSplit", "binned_sah_16_with_median_fallback");
        out.put("bvhLeafTriangles", 8);
        out.put("bvhSahBins", 16);
        out.put("bvhSahThreshold", 9);
        out.put("maxNodeVisits", ModelSundialHeldLight.MAX_NODE_VISITS);
        out.put("queryCounters", "unavailable; shader GPU traversal counters are not instrumented");
        out.put("sharedScenes", keys.size()); out.put("gpuBytes", gpuBytes); out.put("retiringGpuBytes", retiredBytes);
        out.put("cpuUploadPayloadBytes", keys.stream().mapToLong(s -> (long) s.texels().length * 4).sum());
        long uploadedPayloadBytes = (long) regionFloat * Float.BYTES;
        for (int i = 0; i < regionIndex; i++) uploadedPayloadBytes += (long) regions.get(i).scene.texels().length * Float.BYTES;
        long atlasPayloadBytes = regions.stream().mapToLong(r -> (long) r.scene.texels().length * Float.BYTES).sum();
        out.put("uploadReady", ready);
        out.put("uploadedPayloadBytes", uploadedPayloadBytes);
        out.put("pendingUploadBytes", atlasPayloadBytes - uploadedPayloadBytes);
        out.put("uploadBytes", uploadBytes); out.put("totalUploadBytes", totalUploadBytes); out.put("generation", generation);
        out.put("updateNanos", updateNanos); out.put("peakUpdateNanos", peakUpdateNanos); out.put("lastFailure", adapterFailure.isEmpty() ? failure : adapterFailure);
        Object resolutionStatus = ModelBvhLightResolution.report().get("status");
        out.put("additionalDrawCalls", "ACTIVE".equals(resolutionStatus) || "AUTO_SPARSE".equals(resolutionStatus) ? 1 : 0);
        out.put("placedLight", ModelPlacedLightField.report());
        out.put("sourceProviders", ModelLocalLightProviders.report());
        out.put("transparentReceivers", ModelNativeLight.report());
        out.put("scope", "version-guarded pack light adapters; OPAQUE/MASK and scalar BLEND transmission; G-buffer model/terrain/entity and native forward transparent receivers; per-pack effects require visual verification");
        out.put("limits", "8 instances, 128MiB atlas, 2MiB/2ms sliced upload; 2048 node visits per instance/ray, exhaustion is fail-open and not GPU-counted; alpha coverage is bounded tessellation; additional sky occlusion is unsupported (fixed-direction approximation removed); pack sky and sun preserved; placed/provider scope separately reported");
        out.put("gpuTiming", "Iris fullscreen timestamps are reported in shaderGpu; lighting profile isolates module costs by A/B comparison, not in-shader function timing");
        return out;
    }

    public enum HudState { OFF, UNAVAILABLE, EMPTY, UPLOADING, READY, DEGRADED }
    public record HudSnapshot(HudState state, int minSamples, int maxSamples, float minResolution, float maxResolution) {}
    public record LightingHudSnapshot(int localActiveReceivers, int giActiveReceivers, int autoDisabledReceivers,
                                      int overrideCount, int minSamples, int maxSamples,
                                      float minResolution, float maxResolution, int receiverCount) {}
    static boolean anyLocalReceivers() {
        var global = ModelLightingSettings.global();
        if (global.localEnabled() && (global.quality().automatic()
                || global.quality().areaSamples() > 0 && global.quality().resolutionScale() > 0)) return true;
        for (int i = 0; i < receiverCount; i++) if (receiverQuality[i * 4] > 0) return true;
        return false;
    }
    private static boolean anyGiReceivers() {
        if (ModelLightingSettings.global().giEnabled()) return true;
        for (int i = 0; i < receiverCount; i++) if (receiverQuality[i * 4 + 2] > 0) return true;
        return false;
    }
    public static LightingHudSnapshot lightingHudSnapshot() {
        int local = 0, gi = 0, automatic = 0, minimum = 4, maximum = 0;
        float minScale = 1, maxScale = 0;
        for (int i = 0; i < receiverCount; i++) {
            int offset = i * 4;
            if (receiverQuality[offset] > 0) {
                local++;
                minimum = Math.min(minimum, (int) receiverQuality[offset]);
                maximum = Math.max(maximum, (int) receiverQuality[offset]);
                minScale = Math.min(minScale, receiverQuality[offset + 1]);
                maxScale = Math.max(maxScale, receiverQuality[offset + 1]);
            }
            if (receiverQuality[offset + 2] > 0) gi++;
            if (receiverAutoDisabled[i]) automatic++;
        }
        return new LightingHudSnapshot(local, gi, automatic, ModelLightingSettings.overrideCount(),
                local == 0 ? -1 : minimum, local == 0 ? -1 : maximum,
                local == 0 ? -1 : minScale, local == 0 ? -1 : maxScale, receiverCount);
    }

    /** Bounded to the eight current occluder instances; never uploads or queries GL. */
    public static HudSnapshot hudSnapshot() {
        HudState state = !lightingEnabled || !bvhEnabled ? HudState.OFF
                : !failure.isEmpty() || !adapterFailure.isEmpty() || excluded > 0 || excludedReceivers > 0 ? HudState.DEGRADED
                : PROGRAMS.isEmpty() || "NO_COMPATIBLE_PROGRAM".equals(status) ? HudState.UNAVAILABLE
                : !ready && !keys.isEmpty() ? HudState.UPLOADING : count == 0 ? HudState.EMPTY : HudState.READY;
        var lighting = lightingHudSnapshot();
        return new HudSnapshot(state, lighting.minSamples(), lighting.maxSamples(), lighting.minResolution(), lighting.maxResolution());
    }
    private ModelIrisHeldLight() {}
}
