package com.mine.geometry_node.core.engine.graph.data;

import org.jetbrains.annotations.Nullable;

/**
 * A resolved node input. The connection flag preserves the semantic difference
 * between a connected provider that produced null and an unconnected input
 * that fell back to its authored static value.
 */
public record ResolvedGraphInput(boolean connected, @Nullable Object value) {
}
