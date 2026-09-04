package com.mine.geometry_node.core.node.nodes.behavior.control;

import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.nodes.behavior.BehaviorExecutableNode;
import com.mine.geometry_node.core.engine.behavior.runtime.BehaviorNodeExecutor;
import com.mine.geometry_node.core.engine.behavior.runtime.executor.BehaviorControlExecutors;

/** Ordered, memory-form sequence structural node. */
public final class BehaviorSequenceNode extends BaseNode implements BehaviorExecutableNode {
    public static final String TYPE_ID = "behavior_sequence";

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
                "geometry_node.node.behavior_sequence", instanceData);
    }

    @Override
    public BehaviorNodeExecutor behaviorExecutor() {
        return BehaviorControlExecutors.sequence();
    }
}
