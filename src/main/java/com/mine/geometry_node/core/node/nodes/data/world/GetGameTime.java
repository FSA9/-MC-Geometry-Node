package com.mine.geometry_node.core.node.nodes.data.world;

import com.mine.geometry_node.core.engine.graph.data.GraphDataContext;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

public class GetGameTime extends BaseNode {

    public static final String TYPE_ID = "get_game_time";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.DATA, Component.translatable("geometry_node.node.get_game_time"))
                .addRow(new PortRow(null, StandardPorts.GAME_TIME.toOutput(), UIHint.DEFAULT, null, null))
                .build();
    }

    @Override
    public Object compute(GraphDataContext context, String portName) {
        if (!StandardPorts.GAME_TIME.getId().equals(portName)) {
            return null;
        }
        ServerLevel level = context != null ? context.getLevel() : null;
        return level != null ? level.getGameTime() : 0L;
    }
}
