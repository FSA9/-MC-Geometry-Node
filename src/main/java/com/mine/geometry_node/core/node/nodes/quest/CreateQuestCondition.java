package com.mine.geometry_node.core.node.nodes.quest;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.PortDef;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.PortType;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import com.mine.geometry_node.core.node.value.QuestConditionValue;
import net.minecraft.network.chat.Component;

public final class CreateQuestCondition extends BaseNode {
    public static final String TYPE_ID = "create_quest_condition";
    public static final String DISPLAY_TEXT_PORT = "display_text";
    public static final String CONDITION_PORT = "condition";
    public static final String FAILURE_TEXT_PORT = "failure_text";
    public static final String OUTPUT_PORT = "quest_condition";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(
                        TYPE_ID,
                        NodeType.QUEST,
                        Component.translatable("geometry_node.node.create_quest_condition"))
                .addRow(new PortRow(
                        null,
                        PortDef.create(OUTPUT_PORT, "geometry_node.port.quest_condition_value", PortType.QUEST_CONDITION),
                        UIHint.DEFAULT,
                        null,
                        null))
                .addRow(new PortRow(
                        PortDef.create(DISPLAY_TEXT_PORT, "geometry_node.port.quest_condition_display_text", PortType.STRING, "").hiddenPin(),
                        null,
                        UIHint.INPUT,
                        null,
                        null))
                .addRow(new PortRow(
                        PortDef.create(CONDITION_PORT, "geometry_node.port.quest_condition_check", PortType.BOOLEAN, false),
                        null,
                        UIHint.DEFAULT,
                        null,
                        null))
                .addRow(new PortRow(
                        PortDef.create(FAILURE_TEXT_PORT, "geometry_node.port.quest_condition_failure_text", PortType.STRING, "").hiddenPin(),
                        null,
                        UIHint.INPUT,
                        null,
                        null))
                .build();
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        if (!OUTPUT_PORT.equals(portName)) return null;

        String displayText = getInput(context, DISPLAY_TEXT_PORT, String.class);
        Boolean allowed = getInput(context, CONDITION_PORT, Boolean.class);
        String failureText = getInput(context, FAILURE_TEXT_PORT, String.class);
        return new QuestConditionValue(displayText, Boolean.TRUE.equals(allowed), failureText);
    }
}
