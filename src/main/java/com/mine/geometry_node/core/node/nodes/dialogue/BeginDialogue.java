package com.mine.geometry_node.core.node.nodes.dialogue;

import com.mine.geometry_node.core.engine.blueprint.execution.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.execution.ExecutionResult;
import com.mine.geometry_node.core.engine.dialogue.context.DialogueContext;
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
    public static final String SPEAKER_ENTITY = StandardPorts.SPEAKER_ENTITY.getId();
    public static final String TARGET_ENTITY = StandardPorts.TARGET_ENTITY.getId();
    public static final String SPEAKER = StandardPorts.SPEAKER.getId();
    public static final String STYLE_ID = StandardPorts.STYLE_ID.getId();
    public static final String GRAPH_ID = StandardPorts.GRAPH_ID.getId();
    public static final String ENTRY_ID = StandardPorts.ENTRY_ID.getId();

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.DIALOGUE, Component.translatable("geometry_node.node.begin_dialogue"))
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.PLAYER.toInput(), null, UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.SPEAKER_ENTITY.toInput(), null, UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.TARGET_ENTITY.toInput(), null, UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.SPEAKER.toInput(""), null, UIHint.INPUT, null, null))
                .addRow(new PortRow(
                        StandardPorts.STYLE_ID.toInput("default"),
                        null,
                        UIHint.SELECT,
                        null,
                        Map.of(PortMetaKeys.OPTIONS, STYLE_OPTIONS)
                ))
                .addRow(new PortRow(StandardPorts.GRAPH_ID.toInput(""), null, UIHint.INPUT, null, null))
                .addRow(new PortRow(StandardPorts.ENTRY_ID.toInput("root"), null, UIHint.INPUT, null, null))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        ServerPlayer player = resolvePlayer(context);
        Entity speakerEntity = resolveSpeakerEntity(context);
        Entity targetEntity = resolveTargetEntity(context, speakerEntity);
        String speaker = resolveSpeaker(context, speakerEntity);
        String styleId = stringOrDefault(getInput(context, STYLE_ID, String.class), "default");
        String graphId = stringOrDefault(getInput(context, GRAPH_ID, String.class), context.getGraphId());
        String entryId = stringOrDefault(getInput(context, ENTRY_ID, String.class), "root");

        context.setTempData(DialogueContext.TEMP_KEY, new DialogueContext(
                player,
                speakerEntity,
                targetEntity,
                speaker,
                styleId,
                graphId,
                entryId
        ));
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

    private Entity resolveSpeakerEntity(ExecutionContext context) {
        Entity explicitSpeakerEntity = getInput(context, SPEAKER_ENTITY, Entity.class);
        if (explicitSpeakerEntity != null) {
            return explicitSpeakerEntity;
        }
        Entity owner = context.getEntity();
        if (owner != null && !(owner instanceof ServerPlayer)) {
            return owner;
        }
        Object eventEntity = context.getEventData(StandardPorts.ENTITY.getId());
        if (eventEntity instanceof Entity entity && !(entity instanceof ServerPlayer)) {
            return entity;
        }
        return null;
    }

    private Entity resolveTargetEntity(ExecutionContext context, Entity speakerEntity) {
        Entity explicitTarget = getInput(context, TARGET_ENTITY, Entity.class);
        if (explicitTarget != null) {
            return explicitTarget;
        }
        if (speakerEntity != null) {
            return speakerEntity;
        }
        Object eventEntity = context.getEventData(StandardPorts.ENTITY.getId());
        if (eventEntity instanceof Entity entity) {
            return entity;
        }
        return context.getEntity();
    }

    private String resolveSpeaker(ExecutionContext context, Entity speakerEntity) {
        String explicit = getInput(context, SPEAKER, String.class);
        if (explicit != null && !explicit.isBlank()) {
            return explicit;
        }
        if (speakerEntity != null) {
            return speakerEntity.getDisplayName().getString();
        }
        return "";
    }

    private static String stringOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
