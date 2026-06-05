package com.mine.geometry_node.core.node.nodes.dialogue;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.engine.dialogue.DialogueRuntime;
import com.mine.geometry_node.core.engine.dialogue.context.DialogueContext;
import com.mine.geometry_node.core.engine.dialogue.session.DialogueCloseReason;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

public class CloseDialogue extends BaseNode {
    public static final String TYPE_ID = "close_dialogue";
    public static final String PLAYER = StandardPorts.PLAYER.getId();

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.DIALOGUE, Component.translatable("geometry_node.node.close_dialogue"))
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.PLAYER.toInput(), null, UIHint.DEFAULT, null, null))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        ServerPlayer player = resolvePlayer(context);
        if (player != null) {
            DialogueRuntime.INSTANCE.closeForPlayer(player, DialogueCloseReason.NODE);
        }
        return next(StandardPorts.FLOW_OUT.getId());
    }

    private ServerPlayer resolvePlayer(ExecutionContext context) {
        Entity target = getInput(context, PLAYER, Entity.class);
        if (target instanceof ServerPlayer player) {
            return player;
        }
        Object dialogueContext = context.getTempData(DialogueContext.TEMP_KEY);
        if (dialogueContext instanceof DialogueContext contextValue && contextValue.player() != null) {
            return contextValue.player();
        }
        Object triggerEntity = context.getEventData(StandardPorts.TRIGGER_ENTITY.getId());
        if (triggerEntity instanceof ServerPlayer player) {
            return player;
        }
        Object eventEntity = context.getEventData(StandardPorts.ENTITY.getId());
        if (eventEntity instanceof ServerPlayer player) {
            return player;
        }
        Entity owner = context.getEntity();
        return owner instanceof ServerPlayer player ? player : null;
    }
}
