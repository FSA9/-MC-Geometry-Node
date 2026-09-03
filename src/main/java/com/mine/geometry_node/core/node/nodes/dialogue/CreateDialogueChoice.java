package com.mine.geometry_node.core.node.nodes.dialogue;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.PortDef;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.PortType;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import com.mine.geometry_node.core.node.value.DialogueChoiceValue;
import com.mine.geometry_node.core.node.value.RichTextValue;
import net.minecraft.network.chat.Component;

public class CreateDialogueChoice extends BaseNode {
    public static final String TYPE_ID = "create_dialogue_choice";
    private static final String TEXT = "text";
    private static final String DISABLED_REASON = "disabled_reason";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.DATA, Component.translatable("geometry_node.node.create_dialogue_choice"))
                .addRow(new PortRow(
                        null,
                        StandardPorts.DIALOGUE_CHOICE.toOutput(),
                        UIHint.DEFAULT, null, null
                ))
                .addPassthroughInput(PortDef.create(TEXT, "geometry_node.port.choice_text", PortType.RICH_TEXT, RichTextValue.EMPTY), UIHint.INPUT)
                .addPassthroughInput(StandardPorts.CHOICE_VISIBLE.toInput(true), UIHint.CHECKBOX)
                .addPassthroughInput(StandardPorts.CHOICE_ENABLED.toInput(true), UIHint.CHECKBOX)
                .addPassthroughInput(PortDef.create(DISABLED_REASON, "geometry_node.port.choice_disabled_reason", PortType.RICH_TEXT, RichTextValue.EMPTY), UIHint.INPUT)
                .build();
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        if (!StandardPorts.DIALOGUE_CHOICE.getId().equals(portName)) {
            return null;
        }

        RichTextValue text = richText(context, TEXT);
        boolean visible = !Boolean.FALSE.equals(getInput(context, StandardPorts.CHOICE_VISIBLE.getId(), Boolean.class));
        boolean enabled = !Boolean.FALSE.equals(getInput(context, StandardPorts.CHOICE_ENABLED.getId(), Boolean.class));
        RichTextValue disabledReason = enabled ? RichTextValue.EMPTY : richText(context, DISABLED_REASON);
        return new DialogueChoiceValue(text, visible, enabled, disabledReason);
    }

    private RichTextValue richText(ExecutionContext context, String portName) {
        RichTextValue value = getInput(context, portName, RichTextValue.class);
        return value == null ? RichTextValue.EMPTY : value;
    }
}
