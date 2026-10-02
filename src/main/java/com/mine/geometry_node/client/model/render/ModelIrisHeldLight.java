package com.mine.geometry_node.client.model.render;

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
    private static final int MAX_INSTANCES = 8, WIDTH = 2048;
    private static final long MAX_ATLAS_BYTES = 128L << 20, UPLOAD_BYTES = 2L << 20, UPLOAD_NANOS = 2_000_000;
    private record Uniforms(int count, int inverse, int ranges, int placedReady, int placedCount, int placedSources, int blockOrigin, int placedRanges, int dynamicCount, int dynamicSources, int heldSource) {}
    private record Region(ModelLightOcclusionScene scene, int base) {}
    private static final Map<Integer, Uniforms> PROGRAMS = new HashMap<>();
    private static final Map<Integer, Uniforms> NATIVE_PROGRAMS = new HashMap<>();
    private static final float[] heldSource = new float[4];
    private static Method itemEmission;
    private static boolean itemProviderResolved;
    private static List<ModelLightOcclusionScene> keys = List.of();
    private static List<Region> regions = List.of();
    private static final float[] inverses = new float[MAX_INSTANCES * 16];
    private static final int[] ranges = new int[MAX_INSTANCES * 4];
    private static int texture, height, regionIndex, regionFloat, count, excluded;
    private static long gpuBytes, retiredBytes, uploadBytes, totalUploadBytes, updateNanos, peakUpdateNanos, generation;
    private static boolean ready, retryBudget;
    private static final long RETRY_NANOS = 1_000_000_000L;
    private static long retryAfterNanos;
    private static boolean lightingEnabled=true;
    /** Diagnostic A/B toggle: keep resident resources, disable only custom illumination queries. */
    public static void setLightingEnabled(boolean enabled) {RenderSystem.assertOnRenderThread();lightingEnabled=enabled;}
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
            PROGRAMS.put(program, locations(program));
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
            GL20C.glGetUniformLocation(program,"geometry_node_dynamicSources[0]"),GL20C.glGetUniformLocation(program,"geometry_node_heldSource"));
    }
    public static int nativeTexture() {return texture;}
    public static void uploadNativeUniforms(int program) {upload(NATIVE_PROGRAMS.computeIfAbsent(program,ModelIrisHeldLight::locations));}
    private static void updateHeldSource(Vec3 camera) {
        heldSource[3]=0;
        var mc=net.minecraft.client.Minecraft.getInstance();var player=mc.player;if(player==null)return;
        var eye=player.getEyePosition(mc.getDeltaTracker().getGameTimeDeltaPartialTick(true));
        heldSource[0]=(float)(eye.x-camera.x);heldSource[1]=(float)(eye.y-camera.y);heldSource[2]=(float)(eye.z-camera.z);
        if(!itemProviderResolved) {
            itemProviderResolved=true;
            try {itemEmission=Class.forName("net.irisshaders.iris.api.v0.item.IrisItemLightProvider").getMethod("getLightEmission",
                net.minecraft.world.entity.player.Player.class,net.minecraft.world.item.ItemStack.class);}catch(ReflectiveOperationException ignored) {}
        }
        for(var stack:java.util.List.of(player.getMainHandItem(),player.getOffhandItem())) {
            int value=0;
            if(itemEmission!=null&&itemEmission.getDeclaringClass().isInstance(stack.getItem())) {
                try {value=((Number)itemEmission.invoke(stack.getItem(),player,stack)).intValue();}catch(ReflectiveOperationException ignored) {}
            }else if(stack.getItem() instanceof net.minecraft.world.item.BlockItem block) {
                var state=block.getBlock().defaultBlockState();var properties=stack.get(net.minecraft.core.component.DataComponents.BLOCK_STATE);
                if(properties!=null)state=properties.apply(state);
                value=state.getLightEmission();
            }
            heldSource[3]=Math.max(heldSource[3],Math.clamp(value,0,15));
        }
    }
    /** Called after Iris's own uniforms/samplers, only matched programs perform GL operations. */
    public static void uploadUniforms(int program) {
        Uniforms uniforms = PROGRAMS.get(program);
        if (uniforms == null) return;
        upload(uniforms);
    }
    private static void upload(Uniforms uniforms) {
        GL20C.glUniform4fv(uniforms.heldSource, heldSource);
        int activeCount = ready && lightingEnabled ? count : 0;
        GL20C.glUniform1i(uniforms.count, activeCount);
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
        }
    }
    public static void deleted(int program) { PROGRAMS.remove(program); NATIVE_PROGRAMS.remove(program); }

    /** Input is the entire visible scene, before camera culling: offscreen walls remain occluders. */
    public static void update(List<ModelRenderInstance> instances, Vec3 camera, boolean irisActive) {
        if (!irisActive || PROGRAMS.isEmpty() || instances.isEmpty()) {
            ModelPlacedLightField.clear();
            ModelLocalLightProviders.resetFrame();
            if (texture != 0 || !keys.isEmpty()) clear();
            count = excluded = 0; updateNanos = uploadBytes = 0;
            status = !adapterFailure.isEmpty() && irisActive ? "ADAPTER_UNAVAILABLE"
                    : !irisActive || PROGRAMS.isEmpty() ? "NO_COMPATIBLE_PROGRAM" : "EMPTY";
            return;
        }
        if (!lightingEnabled) {updateNanos=uploadBytes=0;status="DISABLED";return;}
        long start = System.nanoTime(); uploadBytes = 0; count = 0; excluded = 0;
        try {
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
                Matrix4f inverse = instance.transform().relativeTo(camera.x, camera.y, camera.z).invert();
                if (!inverse.isFinite()) { excluded++; continue; }
                inverse.get(inverses, count * 16);
                int offset = count * 4;
                ranges[offset] = region.base; ranges[offset + 1] = scene.nodeCount();
                ranges[offset + 2] = region.base + scene.triangleOffset(); ranges[offset + 3] = 0;
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
    private static boolean sameIdentity(List<ModelLightOcclusionScene> a, List<ModelLightOcclusionScene> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) if (a.get(i) != b.get(i)) return false;
        return true;
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
        FloatBuffer buffer = MemoryUtil.memAllocFloat(WIDTH * 4 * 16);
        try {
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, texture); GL15C.glBindBuffer(GL21C.GL_PIXEL_UNPACK_BUFFER, 0);
            GL11C.glPixelStorei(GL11C.GL_UNPACK_SWAP_BYTES, 0);
            GL11C.glPixelStorei(GL12C.GL_UNPACK_ROW_LENGTH, 0); GL11C.glPixelStorei(GL11C.GL_UNPACK_SKIP_ROWS, 0); GL11C.glPixelStorei(GL11C.GL_UNPACK_SKIP_PIXELS, 0);
            while (regionIndex < regions.size() && uploadBytes < UPLOAD_BYTES && System.nanoTime() - started < UPLOAD_NANOS) {
                Region region = regions.get(regionIndex); float[] data = region.scene.texels();
                int rows = Math.min(16, (data.length - regionFloat + WIDTH * 4 - 1) / (WIDTH * 4));
                long chunkBytes = (long) rows * WIDTH * 16;
                if (!ModelIrisUploadBudget.INSTANCE.tryAcquire(chunkBytes)) break;
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
        ModelPlacedLightField.clear();
        ModelLocalLightProviders.resetFrame();
        retire(); keys = List.of(); regions = List.of(); count = excluded = regionIndex = regionFloat = 0;
        ready = retryBudget = false; retryAfterNanos = 0; updateNanos = uploadBytes = 0; failure = ""; status = "EMPTY";
    }
    public static Map<String, Object> report() {
        var out = new LinkedHashMap<String, Object>();
        out.put("status", status); out.put("enabled", lightingEnabled); out.put("matchedPrograms", PROGRAMS.size()); out.put("adapterSourceMatchesTotal", ModelPackLightAdapters.matches());
        out.put("activeInstances", ready ? count : 0); out.put("excludedInstances", excluded);
        out.put("sharedScenes", keys.size()); out.put("gpuBytes", gpuBytes); out.put("retiringGpuBytes", retiredBytes);
        out.put("cpuUploadPayloadBytes", keys.stream().mapToLong(s -> (long) s.texels().length * 4).sum());
        out.put("uploadBytes", uploadBytes); out.put("totalUploadBytes", totalUploadBytes); out.put("generation", generation);
        out.put("updateNanos", updateNanos); out.put("peakUpdateNanos", peakUpdateNanos); out.put("lastFailure", adapterFailure.isEmpty() ? failure : adapterFailure);
        out.put("additionalDrawCalls", 0);
        out.put("placedLight", ModelPlacedLightField.report());
        out.put("sourceProviders", ModelLocalLightProviders.report());
        out.put("transparentReceivers", ModelNativeLight.report());
        out.put("scope", "version-guarded pack light adapters; OPAQUE/MASK and scalar BLEND transmission; G-buffer model/terrain/entity and native forward transparent receivers; per-pack effects require visual verification");
        out.put("limits", "8 instances, 128MiB atlas, 2MiB/2ms sliced upload; 2048 node visits per instance/ray, exhaustion is fail-open and not GPU-counted; alpha coverage is bounded tessellation; additional sky occlusion is unsupported (fixed-direction approximation removed); pack sky and sun preserved; placed/provider scope separately reported");
        out.put("gpuTiming", "N/A; no host timestamp query. CPU payload included in prepared geometry; atlas included in global GPU ledger, not material texture bytes");
        return out;
    }
    private ModelIrisHeldLight() {}
}
