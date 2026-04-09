package com.mine.geometry_node.core.node.nodes.events.player;

import com.mine.geometry_node.core.node.nodes.*;
import com.mine.geometry_node.core.node.nodes.events.BaseEventNode;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;

public class OnPlayerExecuteCommand extends BaseEventNode {

    public static final String TYPE_ID = "on_player_execute_command";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.EVENT, Component.translatable("geometry_node.node.on_player_execute_command"))
                .addRow(new PortRow(null, StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.ENTITY.toOutput(), UIHint.DEFAULT, null, null))
                // 输出执行的指令字符串
                .addRow(new PortRow(null, StandardPorts.MESSAGE.toOutput(), UIHint.DEFAULT, null, null))
                .build();
    }
}