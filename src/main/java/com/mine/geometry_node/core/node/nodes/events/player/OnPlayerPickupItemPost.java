package com.mine.geometry_node.core.node.nodes.events.player;

import com.mine.geometry_node.core.node.nodes.*;
import com.mine.geometry_node.core.node.nodes.events.BaseEventNode;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;

public class OnPlayerPickupItemPost extends BaseEventNode {

    public static final String TYPE_ID = "on_entity_pickup_item_post";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.EVENT, Component.translatable("geometry_node.node.on_entity_pickup_item_post"))
                // 1. 事件触发后的执行流输出
                .addRow(new PortRow(null, StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                // 2. 触发事件的实体（拾取者）
                .addRow(new PortRow(null, StandardPorts.ENTITY.toOutput(), UIHint.DEFAULT, null, null))
                // 3. 已经被拾取的具体物品栈 (ItemStack)
                .addRow(new PortRow(null, StandardPorts.ITEM_STACK.toOutput(), UIHint.DEFAULT, null, null))
                .build();
    }
}