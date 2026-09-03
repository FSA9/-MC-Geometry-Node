package com.mine.geometry_node.core.node.nodes.actions.player;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.nodes.*;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.List;

public class SendMessage extends BaseNode {

    public static final String TYPE_ID = "send_message";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.send_message"))
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addPassthroughInput(StandardPorts.ENTITY.toInput(), UIHint.DEFAULT)
                .addPassthroughInput(StandardPorts.MESSAGE.toInput(), UIHint.INPUT)
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        List<Entity> targets = getInputList(context, StandardPorts.ENTITY.getId(), Entity.class);

        String message = getInput(context, StandardPorts.MESSAGE.getId(), String.class);
        if (message == null) message = "";

        if (targets.isEmpty()) {
            if (context.getLevel() != null) {
                for (Player player : context.getLevel().getServer().getPlayerList().getPlayers()) {
                    player.sendSystemMessage(Component.literal(message));
                }
            }
        }
        else {
            for (Entity target : targets) {
                if (target instanceof Player player) {
                    player.sendSystemMessage(Component.literal(message));
                }
            }
        }

        return next(StandardPorts.FLOW_OUT.getId());
    }
}