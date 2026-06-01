package com.mine.geometry_node.core.node.nodes.events.entity;

import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.nodes.events.BaseEventNode;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;

public class OnEntityBreed extends BaseEventNode {
    public static final String TYPE_ID = "on_entity_breed";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.EVENT, Component.translatable("geometry_node.node.on_entity_breed"))
                .addRow(new PortRow(null, StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.ENTITY.toOutput(), UIHint.DEFAULT, null, null)) // 父母之一
                .addRow(new PortRow(null, StandardPorts.TRIGGER_ENTITY.toOutput(), UIHint.DEFAULT, null, null)) // 玩家/触发者
                .addRow(new PortRow(null, StandardPorts.SOURCE_ENTITY.toOutput(), UIHint.DEFAULT, null, null)) // 诞生的幼体
                .build();
    }
}