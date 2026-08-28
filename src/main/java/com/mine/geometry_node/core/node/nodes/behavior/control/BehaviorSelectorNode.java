package com.mine.geometry_node.core.node.nodes.behavior.control;

import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;

/** Ordered, memory-form fallback selector. */
public final class BehaviorSelectorNode extends BaseNode {
    public static final String TYPE_ID = "geometry_node:behavior_selector";

    @Override
    public NodeDef getDefaultDefinition() {
        return definition(null);
    }

    @Override
    public NodeDef getDefinition(NodeData instanceData) {
        return definition(instanceData);
    }

    private static NodeDef definition(NodeData instanceData) {
        return BehaviorCompositeDefinition.create(TYPE_ID,
                "geometry_node.node.behavior_selector", instanceData);
    }
}
