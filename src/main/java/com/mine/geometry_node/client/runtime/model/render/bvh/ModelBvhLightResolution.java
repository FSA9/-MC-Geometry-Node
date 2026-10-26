package com.mine.geometry_node.client.runtime.model.render.bvh;

import com.mine.geometry_node.client.runtime.model.render.diagnostic.ModelShaderGpuTimings;
import com.mine.geometry_node.client.runtime.model.render.geometry.ModelRenderAsset;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.*;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.IntSupplier;

/** A same-frame local-light prepass; the original composite still draws at its original size. */
public final class ModelBvhLightResolution {
    private record Interface(int pass, int size, int sampler, int count) {}
    private static final Map<Integer, Interface> PROGRAMS = new HashMap<>();
    private static final long MAX_BYTES = 256L << 20;
    private static int texture, framebuffer, width, height, fullWidth, fullHeight;
    private static long gpuBytes, retiringGpuBytes, prepassDraws, fallbackDraws, retryAfter;
    private static float effectiveScale = 1;
    private static String status = "NO_COMPATIBLE_PROGRAM", failure = "";

    public static void registerProgram(Object builder, int program) {
        if (PROGRAMS.containsKey(program)) return;
        int pass = GL20C.glGetUniformLocation(program, "bvhLocalPass");
        if (pass < 0) return;
        try {
            Method add = Arrays.stream(builder.getClass().getMethods()).filter(method ->
                    method.getName().equals("addDynamicSampler") && method.getParameterCount() == 3
                            && method.getParameterTypes()[0] == IntSupplier.class).findFirst().orElseThrow();
            if (!(boolean) add.invoke(builder, (IntSupplier) () -> texture, null, new String[]{"bvhLocalLight"}))
                throw new IllegalStateException("Local-light sampler allocation failed");
            int size = GL20C.glGetUniformLocation(program, "bvhLocalSize");
            int sampler = GL20C.glGetUniformLocation(program, "bvhLocalLight");
            int count = GL20C.glGetUniformLocation(program, "geometry_node_lightCount");
            if (size < 0 || sampler < 0 || count < 0) throw new IllegalStateException("Incomplete local-light interface");
            PROGRAMS.put(program, new Interface(pass, size, sampler, count));
            status = "WAITING_FOR_DRAW";
            failure = "";
        } catch (ReflectiveOperationException | RuntimeException error) {
            status = "UNSUPPORTED";
            failure = error.toString();
        }
    }

