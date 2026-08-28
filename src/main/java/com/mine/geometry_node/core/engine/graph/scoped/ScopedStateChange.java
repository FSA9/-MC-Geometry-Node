package com.mine.geometry_node.core.engine.graph.scoped;

public record ScopedStateChange(long revision, String sourceNodeId, long gameTick) {
}
