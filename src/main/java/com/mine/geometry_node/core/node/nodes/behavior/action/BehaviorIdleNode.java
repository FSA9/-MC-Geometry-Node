package com.mine.geometry_node.core.node.nodes.behavior.action;

import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.nodes.behavior.BehaviorExecutableNode;
import com.mine.geometry_node.core.engine.behavior.runtime.BehaviorNodeExecutor;
import com.mine.geometry_node.core.engine.behavior.runtime.executor.BehaviorActionExecutors;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import net.minecraft.network.chat.Component;

/** Indefinite cancellable fallback with a configurable reevaluation interval. */
public final class BehaviorIdleNode extends BaseNode implements BehaviorExecutableNode {
    public static final String TYPE_ID = "geometry_node:behavior_idle";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION,
                        Component.translatable("geometry_node.node.behavior_idle"))
                .addRow(new PortRow(StandardPorts.BEHAVIOR_PARENT.toInput(), null, UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.TICK.toInput(20)
                        .withDisplayName("geometry_node.port.tick.recheck"), null, UIHint.INPUT, null, null))
                .build();
    }

    @Override
    public BehaviorNodeExecutor behaviorExecutor() {
        return BehaviorActionExecutors.idleExecutor();
    }
}
