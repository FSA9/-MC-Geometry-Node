package com.mine.geometry_node.client.runtime.game.chunk;

import it.unimi.dsi.fastutil.longs.LongCollection;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.function.LongConsumer;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/** Each terrain cache acknowledges readiness independently; a faster view cannot
 * consume notifications needed by a view that was not rendered this frame. */
public final class ViewportChunkReadiness {
    // Render-thread only. Weak keys on both sides do not retain unloaded worlds
    // or closed/rebuilt terrain managers. Invalidations are bounded by known chunks.
    private static final WeakHashMap<Object, Set<ViewportChunkReadiness>> READERS = new WeakHashMap<>();
    private final LongOpenHashSet known;
    private final LongOpenHashSet invalidated = new LongOpenHashSet();

    public ViewportChunkReadiness(LongCollection initialized) { known = new LongOpenHashSet(initialized); }

    public ViewportChunkReadiness(LongCollection initialized, Object source) {
        this(initialized);
        READERS.computeIfAbsent(source, ignored -> Collections.newSetFromMap(new WeakHashMap<>())).add(this);
    }

    /** Record an unload/light-readiness loss even if the same coordinate becomes
     * ready again before a slower viewport gets another render opportunity. */
    public static void invalidate(Object source, long key) {
        Set<ViewportChunkReadiness> readers = READERS.get(source);
        if (readers == null) return;
        for (var reader : readers) if (reader.known.contains(key)) reader.invalidated.add(key);
    }

    public void update(LongCollection current, LongConsumer added, LongConsumer removed) {
        for (var iterator = known.iterator(); iterator.hasNext();) {
            long key = iterator.nextLong();
            if (!current.contains(key) || invalidated.contains(key)) { removed.accept(key); iterator.remove(); }
        }
        for (var iterator = current.iterator(); iterator.hasNext();) {
            long key = iterator.nextLong();
            if (known.add(key)) added.accept(key);
        }
        invalidated.clear();
    }
}
