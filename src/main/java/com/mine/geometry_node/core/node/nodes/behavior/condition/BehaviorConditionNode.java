package com.mine.geometry_node.core.node.nodes.behavior.condition;

import com.mine.geometry_node.core.engine.behavior.document.BehaviorNodeTypes;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortDef;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.PortType;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;

/** Converts one boolean data value into a behavior result. */
public final class BehaviorConditionNode extends BaseNode {
    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(BehaviorNodeTypes.CONDITION, NodeType.FLOW_CONTROL,
                        Component.translatable("geometry_node.node.behavior_condition"))
                .addRow(new PortRow(parentPort(), null, UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.CONDITION.toInput(), null, UIHint.CHECKBOX, null, null))
                .build();
    }

    private static PortDef parentPort() {
        return PortDef.create(BehaviorNodeTypes.PARENT_PORT,
                "geometry_node.port." + BehaviorNodeTypes.PARENT_PORT, PortType.BEHAVIOR_STRUCTURE);
    }
}