    /** Called at the actual Iris draw, after all pack uniforms, samplers and element buffers are set. */
    public static void draw(String passName, Runnable draw) {
        if (com.mine.geometry_node.client.runtime.light.render.NodeLightRenderer.inPrepass()) {draw.run();return;}
        // A fixed sparse target supports different qualities in the same frame without
        // reallocating storage or lowering another instance's requested resolution.
        boolean reduced = ModelIrisHeldLight.hasReducedAutoReceivers();
        int program = GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM);
        Interface uniforms = PROGRAMS.get(program);
        if (uniforms == null || !reduced) {
            if (uniforms != null) {
                GL20C.glUniform1i(uniforms.pass, 0);
                effectiveScale = 1;
                status = "NATIVE_RESOLUTION";
            }
            timedDraw(passName, draw);
            return;
        }
        if (GL20C.glGetUniformi(program, uniforms.count) <= 0) {
            GL20C.glUniform1i(uniforms.pass, 0);
            effectiveScale = 1;
            status = "NO_ACTIVE_SCENE";
            timedDraw(passName, draw);
            return;
        }
        int[] viewport = new int[4];
        GL11C.glGetIntegerv(GL11C.GL_VIEWPORT, viewport);
        fullWidth = viewport[2]; fullHeight = viewport[3];
        int unit = GL20C.glGetUniformi(program, uniforms.sampler);
        int activeTexture = GL11C.glGetInteger(GL13C.GL_ACTIVE_TEXTURE);
        GL13C.glActiveTexture(GL13C.GL_TEXTURE0 + unit);
        int oldTexture = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
        int oldSampler = GL30C.glGetIntegeri(GL33C.GL_SAMPLER_BINDING, unit);
        boolean resolved = false;
        try {
            if (System.nanoTime() < retryAfter) throw new IllegalStateException("Waiting to retry local-light allocation");
            // Auto uses a sparse lattice in fixed storage. Camera movement never reallocates it.
            ensureSize(fullWidth, fullHeight);
            RasterState raster = new RasterState(viewport);
            try {
                // No attached output may also be bound as an input, including an untaken shader branch.
                GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, 0);
                GL33C.glBindSampler(unit, 0);
                GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, framebuffer);
                GL11C.glViewport(0, 0, width, height);
                GL30C.glDisablei(GL11C.GL_BLEND, 0);
                GL30C.glColorMaski(0, true, true, true, true);
                GL11C.glDisable(GL11C.GL_SCISSOR_TEST);
                GL11C.glDisable(GL11C.GL_DEPTH_TEST);
                GL11C.glDisable(GL11C.GL_STENCIL_TEST);
                GL20C.glUniform2f(uniforms.size, width, height);
                GL20C.glUniform1i(uniforms.pass, 1);
                timedDraw("bvh_local", draw);
                prepassDraws++;
            } finally {
                raster.restore();
                GL20C.glUniform1i(uniforms.pass, 0);
            }
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, texture);
            GL33C.glBindSampler(unit, 0);
            GL20C.glUniform1i(uniforms.pass, 2);
            effectiveScale = 1;
            status = "AUTO_SPARSE";
            failure = "";
            resolved = true;
        } catch (RuntimeException error) {
            GL20C.glUniform1i(uniforms.pass, 0);
            effectiveScale = 1;
            status = "FALLBACK_NATIVE_RESOLUTION";
            if (System.nanoTime() >= retryAfter) {
                failure = error.toString();
                retryAfter = System.nanoTime() + 1_000_000_000L;
            }
            fallbackDraws++;
        }
        try {
            GL13C.glActiveTexture(activeTexture);
            timedDraw(passName, draw);
        } finally {
            if (resolved) GL20C.glUniform1i(uniforms.pass, 0);
            GL13C.glActiveTexture(GL13C.GL_TEXTURE0 + unit);
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, oldTexture);
            GL33C.glBindSampler(unit, oldSampler);
            GL13C.glActiveTexture(activeTexture);
        }
    }

    private static void ensureSize(int requestedWidth, int requestedHeight) {
        if (texture != 0 && width == requestedWidth && height == requestedHeight) return;
        retire();
        long bytes = Math.multiplyExact((long) requestedWidth * requestedHeight, 16);
        if (bytes > MAX_BYTES || Math.max(requestedWidth, requestedHeight) > GL11C.glGetInteger(GL11C.GL_MAX_TEXTURE_SIZE))
            throw new IllegalStateException("Local-light texture exceeds extent or 256 MiB limit");
        if (!ModelRenderAsset.residency().reserveTransientGpu(bytes))
            throw new IllegalStateException("Local-light GPU residency budget exhausted");
        gpuBytes = bytes;
        int previous = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
        int previousFramebuffer = GL11C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING);
        int unpackBuffer = GL11C.glGetInteger(GL21C.GL_PIXEL_UNPACK_BUFFER_BINDING);
        try {
            texture = GL11C.glGenTextures();
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, texture);
            GL15C.glBindBuffer(GL21C.GL_PIXEL_UNPACK_BUFFER, 0);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_NEAREST);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_NEAREST);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL12C.GL_TEXTURE_MAX_LEVEL, 0);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_S, GL12C.GL_CLAMP_TO_EDGE);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_T, GL12C.GL_CLAMP_TO_EDGE);
            GL11C.glTexImage2D(GL11C.GL_TEXTURE_2D, 0, GL30C.GL_RGBA32F, requestedWidth, requestedHeight,
                    0, GL11C.GL_RGBA, GL11C.GL_FLOAT, 0L);
            framebuffer = GL30C.glGenFramebuffers();
            GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, framebuffer);
            GL30C.glFramebufferTexture2D(GL30C.GL_DRAW_FRAMEBUFFER, GL30C.GL_COLOR_ATTACHMENT0,
                    GL11C.GL_TEXTURE_2D, texture, 0);
            GL11C.glDrawBuffer(GL30C.GL_COLOR_ATTACHMENT0);
            if (GL30C.glCheckFramebufferStatus(GL30C.GL_DRAW_FRAMEBUFFER) != GL30C.GL_FRAMEBUFFER_COMPLETE)
                throw new IllegalStateException("Local-light framebuffer is incomplete");
            width = requestedWidth; height = requestedHeight;
        } catch (RuntimeException | Error error) {
            retire();
            throw error;
        } finally {
            GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, previousFramebuffer);
            GL15C.glBindBuffer(GL21C.GL_PIXEL_UNPACK_BUFFER, unpackBuffer);
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, previous);
        }
    }

    private static void timedDraw(String passName, Runnable draw) {
        int token = ModelShaderGpuTimings.begin(passName);
        try { draw.run(); }
        finally { ModelShaderGpuTimings.end(token); }
    }

    // Raw GL changes are fully restored before returning to Iris, including its cached state values.
    private static final class RasterState {
        final int framebuffer = GL11C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING);
        final boolean blend = GL30C.glIsEnabledi(GL11C.GL_BLEND, 0);
        final boolean scissor = GL11C.glIsEnabled(GL11C.GL_SCISSOR_TEST);
        final boolean depth = GL11C.glIsEnabled(GL11C.GL_DEPTH_TEST);
        final boolean stencil = GL11C.glIsEnabled(GL11C.GL_STENCIL_TEST);
        final byte[] colorMask = new byte[4];
        final int[] viewport;
        RasterState(int[] viewport) {
            this.viewport = viewport;
            try (var stack = org.lwjgl.system.MemoryStack.stackPush()) {
                var mask = stack.malloc(4);
                GL30C.glGetBooleani_v(GL11C.GL_COLOR_WRITEMASK, 0, mask);
                mask.get(colorMask);
            }
        }
        void restore() {
            GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, framebuffer);
            GL11C.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            if (blend) GL30C.glEnablei(GL11C.GL_BLEND, 0); else GL30C.glDisablei(GL11C.GL_BLEND, 0);
            GL30C.glColorMaski(0, colorMask[0] != 0, colorMask[1] != 0, colorMask[2] != 0, colorMask[3] != 0);
            restoreEnable(GL11C.GL_SCISSOR_TEST, scissor);
            restoreEnable(GL11C.GL_DEPTH_TEST, depth);
            restoreEnable(GL11C.GL_STENCIL_TEST, stencil);
        }
        private static void restoreEnable(int capability, boolean enabled) {
            if (enabled) GL11C.glEnable(capability); else GL11C.glDisable(capability);
        }
    }

    public static void settingsChanged() {
        RenderSystem.assertOnRenderThread();
        // The receiver lattice changes in uniforms; its fixed-size storage remains reusable.
        retryAfter = 0;
        effectiveScale = ModelLightingQuality.active().resolutionAt(0) == 0 ? 0 : 1;
        status = effectiveScale == 0 ? "DISABLED" : "WAITING_FOR_DRAW";
        failure = "";
    }

    public static void deleted(int program) {
        PROGRAMS.remove(program);
        if (PROGRAMS.isEmpty()) clear();
    }

    private static void retire() {
        int oldTexture = texture, oldFramebuffer = framebuffer;
        long bytes = gpuBytes;
        texture = framebuffer = width = height = 0;
        gpuBytes = 0;
        if (oldTexture == 0 && oldFramebuffer == 0 && bytes == 0) return;
        retiringGpuBytes += bytes;
        RenderSystem.queueFencedTask(() -> {
            if (oldFramebuffer != 0) GL30C.glDeleteFramebuffers(oldFramebuffer);
            if (oldTexture != 0) GlStateManager._deleteTexture(oldTexture);
            ModelRenderAsset.residency().releaseTransientGpu(bytes);
            retiringGpuBytes -= bytes;
        });
    }

    public static void clear() {
        retire();
        effectiveScale = 1;
        fullWidth = fullHeight = 0;
        retryAfter = 0;
        status = "NO_ACTIVE_DRAW";
        failure = "";
    }

    public static Map<String, Object> report() {
        Map<String, Object> out = new LinkedHashMap<>();
        boolean automatic = ModelLightingQuality.active().automatic();
        boolean perReceiver = automatic || !ModelLightingSettings.overridesSuspended() && ModelLightingSettings.overrideCount() > 0;
        float requested = ModelLightingQuality.active().resolutionAt(0);
        out.put("requestedScale", perReceiver ? "PER_RECEIVER" : requested);
        out.put("effectiveScale", perReceiver ? "PER_RECEIVER" : requested == 0 ? 0 : "AUTO_SPARSE".equals(status) ? requested : effectiveScale);
        out.put("allocationScale", effectiveScale);
        if (perReceiver) {
            out.put("autoMinScale", ModelIrisHeldLight.autoMinimumScale());
            out.put("autoMaxScale", ModelIrisHeldLight.autoMaximumScale());
        }
        out.put("status", !ModelIrisHeldLight.anyLocalReceivers() ? "DISABLED" : status);
        out.put("scope", "held_and_placed_direct");
        out.put("automatic", automatic);
        out.put("autoResolutionScope", "tagged model-instance receivers; outside world surfaces retain full spatial resolution in auto mode");
        out.put("compatiblePrograms", PROGRAMS.size());
        out.put("fullWidth", fullWidth); out.put("fullHeight", fullHeight);
        out.put("width", width); out.put("height", height);
        out.put("gpuBytes", gpuBytes); out.put("retiringGpuBytes", retiringGpuBytes);
        out.put("prepassDraws", prepassDraws); out.put("fallbackDraws", fallbackDraws);
        out.put("failure", failure);
        out.put("sunShadowAffected", false);
        out.put("giAffected", false);
        return out;
    }

    public enum HudState { OFF, IDLE, UNAVAILABLE, FULL, REDUCED, SPARSE, FALLBACK }
    public static HudState hudState() {
        if (!ModelIrisHeldLight.anyLocalReceivers()) return HudState.OFF;
        return switch (status) {
            case "NATIVE_RESOLUTION" -> HudState.FULL;
            case "AUTO_SPARSE" -> HudState.SPARSE;
            case "ACTIVE" -> HudState.REDUCED;
            case "FALLBACK_NATIVE_RESOLUTION" -> HudState.FALLBACK;
            case "NO_COMPATIBLE_PROGRAM", "UNSUPPORTED" -> HudState.UNAVAILABLE;
            default -> HudState.IDLE;
        };
    }

    private ModelBvhLightResolution() {}
}
