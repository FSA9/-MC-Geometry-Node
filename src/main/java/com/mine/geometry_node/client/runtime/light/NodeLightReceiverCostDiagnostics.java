package com.mine.geometry_node.client.runtime.light;

import com.mine.geometry_node.client.model.render.diagnostic.ModelShaderGpuTimings;
import com.mine.geometry_node.client.model.render.geometry.ModelRenderAsset;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.Map;
import org.lwjgl.opengl.*;

/** Paired receiver timings need a real output; masked framebuffer writes can eliminate shader work. */
final class NodeLightReceiverCostDiagnostics {
    private static int texture, framebuffer, width, height;
    private static long bytes, pairs;
    private static String status = "NOT_SAMPLED", failure = "";

    private NodeLightReceiverCostDiagnostics() {}

    static void reset() { release(); pairs = 0; status = "NOT_SAMPLED"; failure = ""; }

    // Invoked inside the pixel diagnostic guard: nested render passes and depth/stencil writes are disabled.
    static void draw(int location, int w, int h, Runnable draw) {
        int originalFbo = GL11C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING);
        int[] viewport = new int[4]; GL11C.glGetIntegerv(GL11C.GL_VIEWPORT, viewport);
        if (viewport[0] != 0 || viewport[1] != 0) { status = "SKIPPED_VIEWPORT_ORIGIN"; return; }
        boolean blend = GL30C.glIsEnabledi(GL11C.GL_BLEND, 0);
        try {
            ensure(w, h);
            GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, framebuffer);
            GL11C.glViewport(0, 0, w, h);
            GL30C.glDisablei(GL11C.GL_BLEND, 0);
            GL30C.glColorMaski(0, true, true, true, true);
            // Warm the identical input textures before the adjacent reference/comparison pairs.
            GL20C.glUniform1i(location, 0); draw.run();
            int[] modes = {16, 32, 64};
            String[] names = {"integration", "volume", "source"};
            for (int i = 0; i < modes.length; i++) {
                // Drivers can specialize a uniform-controlled path on its first draw.
                // Warm each comparison too, then keep its measured reference adjacent.
                GL20C.glUniform1i(location, modes[i]); draw.run();
                GL20C.glUniform1i(location, 0);
                timed("node_light_probe_receiver_" + names[i] + "_reference", draw);
                GL20C.glUniform1i(location, modes[i]);
                timed("node_light_probe_receiver_" + names[i] + "_disabled", draw);
                pairs++;
            }
            status = "SAMPLED";
        } catch (RuntimeException error) {
            status = "FAILED"; failure = error.toString();
        } finally {
            GL20C.glUniform1i(location, 0);
            GL30C.glColorMaski(0, false, false, false, false);
            if (blend) GL30C.glEnablei(GL11C.GL_BLEND, 0); else GL30C.glDisablei(GL11C.GL_BLEND, 0);
            GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, originalFbo);
            GL11C.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        }
    }

    private static void timed(String name, Runnable draw) {
        int timer = ModelShaderGpuTimings.begin(name);
        try { draw.run(); } finally { ModelShaderGpuTimings.end(timer); }
    }

    private static void ensure(int w, int h) {
        if (texture != 0 && width == w && height == h) return;
        long required = (long) w * h * 8;
        int limit = GL11C.glGetInteger(GL11C.GL_MAX_TEXTURE_SIZE);
        if (w < 1 || h < 1 || w > limit || h > limit || required > 64L * 1024 * 1024)
            throw new IllegalStateException("Receiver diagnostic target exceeds 64 MiB or texture limit");
        release();
        if (!ModelRenderAsset.residency().reserveTransientGpu(required))
            throw new IllegalStateException("Receiver diagnostic GPU residency budget unavailable");
        bytes = required;
        int oldTexture = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
        int oldUnpack = GL11C.glGetInteger(GL21C.GL_PIXEL_UNPACK_BUFFER_BINDING);
        int oldFbo = GL11C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING);
        try {
            GL15C.glBindBuffer(GL21C.GL_PIXEL_UNPACK_BUFFER, 0);
            texture = GL11C.glGenTextures(); framebuffer = GL30C.glGenFramebuffers();
            if (texture == 0 || framebuffer == 0) throw new IllegalStateException("Cannot allocate receiver diagnostic target");
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, texture);
            GL11C.glTexImage2D(GL11C.GL_TEXTURE_2D, 0, GL30C.GL_RGBA16F, w, h, 0, GL11C.GL_RGBA, GL11C.GL_FLOAT, 0L);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_NEAREST);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_NEAREST);
            GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, framebuffer);
            GL30C.glFramebufferTexture2D(GL30C.GL_DRAW_FRAMEBUFFER, GL30C.GL_COLOR_ATTACHMENT0, GL11C.GL_TEXTURE_2D, texture, 0);
            GL20C.glDrawBuffers(GL30C.GL_COLOR_ATTACHMENT0);
            if (GL30C.glCheckFramebufferStatus(GL30C.GL_DRAW_FRAMEBUFFER) != GL30C.GL_FRAMEBUFFER_COMPLETE)
                throw new IllegalStateException("Incomplete receiver diagnostic framebuffer");
            width = w; height = h;
        } catch (RuntimeException error) { release(); throw error; }
        finally {
            GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, oldFbo);
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, oldTexture);
            GL15C.glBindBuffer(GL21C.GL_PIXEL_UNPACK_BUFFER, oldUnpack);
        }
    }

    static void release() {
        int oldTexture = texture, oldFbo = framebuffer; long retired = bytes;
        texture = framebuffer = width = height = 0; bytes = 0;
        if (retired == 0) return;
        RenderSystem.queueFencedTask(() -> {
            if (oldTexture != 0) GL11C.glDeleteTextures(oldTexture);
            if (oldFbo != 0) GL30C.glDeleteFramebuffers(oldFbo);
            ModelRenderAsset.residency().releaseTransientGpu(retired);
        });
    }

    static Map<String, Object> report() {
        return Map.of("status", status, "failure", failure, "submittedPairs", pairs, "gpuBytes", bytes,
                "scope", "Profile-only adjacent reference/disabled receiver draws into an isolated RGBA16F target, after warming both normal and disabled modes. No writes to the scene or lighting atlas. Compare paired timings, not absolute time against the original blended receiver. integration skips full-resolution marches; volume skips reconstruction and marches; source skips visible-emitter evaluation. Differences include GPU scheduling effects and are not additive.");
    }
}
