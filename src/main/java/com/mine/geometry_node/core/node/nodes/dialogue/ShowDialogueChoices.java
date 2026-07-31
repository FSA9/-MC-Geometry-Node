package com.mine.geometry_node.core.node.nodes.dialogue;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.engine.dialogue.DialogueContext;
import com.mine.geometry_node.core.engine.dialogue.model.DialogueChoicePayload;
import com.mine.geometry_node.core.engine.dialogue.model.DialoguePagePayload;
import com.mine.geometry_node.core.engine.dialogue.model.DialogueText;
import com.mine.geometry_node.core.engine.dialogue.DialogueWaitRequest;
import com.mine.geometry_node.core.engine.dialogue.richtext.DialogueRoundParser;
import com.mine.geometry_node.core.engine.graph.GraphKind;
import com.mine.geometry_node.core.node.NodeData;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.meta.SchemaKeys;
import com.mine.geometry_node.core.node.meta.StaticKeys;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortDef;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.PortType;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import com.mine.geometry_node.core.node.value.DialogueChoiceValue;
import com.mine.geometry_node.core.node.value.RichTextValue;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ShowDialogueChoices extends BaseNode {
    public static final String TYPE_ID = "show_dialogue_choices";
    private static final String TEXT = "text";
    private static final int DEFAULT_CHOICE_COUNT = 1;
    private static final int MAX_CHOICE_COUNT = 10;

    @Override
    public NodeDef getDefaultDefinition() {
        return buildDef(DEFAULT_CHOICE_COUNT);
    }

    @Override
    public NodeDef getDefinition(NodeData instanceData) {
        Object countValue = instanceData == null || instanceData.inputs == null
                ? null
                : instanceData.inputs.get(StaticKeys.DYNAMIC_BRANCH_OUTPUT_COUNT.id());
        return buildDef(resolveChoiceCount(countValue));
    }

    private NodeDef buildDef(int choiceCount) {
        NodeDef.Builder builder = NodeDef.builder(TYPE_ID, NodeType.DIALOGUE, Component.translatable("geometry_node.node.show_dialogue_choices"))
                .addMeta(SchemaKeys.MAX_DYNAMIC_OUTPUT, MAX_CHOICE_COUNT)
                .addMeta(SchemaKeys.MIN_DYNAMIC_OUTPUT, 1)
                .addRow(new PortRow(
                        StandardPorts.FLOW_IN.toExec(),
                        null,
                        UIHint.DEFAULT, null, null
                ))
                .addRow(new PortRow(
                        PortDef.create(TEXT, "geometry_node.port.message", PortType.RICH_TEXT, RichTextValue.EMPTY),
                        null,
                        UIHint.INPUT, null, null
                ));

        for (int i = 1; i <= choiceCount; i++) {
            builder.addRow(new PortRow(
                    StandardPorts.DIALOGUE_CHOICE.toInputWithIndex(i),
                    StandardPorts.SELECTED.toExecWithIndex(i),
                    UIHint.DEFAULT,
                    null,
                    Map.of(
                            PortMetaKeys.IS_DYNAMIC, true,
                            PortMetaKeys.DYNAMIC_INDEX, i
                    )
            ));
        }

        builder.addRow(new PortRow(
                null,
                StandardPorts.CLOSED.toExec(),
                UIHint.DEFAULT, null, null
        ));
        return builder.build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        ServerPlayer player = resolvePlayer(context);
        if (player == null) {
            return next(StandardPorts.CLOSED.getId());
        }

        DialogueContext dialogueContext = resolveDialogueContext(context, player);
        List<DialogueChoicePayload> choices = resolveChoices(context);
        if (choices.isEmpty()) {
            choices = List.of(new DialogueChoicePayload(
                    StandardPorts.CLOSED.getId(),
                    DialogueText.rich(RichTextValue.plain("关闭")),
                    new DialogueChoicePayload.ResumePort(StandardPorts.CLOSED.getId()),
                    true,
                    DialogueText.EMPTY
            ));
        }

        RichTextValue bodyText = richText(context, TEXT);
        List<RichTextValue> rounds = DialogueRoundParser.split(bodyText);
        List<DialoguePagePayload> pages = new ArrayList<>(rounds.size());
        String basePageId = "node:" + context.getCurrentNodeId();
        for (int i = 0; i < rounds.size(); i++) {
            boolean finalRound = i == rounds.size() - 1;
            String pageId = rounds.size() == 1 ? basePageId : basePageId + ":round:" + (i + 1);
            pages.add(DialoguePagePayload.text(
                    pageId,
                    DialogueText.rich(rounds.get(i)),
                    dialogueContext.styleId(),
                    finalRound ? choices : List.of(continuePageChoice(i))
            ));
        }

        return ExecutionResult.externalWait(GraphKind.DIALOGUE, new DialogueWaitRequest(dialogueContext, pages));
    }

    private List<DialogueChoicePayload> resolveChoices(ExecutionContext context) {
        int choiceCount = resolveChoiceCount(context.getStaticInput(StaticKeys.DYNAMIC_BRANCH_OUTPUT_COUNT.id()));
        List<DialogueChoicePayload> choices = new ArrayList<>();
        for (int i = 1; i <= choiceCount; i++) {
            DialogueChoiceValue choice = getInput(context, choiceInputPort(i), DialogueChoiceValue.class);
            if (choice == null || !choice.isValid() || !choice.visible()) {
                continue;
            }
            choices.add(new DialogueChoicePayload(
                    selectedOutputPort(i),
                    DialogueText.rich(choice.text()),
                    new DialogueChoicePayload.ResumePort(selectedOutputPort(i)),
                    choice.enabled(),
                    choice.enabled() ? DialogueText.EMPTY : DialogueText.rich(choice.disabledReason())
            ));
        }
        return choices;
    }

    private static DialogueChoicePayload continuePageChoice(int pageIndex) {
        return new DialogueChoicePayload(
                DialogueChoicePayload.continuePageChoiceId(pageIndex),
                DialogueText.rich(RichTextValue.plain("继续")),
                new DialogueChoicePayload.AdvancePage(pageIndex),
                true,
                DialogueText.EMPTY
        );
    }

    private static int resolveChoiceCount(Object countValue) {
        int count = DEFAULT_CHOICE_COUNT;
        if (countValue instanceof Number number) {
            count = number.intValue();
        } else if (countValue instanceof String string) {
            try {
                count = Integer.parseInt(string);
            } catch (NumberFormatException ignored) {
            }
        }
        return Math.max(1, Math.min(count, MAX_CHOICE_COUNT));
    }

    private static String choiceInputPort(int index) {
        return StandardPorts.DIALOGUE_CHOICE.getIdWithIndex(index);
    }

    private static String selectedOutputPort(int index) {
        return StandardPorts.SELECTED.getIdWithIndex(index);
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
