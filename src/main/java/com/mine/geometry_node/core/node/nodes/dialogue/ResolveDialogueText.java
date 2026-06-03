package com.mine.geometry_node.core.node.nodes.dialogue;

import com.mine.geometry_node.core.engine.blueprint.execution.ExecutionContext;
import com.mine.geometry_node.core.engine.dialogue.DialogueRuntime;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;

public class ResolveDialogueText extends BaseNode {
    public static final String TYPE_ID = "resolve_dialogue_text";
    public static final String TEXT_KEY = StandardPorts.TEXT_KEY.getId();
    public static final String FALLBACK_TEXT = StandardPorts.FALLBACK_TEXT.getId();

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.DIALOGUE, Component.translatable("geometry_node.node.resolve_dialogue_text"))
                .addRow(new PortRow(
                        StandardPorts.TEXT_KEY.toInput(""),
                        StandardPorts.STRING.toOutput(),
                        UIHint.INPUT, null, null
                ))
                .addRow(new PortRow(
                        StandardPorts.FALLBACK_TEXT.toInput(""),
                        null,
                        UIHint.INPUT, null, null
                ))
                .build();
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        if (!StandardPorts.STRING.getId().equals(portName)) {
            return null;
        }
        String key = getInput(context, TEXT_KEY, String.class);
        String fallback = getInput(context, FALLBACK_TEXT, String.class);
        return DialogueRuntime.INSTANCE.getTextManager().resolveText(
                key == null ? "" : key,
                fallback == null ? "" : fallback
        );
    }
}
