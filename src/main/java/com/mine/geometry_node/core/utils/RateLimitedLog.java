package com.mine.geometry_node.core.utils;

import com.mine.geometry_node.core.engine.graph.data.GraphDataContext;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bounds repeated diagnostics by key. Intended for error paths that may run on
 * every graph tick; normal execution should not call this class.
 */
public final class RateLimitedLog {
    private static final long WINDOW_NANOS = 5_000_000_000L;
    private static final int MAX_KEYS = 2048;
    private static final Map<String, Long> WINDOWS = new LinkedHashMap<>(16, 0.75F, true);

    private RateLimitedLog() {
    }

    public static boolean acquire(String key) {
        if (key == null || key.isEmpty()) {
            return false;
        }

        long now = System.nanoTime();
        synchronized (WINDOWS) {
            Long windowStart = WINDOWS.get(key);
            if (windowStart != null && now - windowStart < WINDOW_NANOS) {
                return false;
            }
            if (windowStart == null && WINDOWS.size() >= MAX_KEYS) {
                String eldest = WINDOWS.keySet().iterator().next();
                WINDOWS.remove(eldest);
            }
            WINDOWS.put(key, now);
            return true;
        }
    }

    public static boolean acquire(GraphDataContext context, String diagnostic) {
        if (context == null) {
            return acquire(diagnostic);
        }
        String nodeId = context.getCurrentNodeStableId();
        return acquire("graph:" + context.getGraphId() + ":node:"
                + (nodeId != null ? nodeId : "unknown") + ':' + diagnostic);
    }
}
