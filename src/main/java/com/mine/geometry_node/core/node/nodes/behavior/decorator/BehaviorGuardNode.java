package com.mine.geometry_node.core.node.nodes.behavior.decorator;

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

/** Runs one child while its boolean guard remains true. */
public final class BehaviorGuardNode extends BaseNode {
    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(BehaviorNodeTypes.GUARD, NodeType.FLOW_CONTROL,
                        Component.translatable("geometry_node.node.behavior_guard"))
                .addRow(new PortRow(structurePort(BehaviorNodeTypes.PARENT_PORT),
                        structurePort(BehaviorNodeTypes.CHILDREN_PORT), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.CONDITION.toInput(), null, UIHint.CHECKBOX, null, null))
                .build();
    }

    private static PortDef structurePort(String id) {
        return PortDef.create(id, "geometry_node.port." + id, PortType.BEHAVIOR_STRUCTURE);
    }
}
