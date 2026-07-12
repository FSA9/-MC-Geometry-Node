package com.mine.geometry_node.core.node.nodes.data;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.engine.service.PersistentAttributeTarget;
import com.mine.geometry_node.core.node.nodes.*;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;

public class SetScopeAttribute extends BaseNode {

    public static final String TYPE_ID = "set_scope_attribute";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.DATA, Component.translatable("geometry_node.node.set_scope_attribute"))
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.SCOPE.toInput(), StandardPorts.SCOPE.toOutput(), UIHint.INPUT, null, null))
                .addRow(new PortRow(StandardPorts.NAME.toInput(), StandardPorts.NAME.toOutput(), UIHint.INPUT, null, null))
                .addRow(new PortRow(StandardPorts.ANY_VALUE.toInput(), StandardPorts.ANY_VALUE.toOutput(), UIHint.INPUT, null, null))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        String scopeId = getInput(context, StandardPorts.SCOPE.getId(), String.class);
        String attrName = getInput(context, StandardPorts.NAME.getId(), String.class);
        Object attrValue = getInput(context, StandardPorts.ANY_VALUE.getId(), Object.class);
        context.setTempData(tempKey(context, StandardPorts.SCOPE.getId()), scopeId);
        context.setTempData(tempKey(context, StandardPorts.NAME.getId()), attrName);
        context.setTempData(tempKey(context, StandardPorts.ANY_VALUE.getId()), attrValue);

        if (scopeId != null && !scopeId.trim().isEmpty() && attrName != null && !attrName.trim().isEmpty()) {
            PersistentAttributeTarget target = "GLOBAL".equals(scopeId)
                    ? PersistentAttributeTarget.global()
                    : PersistentAttributeTarget.scope(scopeId);
            context.setPersistentAttribute(target, attrName, attrValue);
        }

        return next(StandardPorts.FLOW_OUT.getId());
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        if (StandardPorts.SCOPE.getId().equals(portName)
                || StandardPorts.NAME.getId().equals(portName)
                || StandardPorts.ANY_VALUE.getId().equals(portName)) {
            return context.getTempData(tempKey(context, portName));
        }
        return null;
    }

    private String tempKey(ExecutionContext context, String portName) {
        return TYPE_ID + ":" + context.getCurrentNodeId() + ":" + portName;
    }
}
