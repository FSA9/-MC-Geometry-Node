package com.mine.geometry_node.core.node.nodes.data;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.engine.service.PersistentAttributeTarget;
import com.mine.geometry_node.core.node.nodes.*;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

public class SetEntityAttribute extends BaseNode {

    public static final String TYPE_ID = "set_entity_attribute";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.DATA, Component.translatable("geometry_node.node.set_entity_attribute"))
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.ENTITY.toInput(), StandardPorts.ENTITY.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.NAME.toInput(), StandardPorts.NAME.toOutput(), UIHint.INPUT, null, null))
                .addRow(new PortRow(StandardPorts.ANY_VALUE.toInput(), StandardPorts.ANY_VALUE.toOutput(), UIHint.INPUT, null, null))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        String attrName = getInput(context, StandardPorts.NAME.getId(), String.class);
        Object attrValue = getInput(context, StandardPorts.ANY_VALUE.getId(), Object.class);
        Entity entity = getInput(context, StandardPorts.ENTITY.getId(), Entity.class);
        context.setTempData(tempKey(context, StandardPorts.ENTITY.getId()), entity);
        context.setTempData(tempKey(context, StandardPorts.NAME.getId()), attrName);
        context.setTempData(tempKey(context, StandardPorts.ANY_VALUE.getId()), attrValue);

        if (attrName != null && !attrName.trim().isEmpty() && entity != null) {
            context.setPersistentAttribute(PersistentAttributeTarget.entity(entity), attrName, attrValue);
        }

        return next(StandardPorts.FLOW_OUT.getId());
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        if (StandardPorts.ENTITY.getId().equals(portName)
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
