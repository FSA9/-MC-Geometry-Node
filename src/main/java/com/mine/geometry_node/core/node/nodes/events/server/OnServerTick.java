package com.mine.geometry_node.core.node.nodes.events.server;

import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.nodes.events.BaseEventNode;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;

public class OnServerTick extends BaseEventNode {

    public static final String TYPE_ID = "on_server_tick";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.EVENT, Component.translatable("geometry_node.node.on_server_tick"))
                .addRow(new PortRow(null, StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))

                // 执行间隔与偏移
                .addRow(new PortRow(StandardPorts.INTERVAL.toInput(1), null, UIHint.INPUT, null, null))
                .addRow(new PortRow(StandardPorts.OFFSET.toInput(0), null, UIHint.INPUT, null, null))
                .build();
    }
}