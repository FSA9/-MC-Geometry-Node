package com.mine.geometry_node.core.node.nodes.dialogue;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.engine.system.dialogue.DialogueContext;
import com.mine.geometry_node.core.engine.system.dialogue.DialogueSession;
import com.mine.geometry_node.core.engine.system.dialogue.DialogueStyleRegistry;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.PortDef;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.PortType;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.Map;

public class BeginDialogue extends BaseNode {
    public static final String TYPE_ID = "begin_dialogue";
    private static final String[] STYLE_OPTIONS = {DialogueStyleRegistry.DEFAULT, DialogueStyleRegistry.RPG};

    public static final String PLAYER = StandardPorts.PLAYER.getId();
    public static final String DIALOGUE_ENTITY = StandardPorts.SPEAKER_ENTITY.getId();
    public static final String STYLE_ID = StandardPorts.STYLE_ID.getId();
    public static final String ALLOW_MULTI_PLAYER = "allow_multi_player";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.DIALOGUE, Component.translatable("geometry_node.node.begin_dialogue"))
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addPassthroughInput(StandardPorts.PLAYER.toInput(), UIHint.DEFAULT)
                .addPassthroughInput(StandardPorts.SPEAKER_ENTITY.toInput(), UIHint.DEFAULT)
                .addPassthroughInput(StandardPorts.STYLE_ID.toInput(DialogueStyleRegistry.DEFAULT), UIHint.SELECT, null, Map.of(PortMetaKeys.OPTIONS, STYLE_OPTIONS))
                .addPassthroughInput(PortDef.create(ALLOW_MULTI_PLAYER, "geometry_node.port.allow_multi_player", PortType.BOOLEAN, true), UIHint.CHECKBOX)
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        ServerPlayer player = resolvePlayer(context);
        Entity dialogueEntity = resolveDialogueEntity(context);
        String styleId = stringOrDefault(getInput(context, STYLE_ID, String.class), DialogueStyleRegistry.DEFAULT);
        DialogueSession.Policy policy = new DialogueSession.Policy(
                boolOrDefault(getInput(context, ALLOW_MULTI_PLAYER, Boolean.class), true)
        );

        context.setTempData(DialogueContext.TEMP_KEY, new DialogueContext(
                player,
                dialogueEntity,
                styleId,
                policy
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

    private Entity resolveDialogueEntity(ExecutionContext context) {
        Entity explicitDialogueEntity = getInput(context, DIALOGUE_ENTITY, Entity.class);
        if (explicitDialogueEntity != null) {
            return explicitDialogueEntity;
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

    private static String stringOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static boolean boolOrDefault(Boolean value, boolean fallback) {
        return value == null ? fallback : value;
    }

}
