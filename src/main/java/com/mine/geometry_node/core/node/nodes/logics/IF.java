package com.mine.geometry_node.core.node.nodes.logics;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.node.definition.node.NodeComment;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.nodes.*;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import net.minecraft.network.chat.Component;

public class IF extends BaseNode {

    public static final String TYPE_ID = "if_branch";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.FLOW_CONTROL, Component.translatable("geometry_node.node.if_branch"))
                .comment(NodeComment.builder(TYPE_ID)
                        .text("summary")
                        .output(StandardPorts.FLOW_TRUE, "flow_true")
                        .output(StandardPorts.FLOW_FALSE, "flow_false")
                        .input(StandardPorts.FLOW_IN, "flow_in")
                        .input(StandardPorts.BOOL, "bool")
                        .build())
                .addRow(new PortRow(
                        StandardPorts.FLOW_IN.toExec(),
                        StandardPorts.FLOW_TRUE.toExec(),
                        UIHint.DEFAULT, null, null
                ))
                .addRow(new PortRow(
                        null,
                        StandardPorts.FLOW_FALSE.toExec(),
                        UIHint.DEFAULT, null, null
                ))
                .addPassthroughInput(StandardPorts.BOOL.toInput(), UIHint.DEFAULT, null, null)
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        Boolean isTrue = getInput(context, StandardPorts.BOOL.getId(), Boolean.class);

        if (Boolean.TRUE.equals(isTrue)) {
            return next(StandardPorts.FLOW_TRUE.getId());
        } else {
            return next(StandardPorts.FLOW_FALSE.getId());
        }
    }
}
