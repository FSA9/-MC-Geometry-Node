package com.mine.geometry_node.core.node.nodes.behavior.decorator;

import com.mine.geometry_node.core.engine.behavior.runtime.BehaviorNodeExecutor;
import com.mine.geometry_node.core.engine.behavior.runtime.executor.BehaviorDecoratorExecutors;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.behavior.BehaviorExecutableNode;

public final class BehaviorRepeatNode extends BaseNode implements BehaviorExecutableNode {
    public static final String TYPE_ID = "geometry_node:behavior_repeat";

    @Override
    public NodeDef getDefaultDefinition() {
        return BehaviorDecoratorNodeSupport.builder(TYPE_ID, "geometry_node.node.behavior_repeat")
                .addRow(BehaviorDecoratorNodeSupport.input(StandardPorts.COUNT, 1))
                .build();
    }

    @Override
    public BehaviorNodeExecutor behaviorExecutor() {
        return BehaviorDecoratorExecutors.repeat();
    }
}
