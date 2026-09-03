package com.mine.geometry_node.core.node.nodes.logics;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.node.definition.node.NodeComment;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import com.mine.geometry_node.core.node.util.ValueTagUtils;
import net.minecraft.network.chat.Component;

public class HasTag extends BaseNode {

    public static final String TYPE_ID = "has_tag";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.LOGIC, Component.translatable("geometry_node.node.has_tag"))
                .comment(NodeComment.builder(TYPE_ID)
                        .text("summary")
                        .output(StandardPorts.BOOL, "bool")
                        .input(StandardPorts.ANY_VALUE, "any_value")
                        .input(StandardPorts.TAG, "tag")
                        .build())
                .addRow(new PortRow(null, StandardPorts.BOOL.toOutput(), UIHint.DEFAULT, null, null))
                .addPassthroughInput(StandardPorts.ANY_VALUE.toInput(), UIHint.DEFAULT, null, null)
                .addPassthroughInput(StandardPorts.TAG.toInput(), UIHint.INPUT, null, null)
                .build();
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        if (!StandardPorts.BOOL.getId().equals(portName)) {
            return null;
        }

        Object value = getRawInput(context, StandardPorts.ANY_VALUE.getId());
        String tag = getInput(context, StandardPorts.TAG.getId(), String.class);
        return ValueTagUtils.hasTag(value, tag, context);
    }
}
