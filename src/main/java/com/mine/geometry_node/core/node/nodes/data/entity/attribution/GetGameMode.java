package com.mine.geometry_node.core.node.nodes.data.entity.attribution;

import com.mine.geometry_node.core.engine.graph.data.GraphDataContext;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.nodes.*;
import com.mine.geometry_node.core.node.definition.port.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;


public class GetGameMode extends BaseNode {

    public static final String TYPE_ID = "get_game_mode";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.DATA, Component.translatable("geometry_node.node.get_game_mode"))
                .addRow(new PortRow(null, PortDef.create("gamemode", "geometry_node.port.gamemode", PortType.STRING), UIHint.DEFAULT, null, null))
                .addPassthroughInput(StandardPorts.ENTITY.toInput(), UIHint.DEFAULT, null, null)
                .build();
    }

    @Override
    public Object compute(GraphDataContext context, String portName) {
        if (!"gamemode".equals(portName)) return null;

        Entity entity = getInputFromList(context, StandardPorts.ENTITY.getId(), 0, Entity.class);
        if (entity == null) return null;

        if (entity instanceof ServerPlayer serverPlayer) {
            return serverPlayer.gameMode.getGameModeForPlayer().getName();
        }

        return null;
    }
}
