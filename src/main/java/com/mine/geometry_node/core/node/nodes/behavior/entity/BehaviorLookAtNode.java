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

public final class BehaviorLookAtNode extends BaseNode implements BehaviorExecutableNode {
    public static final String TYPE_ID = "behavior_look_at";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION,
                        Component.translatable("geometry_node.node.behavior_look_at"))
                .addRow(BehaviorEntityNodeSupport.parentRow())
                .addRow(BehaviorEntityNodeSupport.input(StandardPorts.TARGET, null))
                .addRow(BehaviorEntityNodeSupport.tickInput(20, "geometry_node.port.tick.look"))
                .build();
    }

    @Override
    public BehaviorNodeExecutor behaviorExecutor() {
        return BehaviorEntityExecutors.lookAt();
    }

    @Override
    public Set<Resource> requiredResources() {
        return Set.of(Resource.LOOK);
    }
}
