package com.mine.geometry_node.client.runtime.game.render;

import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.gizmos.SimpleGizmoCollector;

/** Completes the extraction lifecycle when an opaque workspace skips world drawing. */
public final class GameFrameCleanup {
    private GameFrameCleanup() {}

    public static void discard(LevelRenderState state, SimpleGizmoCollector gizmos) {
        // Vanilla normally resets these in LevelRenderer.renderLevel and its main
        // pass. LevelRenderState.reset deliberately does not release particle groups.
        // Neither reset changes the extracted camera needed by native GUI previews.
        state.reset();
        state.particlesRenderState.reset();
        // Use the normal drain lifecycle: discard this frame's temporary gizmos,
        // while retaining persistent gizmos until their own expiry time.
        gizmos.drainGizmos();
    }
}
