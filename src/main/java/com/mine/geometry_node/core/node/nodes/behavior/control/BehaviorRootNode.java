package com.mine.geometry_node.core.node.nodes.behavior.control;

import com.mine.geometry_node.core.node.port.StandardPorts;

import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.nodes.behavior.BehaviorExecutableNode;
import com.mine.geometry_node.core.engine.behavior.runtime.BehaviorNodeExecutor;
import com.mine.geometry_node.core.engine.behavior.runtime.executor.BehaviorControlExecutors;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;

/** Unique structural entry point of a behavior tree. */
public final class BehaviorRootNode extends BaseNode implements BehaviorExecutableNode {
    public static final String TYPE_ID = "geometry_node:behavior_root";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.FLOW_CONTROL,
                Component.translatable("geometry_node.node.behavior_root"))
                .addRow(new PortRow(null, StandardPorts.BEHAVIOR_CHILDREN.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(staticInteger(StandardPorts.RECHECK_INTERVAL, 1))
                .addRow(staticInteger(StandardPorts.SCHEDULE_OFFSET, -1))
                .build();
    }

    private static PortRow staticInteger(StandardPorts port, int defaultValue) {
        return new PortRow(port.toInput(defaultValue).hiddenPin(), null, UIHint.INPUT, null, null);
    }

    @Override
    public BehaviorNodeExecutor behaviorExecutor() {
        return BehaviorControlExecutors.root();
    }
}
