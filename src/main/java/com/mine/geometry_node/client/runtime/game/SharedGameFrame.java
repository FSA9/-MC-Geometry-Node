package com.mine.geometry_node.client.runtime.game;

import com.mine.geometry_node.client.ui.MainUI;
import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.mixin.game.GameViewGuiRendererAccessor;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.vertex.VertexFormat;
import icyllis.modernui.mc.MuiScreen;
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

/** One GPU game frame (world and HUD) shared by every visible game view.
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
    private static GpuTexture texture;
    private static boolean editorFrame;
    private static boolean opaqueEditorFrame;
    private static boolean consumersThisFrame;
    private static boolean captureThisFrame;
    private static GuiRenderer hudPassRenderer;
    private static volatile boolean available;
    private static volatile int frameWidth;
    private static volatile int frameHeight;

    private SharedGameFrame() {}

    public static synchronized void setVisible(Object owner, boolean visible) {
        if (visible) CONSUMERS.add(owner);
        else CONSUMERS.remove(owner);
    }

    private static synchronized boolean hasConsumers() {
        return !CONSUMERS.isEmpty();
    }

    public static boolean isAvailable() { return available; }
    public static boolean capturesGameHud() { return captureThisFrame; }
    public static int width() { return frameWidth; }
    public static int height() { return frameHeight; }

    /** The HUD and editor are two submissions in one frame. Renderer caches must
     * retire unused entries only after both have marked their resources as used.
     */
    public static boolean deferGuiCacheCleanup(GuiRenderer renderer) {
        return hudPassRenderer == renderer;
    }

    /** Freeze the render policy before extraction. A UI-thread detach/attach between
     * extraction and rendering must not suppress the world for a frame being captured.
     * MainUiShell fills its root with BG_ROOT; transparent editor roots keep the world.
     */
    public static void beginFrame() {
        Minecraft minecraft = Minecraft.getInstance();
        editorFrame = minecraft.screen instanceof MuiScreen screen && screen.getFragment() instanceof MainUI;
        opaqueEditorFrame = editorFrame && (UIConstants.MainUI.BG_ROOT >>> 24) == 255;
        consumersThisFrame = hasConsumers();
    }

    /** This changes only GameRenderer.render's world branch. Camera updates, world
     * extraction, game ticks, native GUI previews and renderer endFrame still run.
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
        if (!captureThisFrame) {
            releaseTexture();
            return;
        }
        if (texture == null || frameWidth != width || frameHeight != height) {
            long allocationStart = GameViewTiming.start();
            releaseTexture();
            var source = minecraft.getMainRenderTarget().getColorTexture();
            if (source == null) {
                captureThisFrame = false;
                return;
            }
            texture = RenderSystem.getDevice().createTexture(
                    () -> "GeometryNode shared game view",
                    GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT,
                    source.getFormat(), width, height, 1, 1);
            RenderSystem.getDevice().createCommandEncoder().clearColorTexture(texture, 0xFF000000);
            if (!GameFrameImage.publish(texture)) {
                texture.close();
                texture = null;
                captureThisFrame = false;
                return;
            }
            frameWidth = width;
            frameHeight = height;
            GameViewTiming.finish("allocate shared frame " + width + "x" + height, allocationStart, true);
        }
        available = true;
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
        if (captureThisFrame && texture != null) {
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
            var target = Minecraft.getInstance().getMainRenderTarget();
            var source = target.getColorTexture();
            if (source != null && source.getWidth(0) == frameWidth && source.getHeight(0) == frameHeight) {
                var encoder = RenderSystem.getDevice().createCommandEncoder();
                long copyStart = GameViewTiming.start();
                encoder.copyTextureToTexture(source, texture, 0, 0, 0, 0, 0, frameWidth, frameHeight);
                GameViewTiming.finish("frame copy", copyStart);
            } else {
                // Never copy with mixed dimensions during a resize. Retire the
                // publication rather than showing a stale or partially copied frame.
                releaseTexture();
            }
        }
        if (opaqueEditorFrame) {
            // The world is only a source for game views. It must not bleed through
            // workspace gaps or appear briefly while the UI changes its layout.
            var target = Minecraft.getInstance().getMainRenderTarget();
            var encoder = RenderSystem.getDevice().createCommandEncoder();
            if (target.getColorTexture() != null) {
                encoder.clearColorTexture(target.getColorTexture(), UIConstants.MainUI.BG_ROOT);
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

    public static void close() {
        captureThisFrame = false;
        editorFrame = false;
        opaqueEditorFrame = false;
        consumersThisFrame = false;
        HUD.reset();
        releaseTexture();
        // GameRenderer.close invokes this before closing the original GUI buffers.
        // These buffers were restored out of that renderer, so we own their close.
        for (MappableRingBuffer buffer : HUD_VERTEX_BUFFERS.values()) buffer.close();
        HUD_VERTEX_BUFFERS.clear();
    }

    private static void releaseTexture() {
        available = false;
        // Keep the much smaller HUD vertex buffers across temporary invisibility
        // and native screens. Idle rings do not rotate or overwrite in-flight data;
        // their normal fence check protects reuse when the game view returns.
        // Arc3D recordings can still reference retired frames. The image bridge owns
        // texture destruction and releases the GPU allocation after the last use.
        GameFrameImage.clear();
        texture = null;
        frameWidth = 0;
        frameHeight = 0;
    }
}
