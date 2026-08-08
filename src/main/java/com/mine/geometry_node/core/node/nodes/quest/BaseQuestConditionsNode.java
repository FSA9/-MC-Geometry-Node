package com.mine.geometry_node.core.node.nodes.quest;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.system.quest.model.QuestConditionKind;
import com.mine.geometry_node.core.engine.system.quest.model.QuestConditionResult;
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
import com.mine.geometry_node.core.node.port.UIHint;
import com.mine.geometry_node.core.node.value.QuestConditionValue;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Map;

public abstract class BaseQuestConditionsNode extends BaseNode {
    public static final String RESULT_PORT = "result";
    public static final String CONDITION_PREFIX = "condition_";
    public static final int DEFAULT_CONDITION_COUNT = 1;
    public static final int MAX_CONDITION_COUNT = 32;

    private final QuestConditionKind kind;
    private final String titleTranslationKey;

    protected BaseQuestConditionsNode(QuestConditionKind kind, String titleTranslationKey) {
        this.kind = kind;
        this.titleTranslationKey = titleTranslationKey;
    }

    @Override
    public final NodeDef getDefaultDefinition() {
        return buildDefinition(DEFAULT_CONDITION_COUNT);
    }

    @Override
    public final NodeDef getDefinition(NodeData instanceData) {
        return buildDefinition(resolveConditionCount(instanceData));
    }

    private NodeDef buildDefinition(int conditionCount) {
        NodeDef.Builder builder = NodeDef.builder(
                        kind.nodeTypeId(),
                        NodeType.QUEST,
                        Component.translatable(titleTranslationKey))
                .addMeta(SchemaKeys.MIN_DYNAMIC_INPUT, DEFAULT_CONDITION_COUNT)
                .addMeta(SchemaKeys.MAX_DYNAMIC_INPUT, MAX_CONDITION_COUNT);

        for (int i = 1; i <= conditionCount; i++) {
            builder.addRow(dynamicInput(
                    new PortDef(
                            conditionPort(i),
                            Component.translatable("geometry_node.port.quest_condition_value_indexed", i),
                            PortType.QUEST_CONDITION,
                            null,
                            false),
                    UIHint.DEFAULT,
                    i,
                    true));
        }
        return builder.build();
    }

    private static PortRow dynamicInput(PortDef port, UIHint hint, int index, boolean removeButton) {
        return new PortRow(
                port,
                null,
                hint,
                null,
                Map.of(
                        PortMetaKeys.IS_DYNAMIC, true,
                        PortMetaKeys.DYNAMIC_INDEX, index,
                        PortMetaKeys.DYNAMIC_REMOVE_BUTTON, removeButton));
    }

    @Override
    public final Object compute(ExecutionContext context, String portName) {
        if (!RESULT_PORT.equals(portName)) return null;

        int conditionCount = resolveConditionCount(
                context.getStaticInput(StaticKeys.DYNAMIC_BRANCH_INPUT_COUNT.id()));
        for (int i = 1; i <= conditionCount; i++) {
            Object raw = getRawInput(context, conditionPort(i));
            if (raw == null) continue;
            if (!(raw instanceof QuestConditionValue condition)) {
                return QuestConditionResult.denied(List.of());
            }
            if (condition.allowed()) continue;
            return QuestConditionResult.denied(condition.failureText().isBlank()
                    ? List.of()
                    : List.of(condition.failureText()));
        }
        return QuestConditionResult.passed();
    }

    public static String conditionPort(int index) {
        return CONDITION_PREFIX + index;
    }

    public static int resolveConditionCount(NodeData instanceData) {
        Object count = instanceData == null || instanceData.inputs == null
                ? null
                : instanceData.inputs.get(StaticKeys.DYNAMIC_BRANCH_INPUT_COUNT.id());
        return resolveConditionCount(count);
    }

    public static int resolveConditionCount(Object count) {
        int resolved = DEFAULT_CONDITION_COUNT;
        if (count instanceof Number number) {
            resolved = number.intValue();
        } else if (count instanceof String text) {
            try {
                resolved = Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
            }
        }
        return Math.max(DEFAULT_CONDITION_COUNT, Math.min(resolved, MAX_CONDITION_COUNT));
    }
}
