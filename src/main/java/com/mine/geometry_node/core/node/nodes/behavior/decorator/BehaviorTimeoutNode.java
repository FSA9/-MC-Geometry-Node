package com.mine.geometry_node.core.node.nodes.behavior.decorator;

import com.mine.geometry_node.core.engine.behavior.runtime.BehaviorNodeExecutor;
import com.mine.geometry_node.core.engine.behavior.runtime.executor.BehaviorDecoratorExecutors;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.behavior.BehaviorExecutableNode;

public final class BehaviorTimeoutNode extends BaseNode implements BehaviorExecutableNode {
    public static final String TYPE_ID = "geometry_node:behavior_timeout";

    @Override
    public NodeDef getDefaultDefinition() {
        return BehaviorDecoratorNodeSupport.builder(TYPE_ID, "geometry_node.node.behavior_timeout")
                .addRow(BehaviorDecoratorNodeSupport.tickInput(
                        100, "geometry_node.port.tick.timeout"))
                .build();
    }

    @Override
    public BehaviorNodeExecutor behaviorExecutor() {
        return BehaviorDecoratorExecutors.timeout();
    }
}
