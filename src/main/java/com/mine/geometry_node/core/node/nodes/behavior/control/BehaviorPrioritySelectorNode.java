package com.mine.geometry_node.core.node.nodes.behavior.control;

import com.mine.geometry_node.core.engine.behavior.runtime.BehaviorNodeExecutor;
import com.mine.geometry_node.core.engine.behavior.runtime.executor.BehaviorControlExecutors;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.behavior.BehaviorExecutableNode;

public final class BehaviorPrioritySelectorNode extends BaseNode implements BehaviorExecutableNode {
    public static final String TYPE_ID = "behavior_priority_selector";

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
                "geometry_node.node.behavior_priority_selector", instanceData);
    }

    @Override
    public BehaviorNodeExecutor behaviorExecutor() {
        return BehaviorControlExecutors.prioritySelector();
    }
}
