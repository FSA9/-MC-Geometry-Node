package com.mine.geometry_node.core.node.nodes.dialogue;

import com.mine.geometry_node.core.engine.blueprint.execution.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.execution.ExecutionResult;
import com.mine.geometry_node.core.engine.dialogue.DialogueContext;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.Map;

public class BeginDialogue extends BaseNode {
    public static final String TYPE_ID = "begin_dialogue";
    private static final String[] STYLE_OPTIONS = {"default", "rpg"};

    public static final String PLAYER = StandardPorts.PLAYER.getId();
    public static final String SPEAKER = StandardPorts.SPEAKER.getId();
    public static final String STYLE_ID = StandardPorts.STYLE_ID.getId();

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.DIALOGUE, Component.translatable("geometry_node.node.begin_dialogue"))
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.PLAYER.toInput(), null, UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.SPEAKER.toInput(""), null, UIHint.INPUT, null, null))
                .addRow(new PortRow(
                        StandardPorts.STYLE_ID.toInput("default"),
                        null,
                        UIHint.SELECT,
                        null,
                        Map.of(PortMetaKeys.OPTIONS, STYLE_OPTIONS)
                ))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        ServerPlayer player = resolvePlayer(context);
        String speaker = resolveSpeaker(context);
        String styleId = stringOrDefault(getInput(context, STYLE_ID, String.class), "default");

        context.setTempData(DialogueContext.TEMP_KEY, new DialogueContext(player, speaker, styleId));
        return next(StandardPorts.FLOW_OUT.getId());
    }

    private ServerPlayer resolvePlayer(ExecutionContext context) {
        Entity playerInput = getInput(context, PLAYER, Entity.class);
        if (playerInput instanceof ServerPlayer serverPlayer) {
            return serverPlayer;
        }
        Object triggerEntity = context.getEventData(StandardPorts.TRIGGER_ENTITY.getId());
        if (triggerEntity instanceof ServerPlayer serverPlayer) {
            return serverPlayer;
        }
        Object eventEntity = context.getEventData(StandardPorts.ENTITY.getId());
        if (eventEntity instanceof ServerPlayer serverPlayer) {
            return serverPlayer;
        }
        Entity owner = context.getEntity();
        if (owner instanceof ServerPlayer serverPlayer) {
            return serverPlayer;
        }
        return null;
    }

    private String resolveSpeaker(ExecutionContext context) {
        String explicit = getInput(context, SPEAKER, String.class);
        if (explicit != null && !explicit.isBlank()) {
            return explicit;
        }
        Entity owner = context.getEntity();
        if (owner != null && !(owner instanceof ServerPlayer)) {
            return owner.getDisplayName().getString();
        }
        Object eventEntity = context.getEventData(StandardPorts.ENTITY.getId());
        if (eventEntity instanceof Entity entity && !(entity instanceof ServerPlayer)) {
            return entity.getDisplayName().getString();
        }
        return "";
    }

    private static String stringOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
