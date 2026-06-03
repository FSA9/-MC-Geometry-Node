package com.mine.geometry_node.core.node.nodes.dialogue;

import com.mine.geometry_node.core.engine.blueprint.execution.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.execution.ExecutionResult;
import com.mine.geometry_node.core.engine.dialogue.DialogueRuntime;
import com.mine.geometry_node.core.engine.dialogue.context.DialogueContext;
import com.mine.geometry_node.core.engine.dialogue.payload.DialogueChoicePayload;
import com.mine.geometry_node.core.engine.dialogue.payload.DialoguePagePayload;
import com.mine.geometry_node.core.engine.dialogue.payload.DialogueWaitRequest;
import com.mine.geometry_node.core.engine.graph.GraphKind;
import com.mine.geometry_node.core.node.NodeData;
import com.mine.geometry_node.core.node.meta.MetaKey;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.meta.SchemaKeys;
import com.mine.geometry_node.core.node.meta.StaticKeys;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ShowDialoguePage extends BaseNode {
    public static final String TYPE_ID = "show_dialogue_page";

    public static final String SPEAKER = StandardPorts.SPEAKER.getId();
    public static final String TEXT_KEY = StandardPorts.TEXT_KEY.getId();
    public static final String CLOSED = StandardPorts.CLOSED.getId();
    public static final int DEFAULT_CHOICE_COUNT = 0;
    public static final int MAX_CHOICE_COUNT = 10;

    @Override
    public NodeDef getDefaultDefinition() {
        return buildDef(DEFAULT_CHOICE_COUNT);
    }

    @Override
    public NodeDef getDefinition(NodeData instanceData) {
        return buildDef(resolveChoiceCount(instanceData));
    }

    private NodeDef buildDef(int choiceCount) {
        NodeDef.Builder builder = NodeDef.builder(TYPE_ID, NodeType.DIALOGUE, Component.translatable("geometry_node.node.show_dialogue_page"))
                .addMeta(SchemaKeys.MAX_DYNAMIC_OUTPUT, MAX_CHOICE_COUNT)
                .addMeta(SchemaKeys.MIN_DYNAMIC_OUTPUT, 0)
                .addRow(new PortRow(
                        StandardPorts.FLOW_IN.toExec(),
                        choiceCount == 0 ? StandardPorts.FLOW_OUT.toExec() : null,
                        UIHint.DEFAULT, null, null
                ))
                .addRow(new PortRow(
                        StandardPorts.SPEAKER.toInput(""),
                        null,
                        UIHint.INPUT, null, null
                ))
                .addRow(new PortRow(
                        StandardPorts.TEXT_KEY.toInput(""),
                        null,
                        UIHint.INPUT, null, null
                ));

        for (int i = 1; i <= choiceCount; i++) {
            builder.addRow(new PortRow(
                    null,
                    StandardPorts.CHOICE.toExecWithIndex(i),
                    UIHint.DEFAULT, null, dynamicGroupHead(i)
            ));
            builder.addRow(new PortRow(
                    StandardPorts.CHOICE_TEXT_KEY.toInputWithIndex(i, ""),
                    null, UIHint.INPUT, null, dynamicGroupRow()
            ));
            builder.addRow(new PortRow(
                    StandardPorts.CHOICE_VISIBLE.toInputWithIndex(i, true),
                    null, UIHint.CHECKBOX, null, dynamicGroupRow()
            ));
            builder.addRow(new PortRow(
                    StandardPorts.CHOICE_ENABLED.toInputWithIndex(i, true),
                    null, UIHint.CHECKBOX, null, dynamicGroupRow()
            ));
            builder.addRow(new PortRow(
                    StandardPorts.CHOICE_DISABLED_REASON_KEY.toInputWithIndex(i, ""),
                    null, UIHint.INPUT, null, dynamicGroupRow()
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
            return next(CLOSED);
        }

        DialogueContext dialogueContext = getDialogueContext(context);
        String speaker = stringOrDefault(getInput(context, SPEAKER, String.class),
                dialogueContext != null ? dialogueContext.speaker() : "");
        if (dialogueContext == null) {
            dialogueContext = createFallbackDialogueContext(context, player, speaker);
            context.setTempData(DialogueContext.TEMP_KEY, dialogueContext);
        }
        String textKey = stringOrEmpty(getInput(context, TEXT_KEY, String.class));
        String styleId = dialogueContext != null ? dialogueContext.styleId() : "default";
        String bodyText = DialogueRuntime.INSTANCE.getTextManager().resolveText(textKey, textKey);

        List<DialogueChoicePayload> choices = new ArrayList<>();
        int choiceCount = resolveRuntimeChoiceCount(context);
        for (int i = 1; i <= choiceCount; i++) {
            Boolean visible = getInput(context, choiceVisiblePort(i), Boolean.class);
            if (Boolean.FALSE.equals(visible)) {
                continue;
            }
            String choiceTextKey = stringOrEmpty(getInput(context, choiceTextKeyPort(i), String.class));
            String choiceText = DialogueRuntime.INSTANCE.getTextManager().resolveText(choiceTextKey, choiceTextKey);
            if (choiceText.isBlank()) {
                continue;
            }
            Boolean enabled = getInput(context, choiceEnabledPort(i), Boolean.class);
            boolean choiceEnabled = !Boolean.FALSE.equals(enabled);
            String disabledReason = choiceEnabled
                    ? ""
                    : resolveChoiceDisabledReason(context, i);
            choices.add(new DialogueChoicePayload(
                    choiceOutputPort(i),
                    choiceText,
                    null,
                    choiceEnabled,
                    disabledReason,
                    Map.of()
            ));
        }

        if (choices.isEmpty() && choiceCount == 0) {
            String continueText = DialogueRuntime.INSTANCE.getTextManager().resolveText("geometry_node.dialogue.continue", "Continue");
            choices.add(new DialogueChoicePayload(StandardPorts.FLOW_OUT.getId(), continueText, null, true, null, Map.of()));
        } else if (choices.isEmpty()) {
            choices.add(new DialogueChoicePayload(CLOSED, "Close", null, true, null, Map.of()));
        }

        DialoguePagePayload page = new DialoguePagePayload(
                "node:" + context.getCurrentNodeId(),
                speaker,
                bodyText,
                styleId,
                choices,
                Map.of()
        );
        return ExecutionResult.externalWait(GraphKind.DIALOGUE, new DialogueWaitRequest(player, page));
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

    private DialogueContext createFallbackDialogueContext(ExecutionContext context, ServerPlayer player, String speaker) {
        Entity targetEntity = context.getEntity();
        return new DialogueContext(player, null, targetEntity, speaker, "default", context.getGraphId(), "root");
    }

    private static int resolveChoiceCount(NodeData instanceData) {
        Object countObj = instanceData == null || instanceData.inputs == null
                ? null
                : instanceData.inputs.get(StaticKeys.DYNAMIC_BRANCH_OUTPUT_COUNT.id());
        if (countObj != null) {
            return resolveChoiceCount(countObj);
        }

        int inferred = DEFAULT_CHOICE_COUNT;
        if (instanceData != null) {
            inferred = Math.max(inferred, inferMaxChoiceIndex(instanceData.inputs == null ? null : instanceData.inputs.keySet()));
            inferred = Math.max(inferred, inferMaxChoiceIndex(instanceData.execOutputs == null ? null : instanceData.execOutputs.keySet()));
            if (instanceData.portSettings != null) {
                inferred = Math.max(inferred, inferMaxChoiceIndex(instanceData.portSettings.inputs == null ? null : instanceData.portSettings.inputs.keySet()));
                inferred = Math.max(inferred, inferMaxChoiceIndex(instanceData.portSettings.execOutputs == null ? null : instanceData.portSettings.execOutputs.keySet()));
            }
        }
        return clampChoiceCount(inferred);
    }

    private static int resolveChoiceCount(Object countObj) {
        int choiceCount = DEFAULT_CHOICE_COUNT;
        if (countObj instanceof Number number) {
            choiceCount = number.intValue();
        } else if (countObj instanceof String string) {
            try {
                choiceCount = Integer.parseInt(string);
            } catch (NumberFormatException ignored) {
            }
        }
        return clampChoiceCount(choiceCount);
    }

    private static int resolveRuntimeChoiceCount(ExecutionContext context) {
        Object countObj = context.getStaticInput(StaticKeys.DYNAMIC_BRANCH_OUTPUT_COUNT.id());
        if (countObj != null) {
            return resolveChoiceCount(countObj);
        }

        int inferred = DEFAULT_CHOICE_COUNT;
        for (int i = 1; i <= MAX_CHOICE_COUNT; i++) {
            if (context.hasPort(choiceOutputPort(i))
                    || context.hasPort(choiceTextKeyPort(i))
                    || context.hasPort(choiceVisiblePort(i))
                    || context.hasPort(choiceEnabledPort(i))
                    || context.hasPort(choiceDisabledReasonKeyPort(i))) {
                inferred = i;
            }
        }
        return clampChoiceCount(inferred);
    }

    private static int inferMaxChoiceIndex(Iterable<String> portIds) {
        int max = 0;
        if (portIds == null) {
            return max;
        }
        for (String portId : portIds) {
            max = Math.max(max, parseChoiceIndex(portId));
        }
        return max;
    }

    private static int parseChoiceIndex(String portId) {
        int index = parseIndexedPort(portId, StandardPorts.CHOICE_TEXT_KEY.getId());
        if (index > 0) {
            return index;
        }
        index = parseIndexedPort(portId, StandardPorts.CHOICE_VISIBLE.getId());
        if (index > 0) {
            return index;
        }
        index = parseIndexedPort(portId, StandardPorts.CHOICE_ENABLED.getId());
        if (index > 0) {
            return index;
        }
        index = parseIndexedPort(portId, StandardPorts.CHOICE_DISABLED_REASON_KEY.getId());
        if (index > 0) {
            return index;
        }
        return parseIndexedPort(portId, StandardPorts.CHOICE.getId());
    }

    private static int parseIndexedPort(String portId, String baseId) {
        if (portId == null) {
            return 0;
        }
        String prefix = baseId + "_";
        if (!portId.startsWith(prefix)) {
            return 0;
        }
        try {
            return Integer.parseInt(portId.substring(prefix.length()));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static int clampChoiceCount(int choiceCount) {
        return Math.max(0, Math.min(choiceCount, MAX_CHOICE_COUNT));
    }

    private static Map<MetaKey<?>, Object> dynamicGroupHead(int index) {
        return Map.of(
                PortMetaKeys.IS_DYNAMIC, true,
                PortMetaKeys.DYNAMIC_INDEX, index
        );
    }

    private static Map<MetaKey<?>, Object> dynamicGroupRow() {
        return Map.of(PortMetaKeys.IS_DYNAMIC, true);
    }

    private static String choiceOutputPort(int index) {
        return StandardPorts.CHOICE.getIdWithIndex(index);
    }

    private static String choiceTextKeyPort(int index) {
        return StandardPorts.CHOICE_TEXT_KEY.getIdWithIndex(index);
    }

    private static String choiceVisiblePort(int index) {
        return StandardPorts.CHOICE_VISIBLE.getIdWithIndex(index);
    }

    private static String choiceEnabledPort(int index) {
        return StandardPorts.CHOICE_ENABLED.getIdWithIndex(index);
    }

    private static String choiceDisabledReasonKeyPort(int index) {
        return StandardPorts.CHOICE_DISABLED_REASON_KEY.getIdWithIndex(index);
    }

    private String resolveChoiceDisabledReason(ExecutionContext context, int index) {
        String reasonKey = stringOrEmpty(getInput(context, choiceDisabledReasonKeyPort(index), String.class));
        return DialogueRuntime.INSTANCE.getTextManager().resolveText(reasonKey, reasonKey);
    }

    private static String stringOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String stringOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
