package com.mine.geometry_node.core.node.nodes.behavior.entity;

import com.mine.geometry_node.core.engine.behavior.runtime.BehaviorNodeExecutor;
import com.mine.geometry_node.core.engine.behavior.runtime.executor.BehaviorEntityExecutors;
import com.mine.geometry_node.core.node.definition.node.NodeComment;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.behavior.BehaviorExecutableNode;
import net.minecraft.network.chat.Component;

import java.util.Set;

public final class BehaviorSetAttackTargetNode extends BaseNode implements BehaviorExecutableNode {
    public static final String TYPE_ID = "behavior_attack_target";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION,
                        Component.translatable("geometry_node.node.behavior_attack_target"))
                .comment(NodeComment.builder(TYPE_ID)
                        .text("summary")
                        .text("range")
                        .text("delegation")
                        .build())
                .addRow(BehaviorEntityNodeSupport.parentRow())
                .addRow(BehaviorEntityNodeSupport.input(StandardPorts.TARGET, null))
                .addRow(BehaviorEntityNodeSupport.input(StandardPorts.TARGET_RANGE, 20.0f))
                .build();
    }

    @Override
    public BehaviorNodeExecutor behaviorExecutor() {
        return BehaviorEntityExecutors.setAttackTarget();
    }

    @Override
    public Set<Resource> requiredResources() {
        return Set.of(Resource.TARGET);
    }
}
