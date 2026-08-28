package com.mine.geometry_node.core.node.nodes.events.entity;

import com.mine.geometry_node.core.node.nodes.*;
import com.mine.geometry_node.core.node.nodes.events.BaseEventNode;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;

public class OnEntityDealDamage extends BaseEventNode {

    public static final String TYPE_ID = "on_entity_deal_damage";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.EVENT, Component.translatable("geometry_node.node.on_entity_deal_damage"))
                .addRow(new PortRow(null, StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.SOURCE_ENTITY.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.ENTITY.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.DIRECT_SOURCE.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.FLOAT_VALUE.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.DAMAGE_TYPE.toOutput(), UIHint.DEFAULT, null, null))
                .build();
    }
}
