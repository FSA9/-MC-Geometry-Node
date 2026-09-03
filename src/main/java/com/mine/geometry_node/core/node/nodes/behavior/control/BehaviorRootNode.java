package com.mine.geometry_node.core.node.nodes.behavior.control;

import com.mine.geometry_node.core.node.definition.port.StandardPorts;

import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.nodes.behavior.BehaviorExecutableNode;
import com.mine.geometry_node.core.engine.behavior.runtime.BehaviorNodeExecutor;
import com.mine.geometry_node.core.engine.behavior.runtime.executor.BehaviorControlExecutors;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import net.minecraft.network.chat.Component;

/** Unique structural entry point of a behavior tree. */
public final class BehaviorRootNode extends BaseNode implements BehaviorExecutableNode {
    public static final String TYPE_ID = "geometry_node:behavior_root";
    public static final String RECHECK_TICK_PORT = StandardPorts.TICK.getId();
    public static final String SCHEDULE_TICK_PORT = StandardPorts.TICK.getIdWithIndex(1);

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.FLOW_CONTROL,
                Component.translatable("geometry_node.node.behavior_root"))
                .addRow(new PortRow(null, StandardPorts.BEHAVIOR_CHILDREN.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(tickInput(0, 1, "geometry_node.port.tick.recheck"))
                .addRow(tickInput(1, -1, "geometry_node.port.tick.schedule"))
                .build();
    }

    private static PortRow tickInput(int index, int defaultValue, String translationKey) {
        var port = index == 0
                ? StandardPorts.TICK.toInput(defaultValue)
                : StandardPorts.TICK.toInputWithIndex(index, defaultValue);
        return PortRow.passthrough(port.withDisplayName(translationKey).hiddenPin(),
                UIHint.INPUT, null, null);
    }

    @Override
    public BehaviorNodeExecutor behaviorExecutor() {
        return BehaviorControlExecutors.root();
    }
}
