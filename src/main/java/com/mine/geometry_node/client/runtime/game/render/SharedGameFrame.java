package com.mine.geometry_node.client.runtime.game.render;

import com.mine.geometry_node.client.runtime.game.diagnostic.GameViewTiming;

import com.mine.geometry_node.client.runtime.game.host.ViewportHostPolicy;
import com.mine.geometry_node.mixin.game.hud.GameViewGuiRendererAccessor;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.state.gui.GuiRenderState;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/** One scheduled world frame: shared player+HUD, or one independent editor camera.
 * GPU operations and GUI extraction run on Minecraft's render thread. Only visibility
 * and the small published status are accessed from ModernUI's UI thread.
 */
public final class SharedGameFrame {
    private static final Set<Object> CONSUMERS = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final GuiRenderState HUD = new GuiRenderState();
    // GuiRenderer rotates every vertex buffer after each submission. Sharing its
    // three-slot rings between HUD and editor would rotate them twice per frame,
    // forcing earlier GPU fence waits. Each submission keeps its own buffer rings;
    // item atlases and picture-in-picture caches still belong to the same renderer.
    private static final Map<VertexFormat, MappableRingBuffer> HUD_VERTEX_BUFFERS = new Object2ObjectOpenHashMap<>();
    private static final ViewportFrameSurface GAME = new ViewportFrameSurface();
    private static boolean editorFrame;
    private static boolean opaqueEditorFrame;
    private static int editorBackground;
    private static boolean consumersThisFrame;
    private static boolean captureThisFrame;
    private static GuiRenderer hudPassRenderer;
    private SharedGameFrame() {}

    /** Borrow the runtime-owned game publication; null until the first capture. Consumers only read/retain it. */
    public static ViewportFramePublication framePublication() { return GAME.publication(); }

    public static synchronized void setVisible(Object owner, boolean visible) {
        if (visible) CONSUMERS.add(owner);
        else CONSUMERS.remove(owner);
    }

    static synchronized boolean hasConsumers() {
        return !CONSUMERS.isEmpty();
    }

    static synchronized boolean containsConsumer(Object owner) { return CONSUMERS.contains(owner); }
    public static boolean capturesGameHud() { return captureThisFrame && !EditViewportRuntime.isRendering(); }

    /** The HUD and editor are two submissions in one frame. Renderer caches must
     * retire unused entries only after both have marked their resources as used.
     */
    public static boolean deferGuiCacheCleanup(GuiRenderer renderer) {
        return hudPassRenderer == renderer;
    }

    /** Freeze the render policy before extraction. A UI-thread detach/attach between
     * extraction and rendering must not suppress the world for a frame being captured.
     * The host supplies its root color; transparent editor roots keep the world.
     */
    public static void beginFrame() {
        Minecraft minecraft = Minecraft.getInstance();
        beginFrame(ViewportHostPolicy.snapshot(minecraft.screen), EditViewportRuntime.hasScheduledWorld());
    }

    static void beginFrame(ViewportHostPolicy.Snapshot host, boolean scheduledWorld) {
        editorFrame = host.workspace();
        opaqueEditorFrame = host.opaque();
        editorBackground = host.background();
        consumersThisFrame = scheduledWorld;
    }

    /** Controls GameRenderer.render's world branch. The runtime separately controls
     * camera/world extraction; game ticks, native GUI previews and frame cleanup continue.
     */
    public static boolean shouldRenderWorld(boolean original) {
        return original && (!opaqueEditorFrame || consumersThisFrame);
    }

    /** WindowRenderState dimensions are framebuffer pixels, not GUI-scaled sizes.
     * GameRenderer resizes the main target to these same dimensions before rendering.
     */
    public static void beginExtraction(boolean shouldRenderLevel, int width, int height) {
        Minecraft minecraft = Minecraft.getInstance();
        captureThisFrame = shouldRenderLevel && consumersThisFrame && minecraft.level != null && editorFrame
                && width > 0 && height > 0;
        HUD.reset();
        if (!hasConsumers() || !editorFrame || minecraft.level == null) GAME.clear();
    }

