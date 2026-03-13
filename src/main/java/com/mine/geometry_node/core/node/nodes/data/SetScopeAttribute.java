package com.mine.geometry_node.core.node.nodes.data;

import com.mine.geometry_node.core.execution.ExecutionContext;
import com.mine.geometry_node.core.execution.ExecutionResult;
import com.mine.geometry_node.core.node.nodes.*;
import net.minecraft.network.chat.Component;

public class SetScopeAttribute extends BaseNode {

    public static final String TYPE_ID = "set_scope_attribute";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.DATA, Component.translatable("geometry_node.node.set_scope_attribute"))
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.SCOPE.toInput(), null, UIHint.INPUT, null, null))
                .addRow(new PortRow(StandardPorts.NAME.toInput(), null, UIHint.INPUT, null, null))
                .addRow(new PortRow(StandardPorts.ANY_VALUE.toInput(), null, UIHint.INPUT, null, null))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        String scopeId = getInput(context, StandardPorts.SCOPE.getId(), String.class);
        String attrName = getInput(context, StandardPorts.NAME.getId(), String.class);
        Object attrValue = getInput(context, StandardPorts.ANY_VALUE.getId(), Object.class);

        if (scopeId != null && !scopeId.trim().isEmpty() && attrName != null && !attrName.trim().isEmpty()) {
            context.setPersistentAttribute(scopeId, attrName, attrValue);
        }

        return next(StandardPorts.FLOW_OUT.getId());
    }
}