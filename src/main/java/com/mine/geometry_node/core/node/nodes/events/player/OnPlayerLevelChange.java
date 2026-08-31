package com.mine.geometry_node.core.node.nodes.events.player;

import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.nodes.events.BaseEventNode;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import net.minecraft.network.chat.Component;

public class OnPlayerLevelChange extends BaseEventNode {

    public static final String TYPE_ID = "on_player_level_change";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.EVENT, Component.translatable("geometry_node.node.on_player_level_change"))
                .addRow(new PortRow(null, StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.ENTITY.toOutput(), UIHint.DEFAULT, null, null))
                // 输出变化了多少等级 (正数升级，负数掉级)
                .addRow(new PortRow(null, StandardPorts.INT_VALUE.toOutput(), UIHint.DEFAULT, null, null))
                .build();
    }
}
