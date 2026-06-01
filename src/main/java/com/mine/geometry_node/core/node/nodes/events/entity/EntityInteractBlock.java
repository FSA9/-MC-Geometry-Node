package com.mine.geometry_node.core.node.nodes.events.entity;

import com.mine.geometry_node.core.node.nodes.*;
import com.mine.geometry_node.core.node.nodes.events.BaseEventNode;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;

public class EntityInteractBlock extends BaseEventNode {

    public static final String TYPE_ID = "entity_interact_block";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.EVENT, Component.translatable("geometry_node.node.entity_interact_block"))
                .addRow(new PortRow(null, StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                // 输出：发起交互的实体
                .addRow(new PortRow(null, StandardPorts.TRIGGER_ENTITY.toOutput(), UIHint.DEFAULT, null, null))
                // 输出：被点击的方块坐标
                .addRow(new PortRow(null, StandardPorts.XYZ.toOutput(), UIHint.DEFAULT, null, null))
                // 输出：被点击的方块状态
                .addRow(new PortRow(null, StandardPorts.BLOCK_STATE.toOutput(), UIHint.DEFAULT, null, null))
                .build();
    }
}