package com.mine.geometry_node.client.model.render.pipeline;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.lwjgl.opengl.ARBDrawBuffersBlend;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL14C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL40C;

import java.util.Map;
import java.util.WeakHashMap;

/** Opaque and MASK draws must replace surviving pixels even when a pack enables entity alpha blending. */
public final class ModelIrisBlendOverride {
    private static Scope active;
    private static org.lwjgl.opengl.GLCapabilities cachedContext;
    private static int maxDrawBuffers;
    private static boolean[] enabledSnapshot = new boolean[0];
    private static final ModelOpaqueBlendOverride.Driver ENABLES = new ModelOpaqueBlendOverride.Driver() {
        public int drawBuffers() { return maxDrawBuffers; }
        public boolean enabled(int buffer) { return GL30C.glIsEnabledi(GL11C.GL_BLEND, buffer); }
        public void enabled(int buffer, boolean enabled) {
            if (enabled) GL30C.glEnablei(GL11C.GL_BLEND, buffer); else GL30C.glDisablei(GL11C.GL_BLEND, buffer);
        }
    };
    private static long draws, failures;
    private static String lastFailure = "";
    private static String capability = "UNTESTED";
    private static final Map<RenderType, Boolean> RENDER_TYPES = new WeakHashMap<>();
    private static final ModelIndexedBlendOverride.State TRANSLUCENT = new ModelIndexedBlendOverride.State(true,
            GL11C.GL_SRC_ALPHA, GL11C.GL_ONE_MINUS_SRC_ALPHA, GL11C.GL_ONE, GL11C.GL_ONE_MINUS_SRC_ALPHA,
            GL14C.GL_FUNC_ADD, GL14C.GL_FUNC_ADD);

    /** Weak metadata for the actual deferred RenderType draw; register OPAQUE/MASK types. */
    public static void registerRenderType(RenderType type, boolean translucent) {
        RenderSystem.assertOnRenderThread();
        RENDER_TYPES.put(type, translucent);
    }
    public static Scope forRenderType(RenderType type) {
        Boolean translucent = RENDER_TYPES.get(type);
        return translucent == null ? null : scope(translucent);
    }

    public static Scope scope(boolean translucent) {
        RenderSystem.assertOnRenderThread();
        if (active != null) throw new IllegalStateException("Nested model material blend scopes");
        Scope scope = new Scope(translucent);
        active = scope;
        return scope;
    }

    public static final class Scope implements AutoCloseable {
        private final boolean translucent;
        private boolean closed;
        private Scope(boolean translucent) { this.translucent = translucent; }
        @Override public void close() {
            if (closed) return;
            if (active != this) throw new IllegalStateException("Model unlit blend scope closed out of order");
            active = null;
            closed = true;
        }
    }

    /** Actual GL draw hook, after Iris blend overrides. Unsupported capability fails before mutation. */
    public static boolean isActive() { return active != null; }

    public static void draw(Runnable draw) {
        if (active == null) { draw.run(); return; }
        var caps = GL.getCapabilities();
        if (cachedContext != caps) {
            maxDrawBuffers = GL11C.glGetInteger(GL20C.GL_MAX_DRAW_BUFFERS);
            enabledSnapshot = new boolean[maxDrawBuffers];
            cachedContext = caps;
        }
        if (active.translucent && !caps.OpenGL40 && !caps.GL_ARB_draw_buffers_blend) {
            capability = "UNAVAILABLE";
            failures++;
            lastFailure = "Iris material blend adaptation requires OpenGL 4.0 or ARB_draw_buffers_blend";
            throw new IllegalStateException(lastFailure);
        }
        capability = active.translucent ? (caps.OpenGL40 ? "OPENGL_40" : "ARB_DRAW_BUFFERS_BLEND") : "INDEXED_ENABLE_ONLY";
        try {
            if (active.translucent) ModelIndexedBlendOverride.draw(new IndexedDriver(caps.OpenGL40), TRANSLUCENT, draw);
            else ModelOpaqueBlendOverride.draw(ENABLES, enabledSnapshot, draw);
            draws++;
        } catch (RuntimeException | Error error) {
            failures++;
            lastFailure = error.toString();
            throw error;
        }
    }

    private record IndexedDriver(boolean core) implements ModelIndexedBlendOverride.Driver {
        public int drawBuffers() { return maxDrawBuffers; }
        public ModelIndexedBlendOverride.State state(int buffer) {
            return new ModelIndexedBlendOverride.State(GL30C.glIsEnabledi(GL11C.GL_BLEND, buffer),
                    GL30C.glGetIntegeri(GL14C.GL_BLEND_SRC_RGB, buffer), GL30C.glGetIntegeri(GL14C.GL_BLEND_DST_RGB, buffer),
                    GL30C.glGetIntegeri(GL14C.GL_BLEND_SRC_ALPHA, buffer), GL30C.glGetIntegeri(GL14C.GL_BLEND_DST_ALPHA, buffer),
                    GL30C.glGetIntegeri(GL20C.GL_BLEND_EQUATION_RGB, buffer), GL30C.glGetIntegeri(GL20C.GL_BLEND_EQUATION_ALPHA, buffer));
        }
        public void state(int buffer, ModelIndexedBlendOverride.State state) {
            if (core) {
                GL40C.glBlendFuncSeparatei(buffer, state.srcRgb(), state.dstRgb(), state.srcAlpha(), state.dstAlpha());
                GL40C.glBlendEquationSeparatei(buffer, state.equationRgb(), state.equationAlpha());
            } else {
                ARBDrawBuffersBlend.glBlendFuncSeparateiARB(buffer, state.srcRgb(), state.dstRgb(), state.srcAlpha(), state.dstAlpha());
                ARBDrawBuffersBlend.glBlendEquationSeparateiARB(buffer, state.equationRgb(), state.equationAlpha());
            }
            if (state.enabled()) GL30C.glEnablei(GL11C.GL_BLEND, buffer);
            else GL30C.glDisablei(GL11C.GL_BLEND, buffer);
        }
    }

    public static Map<String, Object> report() {
        return Map.of("capability", capability, "draws", draws, "failures", failures, "lastFailure", lastFailure,
                "scope", "OPAQUE/MASK surviving fragments replace every attachment; all per-buffer blend states restored after draw");
    }

    private ModelIrisBlendOverride() {}
}
