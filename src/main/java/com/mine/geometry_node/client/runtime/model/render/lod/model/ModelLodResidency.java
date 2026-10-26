package com.mine.geometry_node.client.runtime.model.render.lod.model;

import java.util.function.IntPredicate;

/** Chooses a complete model level while another level uploads. Never mixes primitive levels. */
public final class ModelLodResidency {
    private ModelLodResidency() {}

    /** A cold resident level waits for upload; compatibility/budget fallbacks may draw immediately. */
    public record Selection(int level, boolean residentReady, boolean deferred) {
        public Selection(int level, boolean residentReady) { this(level, residentReady, false); }
        public Selection {
            ModelLodPolicy.requireLevel(level);
            if (residentReady && deferred) throw new IllegalArgumentException("ready geometry cannot be deferred");
        }
    }

    public static Selection select(int requested, boolean resident,
                                   IntPredicate complete, IntPredicate upload) {
        return select(requested, resident, false, complete, upload);
    }

    /** Under memory pressure, let the old fallback retire while the requested level keeps progressing. */
    public static Selection select(int requested, boolean resident, boolean requestedOnly,
                                   IntPredicate complete, IntPredicate upload) {
        ModelLodPolicy.requireLevel(requested);
        if (!resident) return new Selection(requested, false);
        if (requestedOnly) {
            boolean ready = complete.test(requested) || upload.test(requested);
            return new Selection(requested, ready, !ready);
        }
        int fallback = -1;
        for (int delta = 0; delta < ModelLodPolicy.LEVEL_COUNT && fallback < 0; delta++) {
            int finer = requested - delta, coarser = requested + delta;
            if (finer >= 0 && complete.test(finer)) fallback = finer;
            else if (coarser < ModelLodPolicy.LEVEL_COUNT && coarser != finer && complete.test(coarser)) fallback = coarser;
        }
        if (fallback == requested || upload.test(requested)) return new Selection(requested, true);
        // Rebuilding a large missing tail every frame defeats the bounded resident upload budget.
        return fallback >= 0 ? new Selection(fallback, true) : new Selection(requested, false, true);
    }
}
