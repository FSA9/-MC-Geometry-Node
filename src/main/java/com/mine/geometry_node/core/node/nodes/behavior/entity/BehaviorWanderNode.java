package com.mine.geometry_node.core.node.nodes.behavior.entity;

import com.mine.geometry_node.core.engine.behavior.runtime.BehaviorNodeExecutor;
import com.mine.geometry_node.core.engine.behavior.runtime.executor.BehaviorEntityExecutors;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.behavior.BehaviorExecutableNode;
import net.minecraft.network.chat.Component;

import java.util.Set;

public final class BehaviorWanderNode extends BaseNode implements BehaviorExecutableNode {
    public static final String TYPE_ID = "geometry_node:behavior_wander";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION,
                        Component.translatable("geometry_node.node.behavior_wander"))
                .addRow(BehaviorEntityNodeSupport.parentRow())
                .addRow(BehaviorEntityNodeSupport.input(StandardPorts.SPEED, 1.0f))
                .addRow(BehaviorEntityNodeSupport.input(StandardPorts.HORIZONTAL_RANGE, 10))
                .addRow(BehaviorEntityNodeSupport.input(StandardPorts.VERTICAL_RANGE, 4))
                .build();
    }

    @Override
    public BehaviorNodeExecutor behaviorExecutor() {
        return BehaviorEntityExecutors.wander();
    }

    @Override
    public Set<Resource> requiredResources() {
        return Set.of(Resource.MOVEMENT);
    }
}