    /** Redirect only in-game GUI producers; the editor keeps its original GUI state. */
    public static GuiGraphicsExtractor hudGraphics(GuiGraphicsExtractor original) {
        if (!captureThisFrame) return original;
        Minecraft minecraft = Minecraft.getInstance();
        return new GuiGraphicsExtractor(minecraft, HUD,
                (int) minecraft.mouseHandler.getScaledXPos(minecraft.getWindow()),
                (int) minecraft.mouseHandler.getScaledYPos(minecraft.getWindow()));
    }

    /** Runs at the normal GUI render boundary, never inside a view's draw callback. */
    public static void renderGui(GuiRenderer renderer, GpuBufferSlice fog) {
        if (captureThisFrame) {
            if (capturesGameHud()) renderHud(renderer, fog);
            long copyStart = GameViewTiming.start();
            if (EditViewportRuntime.isRendering()) EditViewportRuntime.captureFrame();
            else GAME.capture(Minecraft.getInstance().getMainRenderTarget().getColorTexture());
            GameViewTiming.finish("frame copy", copyStart);
            EditViewportRuntime.frameCompleted();
        }

        if (opaqueEditorFrame) {
            // The world is only a source for game views. It must not bleed through
            // workspace gaps or appear briefly while the UI changes its layout.
            var target = Minecraft.getInstance().getMainRenderTarget();
            var encoder = RenderSystem.getDevice().createCommandEncoder();
            if (target.getColorTexture() != null) {
                encoder.clearColorTexture(target.getColorTexture(), editorBackground);
            }
            if (target.getDepthTexture() != null) encoder.clearDepthTexture(target.getDepthTexture(), 1.0);
        } else if (captureThisFrame) {
            // HUD and editor use their own GUI depth ordering.
            var depth = Minecraft.getInstance().getMainRenderTarget().getDepthTexture();
            if (depth != null) RenderSystem.getDevice().createCommandEncoder().clearDepthTexture(depth, 1.0);
        }
        long editorStart = GameViewTiming.start();
        renderer.render(fog);
        GameViewTiming.finish("editor submission", editorStart);
    }

    /** Only the shared player source owns a HUD submission and its buffer rings. */
    private static void renderHud(GuiRenderer renderer, GpuBufferSlice fog) {
        GameViewGuiRendererAccessor access = (GameViewGuiRendererAccessor) renderer;
        GuiRenderState editor = access.geometryNode$getRenderState();
        Map<VertexFormat, MappableRingBuffer> editorVertexBuffers = access.geometryNode$getVertexBuffers();
        GuiRenderer previousHudPass = hudPassRenderer;
        try {
            hudPassRenderer = renderer;
            access.geometryNode$setRenderState(HUD);
            access.geometryNode$setVertexBuffers(HUD_VERTEX_BUFFERS);
            long hudStart = GameViewTiming.start();
            renderer.render(fog);
            GameViewTiming.finish("HUD submission", hudStart);
        } finally {
            hudPassRenderer = previousHudPass;
            access.geometryNode$setVertexBuffers(editorVertexBuffers);
            access.geometryNode$setRenderState(editor);
        }
    }

    public static void close() {
        captureThisFrame = false;
        editorFrame = false;
        opaqueEditorFrame = false;
        editorBackground = 0;
        consumersThisFrame = false;
        HUD.reset();
        GAME.clear();
        // GameRenderer.close invokes this before closing the original GUI buffers.
        // These buffers were restored out of that renderer, so we own their close.
        for (MappableRingBuffer buffer : HUD_VERTEX_BUFFERS.values()) buffer.close();
        HUD_VERTEX_BUFFERS.clear();
        EditViewportRuntime.close();
    }

    static void clearImage() { GAME.clear(); }
}
