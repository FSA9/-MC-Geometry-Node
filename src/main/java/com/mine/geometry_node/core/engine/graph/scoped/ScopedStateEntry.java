package com.mine.geometry_node.core.engine.graph.scoped;

import com.mine.geometry_node.core.node.port.PortType;

public record ScopedStateEntry(Object value, PortType type, long revision,
                               String sourceNodeId, long gameTick) {
}
