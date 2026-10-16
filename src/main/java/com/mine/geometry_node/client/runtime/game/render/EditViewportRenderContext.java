package com.mine.geometry_node.client.runtime.game.render;

import com.mine.geometry_node.mixin.game.render.EditViewportMinecraftAccessor;
import com.mine.geometry_node.mixin.game.render.EditViewportLevelStateAccessor;
import com.mine.geometry_node.mixin.game.render.EditViewportResourcePoolAccessor;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Render-thread terrain ownership. Only one context extracts/draws in a client frame. */
public final class EditViewportRenderContext implements AutoCloseable {
    private static final List<EditViewportRenderContext> LIVE = new ArrayList<>();
    private static boolean forwarding;
    private final Minecraft minecraft;
    private final ClientLevel level;
    private final LevelRenderer playerRenderer;
    private final LevelRenderer renderer;
    private final CrossFrameResourcePool playerResources;
    // Match GameRenderer's retention, measured in this viewport's own renders.
    private final CrossFrameResourcePool resources = new CrossFrameResourcePool(3);
    private boolean rendered;
    private boolean closed;

    public EditViewportRenderContext(Minecraft minecraft) {
        this.minecraft = minecraft;
        level = minecraft.level;
        playerRenderer = minecraft.levelRenderer;
        playerResources = ((EditViewportResourcePoolAccessor) minecraft.gameRenderer).geometryNode$getResourcePool();
        renderer = new LevelRenderer(minecraft, minecraft.getEntityRenderDispatcher(),
                minecraft.getBlockEntityRenderDispatcher(), minecraft.renderBuffers(),
                minecraft.gameRenderer.getGameRenderState(), minecraft.gameRenderer.getFeatureRenderDispatcher());
        // Registration precedes initialization so the optional Sodium hook can
        // identify this renderer while its first ready-chunk baseline is built.
        LIVE.add(this);
        try (var shader = EditViewportShaderScope.enter(true); var scope = enter()) {
            renderer.onResourceManagerReload(minecraft.getResourceManager());
            renderer.setLevel(level);
        } catch (RuntimeException | Error failure) {
            close();
            throw failure;
        }
    }

    public boolean isClosed() { return closed; }

    public void resize(int width, int height) {
        if (closed || width <= 0 || height <= 0) return;
        var outline = renderer.entityOutlineTarget();
        if (outline != null && (outline.width != width || outline.height != height)) {
            resources.clear();
            renderer.resize(width, height);
        }
    }

    /** A native window resize may occur while the editor renderer is installed.
     * Keep the retained player attachments in sync before its next scheduled pass. */
    public void resizePlayer(int width, int height) {
        if (closed || width <= 0 || height <= 0) return;
        var outline = playerRenderer.entityOutlineTarget();
        if (outline != null && (outline.width != width || outline.height != height)) {
            try (var scope = new Scope(minecraft, playerRenderer, playerResources)) {
                playerResources.clear();
                playerRenderer.resize(width, height);
            }
        }
    }

    public Scope enter() {
        if (closed) throw new IllegalStateException("Closed edit render context");
        ((EditViewportLevelStateAccessor) renderer).geometryNode$setTicks(playerRenderer.getTicks());
        ((EditViewportLevelStateAccessor) renderer).geometryNode$expireBreakingProgress();
        return new Scope(minecraft, renderer, resources);
    }

    public static Scope enter(EditViewportRenderContext context) {
        return context == null ? new Scope(null, null, null) : context.enter();
    }

    public void rendered() { rendered = true; }

    public static void mutate(LevelRenderer source, Runnable original, Consumer<LevelRenderer> operation) {
        if (forwarding || isEditorRenderer(source) || LIVE.isEmpty()) { original.run(); return; }
        forwarding = true;
        try {
            original.run();
            for (var context : List.copyOf(LIVE)) {
                if (!context.closed && context.playerRenderer == source && context.level == context.minecraft.level) {
                    try (var scope = context.enter()) { operation.accept(context.renderer); }
                }
            }
        } finally { forwarding = false; }
    }

    public static boolean isEditorRenderer(LevelRenderer candidate) {
        for (var context : LIVE) if (context.renderer == candidate) return true;
        return false;
    }

    /** World mutations are sent to ClientLevel's original renderer; fan out once. */
    public static void forward(LevelRenderer source, Consumer<LevelRenderer> operation) {
        if (forwarding || isEditorRenderer(source) || LIVE.isEmpty()) return;
        forwarding = true;
        try {
            for (var context : List.copyOf(LIVE)) {
                if (!context.closed && context.playerRenderer == source && context.level == context.minecraft.level) {
                    try (var shader = EditViewportShaderScope.enter(true); var scope = context.enter()) {
                        operation.accept(context.renderer);
                    }
                }
            }
        } finally { forwarding = false; }
    }

    /** Called after presentation where vanilla retires the original renderer's rings. */
    public static void endFrame(LevelRenderer source) {
        forward(source, renderer -> {
            for (var context : LIVE) {
                if (context.renderer == renderer && context.rendered) {
                    context.rendered = false;
                    renderer.endFrame();
                    break;
                }
            }
        });
    }

    public static void closeFor(LevelRenderer source) {
        if (isEditorRenderer(source)) return;
        for (var context : List.copyOf(LIVE)) if (context.playerRenderer == source) context.close();
    }

    @Override public void close() {
        if (closed) return;
        // Keep registration through setLevel(null): its hooks must still know
        // that this is an editor renderer, not a source for lifecycle broadcasts.
        try (var shader = EditViewportShaderScope.enter(true); var scope = enter()) {
            renderer.setLevel(null);
            renderer.close();
        } finally {
            closed = true;
            LIVE.remove(this);
            resources.close();
        }
    }

    public static final class Scope implements AutoCloseable {
        private final Minecraft minecraft;
        private final LevelRenderer previous;
        private final CrossFrameResourcePool previousResources;
        private Scope(Minecraft minecraft, LevelRenderer renderer, CrossFrameResourcePool resources) {
            this.minecraft = minecraft;
            previous = minecraft == null ? null : minecraft.levelRenderer;
            previousResources = minecraft == null ? null
                    : ((EditViewportResourcePoolAccessor) minecraft.gameRenderer).geometryNode$getResourcePool();
            if (minecraft != null) {
                ((EditViewportMinecraftAccessor) minecraft).geometryNode$setLevelRenderer(renderer);
                ((EditViewportResourcePoolAccessor) minecraft.gameRenderer).geometryNode$setResourcePool(resources);
            }
        }
        @Override public void close() {
            if (minecraft != null) {
                ((EditViewportResourcePoolAccessor) minecraft.gameRenderer).geometryNode$setResourcePool(previousResources);
                ((EditViewportMinecraftAccessor) minecraft).geometryNode$setLevelRenderer(previous);
            }
        }
    }
}
