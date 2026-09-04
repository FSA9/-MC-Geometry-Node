package com.mine.geometry_node.core.node.nodes.behavior.entity;

import com.mine.geometry_node.core.engine.behavior.runtime.BehaviorNodeExecutor;
import com.mine.geometry_node.core.engine.behavior.runtime.executor.BehaviorEntityExecutors;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.behavior.BehaviorExecutableNode;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Set;

public final class BehaviorSelectTargetNode extends BaseNode implements BehaviorExecutableNode {
    public static final String TYPE_ID = "behavior_select_target";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION,
                        Component.translatable("geometry_node.node.behavior_select_target"))
                .addRow(BehaviorEntityNodeSupport.parentRow())
                .addRow(BehaviorEntityNodeSupport.input(StandardPorts.CANDIDATES, List.of()))
                .build();
    }

    @Override
    public BehaviorNodeExecutor behaviorExecutor() {
        return BehaviorEntityExecutors.selectTarget();
    }

    @Override
    public Set<Resource> requiredResources() {
        return Set.of(Resource.TARGET);
    }
}
