package com.mine.geometry_node.core.node.nodes.behavior.entity;

import com.mine.geometry_node.core.engine.behavior.runtime.BehaviorNodeExecutor;
import com.mine.geometry_node.core.engine.behavior.runtime.executor.BehaviorEntityExecutors;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.behavior.BehaviorExecutableNode;
import net.minecraft.network.chat.Component;

import java.util.Set;

public final class BehaviorClearTargetNode extends BaseNode implements BehaviorExecutableNode {
    public static final String TYPE_ID = "behavior_clear_target";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION,
                        Component.translatable("geometry_node.node.behavior_clear_target"))
                .addRow(BehaviorEntityNodeSupport.parentRow())
                .build();
    }

    @Override
    public BehaviorNodeExecutor behaviorExecutor() {
        return BehaviorEntityExecutors.clearTarget();
    }

    @Override
    public Set<Resource> requiredResources() {
        return Set.of(Resource.TARGET);
    }
}
