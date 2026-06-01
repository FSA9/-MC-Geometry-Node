package com.mine.geometry_node.core.node.nodes.events.world;

import com.mine.geometry_node.core.node.nodes.*;
import com.mine.geometry_node.core.node.nodes.events.BaseEventNode;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;

public class OnPortalCreate extends BaseEventNode {

    public static final String TYPE_ID = "on_portal_create";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.EVENT, Component.translatable("geometry_node.node.on_portal_create"))
                // 执行流
                .addRow(new PortRow(null, StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                // 传送门中心位置
                .addRow(new PortRow(null, StandardPorts.XYZ.toOutput(), UIHint.DEFAULT, null, null))
                // 维度信息
                .addRow(new PortRow(null, StandardPorts.DIMENSION.toOutput(), UIHint.DEFAULT, null, null))
                .build();
    }
}