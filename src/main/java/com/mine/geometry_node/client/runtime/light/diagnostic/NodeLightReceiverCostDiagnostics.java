package com.mine.geometry_node.client.runtime.light.diagnostic;

import com.mine.geometry_node.client.runtime.model.render.diagnostic.ModelShaderGpuTimings;
import com.mine.geometry_node.client.runtime.model.render.geometry.ModelRenderAsset;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.Map;
import org.lwjgl.opengl.*;

/** Paired receiver timings need a real output; masked framebuffer writes can eliminate shader work. */
final class NodeLightReceiverCostDiagnostics {
    private static int[] textures = new int[0];
    private static int framebuffer, width, height, attachmentMask;
    private static long bytes, pairs;
    private static String status = "NOT_SAMPLED", failure = "";

    private NodeLightReceiverCostDiagnostics() {}

    static void reset() { release(); pairs = 0; status = "NOT_SAMPLED"; failure = ""; }

    // Invoked inside the pixel diagnostic guard: nested render passes and depth/stencil writes are disabled.
    static void draw(int location, int w, int h, Runnable draw) {
        int originalFbo = GL11C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING);
        int[] viewport = new int[4]; GL11C.glGetIntegerv(GL11C.GL_VIEWPORT, viewport);
        if (viewport[0] != 0 || viewport[1] != 0) { status = "SKIPPED_VIEWPORT_ORIGIN"; return; }
        int slots = GL11C.glGetInteger(GL20C.GL_MAX_DRAW_BUFFERS), activeMask = 0;
        int[] targets = new int[slots];
        boolean[] blend = new boolean[slots];
        for (int i = 0; i < slots; i++) {
            blend[i] = GL30C.glIsEnabledi(GL11C.GL_BLEND, i);
            if (GL11C.glGetInteger(GL20C.GL_DRAW_BUFFER0 + i) != GL11C.GL_NONE) {
                targets[i] = GL30C.GL_COLOR_ATTACHMENT0 + i;
                activeMask |= 1 << i;
            }
        }
        if (activeMask == 0) { status = "SKIPPED_NO_COLOR_OUTPUTS"; return; }
        try {
            ensure(w, h, activeMask, slots);
            GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, framebuffer);
            GL20C.glDrawBuffers(targets);
            GL11C.glViewport(0, 0, w, h);
            // Packs can write lighting to location 1 or higher. Every enabled
            // output needs independent storage so the driver retains its work.
            for (int i = 0; i < slots; i++) if (targets[i] != GL11C.GL_NONE) {
                GL30C.glDisablei(GL11C.GL_BLEND, i);
                GL30C.glColorMaski(i, true, true, true, true);
            }
            // Warm the identical input textures before the adjacent reference/comparison pairs.
            GL20C.glUniform1i(location, 0); draw.run();
            int program = GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM);
            boolean surface = GL20C.glGetUniformLocation(program, "nodeSurfaceRects[0]") >= 0;
            boolean volume = GL20C.glGetUniformLocation(program, "nodeFroxelReady") >= 0;
            int[] modes = surface ? (volume ? new int[]{32, 64, 128, 256} : new int[]{128, 256})
                    : new int[]{32, 64};
            for (int i = 0; i < modes.length; i++) {
                String name = switch (modes[i]) {
                    case 128 -> "surface";
                    case 256 -> "surface_fallback";
                    case 64 -> "source";
                    default -> "volume";
                };
                // Drivers can specialize a uniform-controlled path on its first draw.
                // Warm each comparison too, then keep its measured reference adjacent.
                GL20C.glUniform1i(location, modes[i]); draw.run();
                GL20C.glUniform1i(location, 0);
                timed("node_light_probe_receiver_" + name + "_reference", draw);
                GL20C.glUniform1i(location, modes[i]);
                timed("node_light_probe_receiver_" + name + "_disabled", draw);
                pairs++;
            }
            status = "SAMPLED";
        } catch (RuntimeException error) {
            status = "FAILED"; failure = error.toString();
        } finally {
            GL20C.glUniform1i(location, 0);
            for (int i = 0; i < slots; i++) if (targets[i] != GL11C.GL_NONE) {
                GL30C.glColorMaski(i, false, false, false, false);
                if (blend[i]) GL30C.glEnablei(GL11C.GL_BLEND, i); else GL30C.glDisablei(GL11C.GL_BLEND, i);
            }
            GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, originalFbo);
            GL11C.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        }
    }

    private static void timed(String name, Runnable draw) {
        int timer = ModelShaderGpuTimings.begin(name);
        try { draw.run(); } finally { ModelShaderGpuTimings.end(timer); }
    }

    private static void ensure(int w, int h, int requestedMask, int slots) {
        if (framebuffer != 0 && width == w && height == h && (attachmentMask & requestedMask) == requestedMask) return;
        int mask = width == w && height == h ? attachmentMask | requestedMask : requestedMask;
        long required = (long) w * h * 8 * Integer.bitCount(mask);
        int limit = GL11C.glGetInteger(GL11C.GL_MAX_TEXTURE_SIZE);
        if (w < 1 || h < 1 || w > limit || h > limit || required > 128L * 1024 * 1024)
            throw new IllegalStateException("Receiver diagnostic targets exceed 128 MiB or texture limit");
        release();
        if (!ModelRenderAsset.residency().reserveTransientGpu(required))
            throw new IllegalStateException("Receiver diagnostic GPU residency budget unavailable");
        bytes = required;
        int oldTexture = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
        int oldUnpack = GL11C.glGetInteger(GL21C.GL_PIXEL_UNPACK_BUFFER_BINDING);
        int oldFbo = GL11C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING);
        int oldReadFbo = GL11C.glGetInteger(GL30C.GL_READ_FRAMEBUFFER_BINDING);
        try {
            GL15C.glBindBuffer(GL21C.GL_PIXEL_UNPACK_BUFFER, 0);
            textures = new int[slots]; framebuffer = GL30C.glGenFramebuffers();
            if (framebuffer == 0) throw new IllegalStateException("Cannot allocate receiver diagnostic framebuffer");
            GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, framebuffer);
            GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, framebuffer);
            GL11C.glReadBuffer(GL11C.GL_NONE);
            int[] targets = new int[slots];
            for (int i = 0; i < slots; i++) if ((mask & (1 << i)) != 0) {
                textures[i] = GL11C.glGenTextures();
                if (textures[i] == 0) throw new IllegalStateException("Cannot allocate receiver diagnostic target");
                GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, textures[i]);
                GL11C.glTexImage2D(GL11C.GL_TEXTURE_2D, 0, GL30C.GL_RGBA16F, w, h, 0, GL11C.GL_RGBA, GL11C.GL_FLOAT, 0L);
                GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_NEAREST);
                GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_NEAREST);
                targets[i] = GL30C.GL_COLOR_ATTACHMENT0 + i;
                GL30C.glFramebufferTexture2D(GL30C.GL_DRAW_FRAMEBUFFER, targets[i], GL11C.GL_TEXTURE_2D, textures[i], 0);
            }
            GL20C.glDrawBuffers(targets);
            if (GL30C.glCheckFramebufferStatus(GL30C.GL_DRAW_FRAMEBUFFER) != GL30C.GL_FRAMEBUFFER_COMPLETE)
                throw new IllegalStateException("Incomplete receiver diagnostic framebuffer");
            width = w; height = h; attachmentMask = mask;
        } catch (RuntimeException error) { release(); throw error; }
        finally {
            GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, oldFbo);
            GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, oldReadFbo);
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, oldTexture);
            GL15C.glBindBuffer(GL21C.GL_PIXEL_UNPACK_BUFFER, oldUnpack);
        }
    }

    static void release() {
        int[] oldTextures = textures; int oldFbo = framebuffer; long retired = bytes;
        textures = new int[0]; framebuffer = width = height = attachmentMask = 0; bytes = 0;
        if (retired == 0) return;
        RenderSystem.queueFencedTask(() -> {
            for (int texture : oldTextures) if (texture != 0) GL11C.glDeleteTextures(texture);
            if (oldFbo != 0) GL30C.glDeleteFramebuffers(oldFbo);
            ModelRenderAsset.residency().releaseTransientGpu(retired);
        });
    }

    static Map<String, Object> report() {
        return Map.of("status", status, "failure", failure, "submittedPairs", pairs, "gpuBytes", bytes,
                "allocatedOutputSlots", Integer.bitCount(attachmentMask),
                "scope", "Profile-only adjacent reference/disabled receiver draws into independent RGBA16F targets for every enabled framebuffer output, after warming both normal and disabled modes. No writes to the scene or lighting atlas. Compare paired timings, not absolute time against the original blended receiver. volume skips froxel interval lookup and composition; source skips visible-emitter evaluation; surface skips all receiver surface lighting; surface_fallback skips only direct reevaluation after surface-atlas reconstruction rejection. Surface prepasses, froxel injection, integration and history happen before the receiver and are not removed by these probes. Legacy full-resolution march mode 16 is not applicable. Differences include GPU scheduling effects and are not additive.");
    }
}
