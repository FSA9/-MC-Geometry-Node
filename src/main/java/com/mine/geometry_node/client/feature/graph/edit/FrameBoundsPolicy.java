package com.mine.geometry_node.client.feature.graph.edit;

import com.mine.geometry_node.core.node.document.FrameData;
import com.mine.geometry_node.core.node.document.NodeData;

/** Explicit layout capability for document edits. Planning forks retain the same policy. */
@FunctionalInterface
public interface FrameBoundsPolicy {
    Bounds measure(String frameId, Iterable<NodeData> nodes, Iterable<FrameData> frames);

    record Bounds(boolean hasChildren, float x, float y, float width, float height) {}
}
