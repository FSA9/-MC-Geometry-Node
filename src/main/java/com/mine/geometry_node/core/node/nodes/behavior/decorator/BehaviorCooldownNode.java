package com.mine.geometry_node.core.node.nodes.behavior.decorator;

import com.mine.geometry_node.core.engine.behavior.runtime.BehaviorNodeExecutor;
import com.mine.geometry_node.core.engine.behavior.runtime.executor.BehaviorDecoratorExecutors;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.behavior.BehaviorExecutableNode;

public final class BehaviorCooldownNode extends BaseNode implements BehaviorExecutableNode {
    public static final String TYPE_ID = "behavior_cooldown";

    @Override
    public NodeDef getDefaultDefinition() {
        return BehaviorDecoratorNodeSupport.builder(TYPE_ID, "geometry_node.node.behavior_cooldown")
                .addRow(BehaviorDecoratorNodeSupport.tickInput(
                        20, "geometry_node.port.tick.cooldown"))
                .build();
    }

    @Override
    public BehaviorNodeExecutor behaviorExecutor() {
        return BehaviorDecoratorExecutors.cooldown();
    }
}
