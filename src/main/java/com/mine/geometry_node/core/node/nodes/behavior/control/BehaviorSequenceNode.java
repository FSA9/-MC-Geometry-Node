package com.mine.geometry_node.core.node.nodes.behavior.control;

import com.mine.geometry_node.core.engine.behavior.document.BehaviorNodeTypes;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;

/** Ordered, memory-form sequence structural node. */
public final class BehaviorSequenceNode extends BaseNode {
    @Override
    public NodeDef getDefaultDefinition() {
        return definition(null);
    }

    @Override
    public NodeDef getDefinition(NodeData instanceData) {
        return definition(instanceData);
    }

    private static NodeDef definition(NodeData instanceData) {
        return BehaviorCompositeDefinition.create(BehaviorNodeTypes.SEQUENCE,
                "geometry_node.node.behavior_sequence", instanceData);
    }
}
