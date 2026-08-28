package com.mine.geometry_node.core.node.nodes.behavior.decorator;

import com.mine.geometry_node.core.node.port.StandardPorts;

import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;

/** Inverts normal success and failure while preserving Running. */
public final class BehaviorInverterNode extends BaseNode {
    public static final String TYPE_ID = "geometry_node:behavior_inverter";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.FLOW_CONTROL,
                        Component.translatable("geometry_node.node.behavior_inverter"))
                .addRow(new PortRow(StandardPorts.BEHAVIOR_PARENT.toInput(),
                        StandardPorts.BEHAVIOR_CHILDREN.toOutput(), UIHint.DEFAULT, null, null))
                .build();
    }
}
