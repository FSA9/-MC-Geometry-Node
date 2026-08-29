package com.mine.geometry_node.core.node.nodes.dialogue;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.engine.system.dialogue.DialogueContext;
import com.mine.geometry_node.core.engine.system.dialogue.model.DialoguePageFactory;
import com.mine.geometry_node.core.engine.system.dialogue.model.DialoguePagePayload;
import com.mine.geometry_node.core.engine.system.dialogue.DialogueWaitRequest;
import com.mine.geometry_node.core.engine.system.dialogue.DialogueRuntime;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortDef;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.PortType;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import com.mine.geometry_node.core.node.value.RichTextValue;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.List;
import java.util.Map;

public class ShowDialoguePage extends BaseNode {
    public static final String TYPE_ID = "show_dialogue_page";
    public static final String TEXT = "text";
    public static final String CLOSED = StandardPorts.CLOSED.getId();
    public static final String ACTION_PREVIEW = "preview_dialogue";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.DIALOGUE, Component.translatable("geometry_node.node.show_dialogue_page"))
                .addRow(new PortRow(
                        StandardPorts.FLOW_IN.toExec(),
                        StandardPorts.FLOW_OUT.toExec(),
                        UIHint.DEFAULT, null, null
                ))
                .addRow(new PortRow(
                        PortDef.create(TEXT, "geometry_node.port.message", PortType.RICH_TEXT, RichTextValue.EMPTY),
                        null,
                        UIHint.INPUT, null, null
                ))
                .addRow(new PortRow(
                        null,
                        null,
                        UIHint.BUTTON,
                        null,
                        Map.of(
                                PortMetaKeys.BUTTON_LABEL, "geometry_node.button.preview",
                                PortMetaKeys.BUTTON_ACTION, ACTION_PREVIEW,
                                PortMetaKeys.BUTTON_COLOR, 0xFF3D6EA8,
                                PortMetaKeys.BUTTON_TEXT_COLOR, 0xFFFFFFFF
                        )
                ))
                .addRow(new PortRow(
                        null,
                        StandardPorts.CLOSED.toExec(),
                        UIHint.DEFAULT, null, null
                ))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        ServerPlayer player = resolvePlayer(context);
        if (player == null) {
            return next(CLOSED);
        }

        DialogueContext dialogueContext = resolveDialogueContext(context, player);
        RichTextValue bodyText = richText(context, TEXT);
        String styleId = dialogueContext.styleId();

        String basePageId = "node:" + context.getCurrentNodeId();
        List<DialoguePagePayload> pages = DialoguePageFactory.textRounds(basePageId, bodyText, styleId);

        return ExecutionResult.externalWait(DialogueRuntime.ID,
                new DialogueWaitRequest(dialogueContext, pages));
    }

    private DialogueContext resolveDialogueContext(ExecutionContext context, ServerPlayer player) {
        DialogueContext dialogueContext = getDialogueContext(context);
        if (dialogueContext == null) {
            dialogueContext = createFallbackDialogueContext(context, player);
            context.setTempData(DialogueContext.TEMP_KEY, dialogueContext);
        } else if (dialogueContext.player() == null) {
            dialogueContext = withPlayer(dialogueContext, player);
            context.setTempData(DialogueContext.TEMP_KEY, dialogueContext);
        }
        return dialogueContext;
    }

    private ServerPlayer resolvePlayer(ExecutionContext context) {
        DialogueContext dialogueContext = getDialogueContext(context);
        if (dialogueContext != null && dialogueContext.player() != null) {
            return dialogueContext.player();
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

    private DialogueContext getDialogueContext(ExecutionContext context) {
        Object value = context.getTempData(DialogueContext.TEMP_KEY);
        return value instanceof DialogueContext dialogueContext ? dialogueContext : null;
    }

    private DialogueContext createFallbackDialogueContext(ExecutionContext context, ServerPlayer player) {
        Entity owner = context.getEntity();
        Entity dialogueEntity = owner instanceof ServerPlayer ? null : owner;
        return new DialogueContext(player, dialogueEntity, "default");
    }

    private DialogueContext withPlayer(DialogueContext dialogueContext, ServerPlayer player) {
        return new DialogueContext(
                player,
                dialogueContext.dialogueEntityId(),
                dialogueContext.styleId(),
                dialogueContext.policy()
        );
    }

    private RichTextValue richText(ExecutionContext context, String portName) {
        RichTextValue value = getInput(context, portName, RichTextValue.class);
        return value == null ? RichTextValue.EMPTY : value;
    }
}
