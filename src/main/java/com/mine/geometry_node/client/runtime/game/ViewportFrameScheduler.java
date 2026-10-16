package com.mine.geometry_node.client.runtime.game;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** One world pass per frame. Background deadlines cap actual rendering, not UI composition. */
final class ViewportFrameScheduler<T> {
    private final Map<T, Long> lastRendered = new HashMap<>();
    private T lastBackground;
    private boolean preferredTurn = true;

    T next(List<T> sources, T preferred, long now, int backgroundFps) {
        if (sources.isEmpty()) { reset(); return null; }
        lastRendered.keySet().retainAll(sources);
        if (preferred != null && !sources.contains(preferred)) preferred = null;
        long interval = (1_000_000_000L + Math.clamp(backgroundFps, 1, 120) - 1) / Math.clamp(backgroundFps, 1, 120);
        if (preferred != null && preferredTurn) {
            preferredTurn = false;
            return selected(preferred, now);
        }
        int start = lastBackground == null ? -1 : sources.indexOf(lastBackground);
        for (int step = 1; step <= sources.size(); step++) {
            T candidate = sources.get(Math.floorMod(start + step, sources.size()));
            Long previous = lastRendered.get(candidate);
            if (candidate != preferred && (previous == null || now - previous >= interval)) {
                lastBackground = candidate;
                preferredTurn = true;
                return selected(candidate, now);
            }
        }
        // If peers are not due, give the active view the remaining frame budget.
        // With no active view, null means no world update/extraction/draw this frame.
        return preferred == null ? null : selected(preferred, now);
    }

    private T selected(T source, long now) { lastRendered.put(source, now); return source; }
    void completed(T source, long now) { if (source != null && lastRendered.containsKey(source)) lastRendered.put(source, now); }
    void reset() { lastBackground = null; preferredTurn = true; lastRendered.clear(); }
}
