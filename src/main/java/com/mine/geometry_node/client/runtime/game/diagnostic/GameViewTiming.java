package com.mine.geometry_node.client.runtime.game.diagnostic;

import com.mine.geometry_node.GeometryNode;
import net.minecraft.client.renderer.state.level.LevelRenderState;

/** Opt-in CPU-side timings; these include driver waits, not asynchronous GPU execution time. */
public final class GameViewTiming {
    private static final boolean ENABLED = Boolean.getBoolean("geometry_node.gameView.profile");
    private static final long SLOW_NANOS = 20_000_000L;
    private static int discardedFrames;
    private static int largestDiscardedEntityCount;

    private GameViewTiming() {}

    public static long start() { return ENABLED ? System.nanoTime() : 0; }

    /** Render-thread counters make a long-hidden view's first frame reviewable. */
    public static void frameDecision(boolean discarded, LevelRenderState state) {
        if (!ENABLED) return;
        if (discarded) {
            discardedFrames++;
            largestDiscardedEntityCount = Math.max(largestDiscardedEntityCount, state.entityRenderStates.size());
        } else if (discardedFrames > 0) {
            GeometryNode.LOGGER.info("[GameViewTiming] Resume after {} discarded frames: max hidden entities={}, current entities={}, blockEntities={}, particleGroups={}",
                    discardedFrames, largestDiscardedEntityCount, state.entityRenderStates.size(),
                    state.blockEntityRenderStates.size(), state.particlesRenderState.particles.size());
            discardedFrames = 0;
            largestDiscardedEntityCount = 0;
        }
    }

    public static void finish(String stage, long start) { finish(stage, start, false); }

    public static void finish(String stage, long start, boolean transition) {
        if (start == 0) return;
        long elapsed = System.nanoTime() - start;
        if (transition || elapsed >= SLOW_NANOS) {
            GeometryNode.LOGGER.info("[GameViewTiming] {}: {} ms", stage, elapsed / 1_000_000.0);
        }
    }
}
