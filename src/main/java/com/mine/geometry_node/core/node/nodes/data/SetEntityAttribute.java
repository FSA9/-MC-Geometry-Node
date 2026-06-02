package com.mine.geometry_node.core.node.nodes.data;

import com.mine.geometry_node.core.engine.blueprint.execution.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.execution.ExecutionResult;
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
                .addRow(new PortRow(StandardPorts.ENTITY.toInput(), null, UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.NAME.toInput(), null, UIHint.INPUT, null, null))
                .addRow(new PortRow(StandardPorts.ANY_VALUE.toInput(), null, UIHint.INPUT, null, null))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        String attrName = getInput(context, StandardPorts.NAME.getId(), String.class);
        Object attrValue = getInput(context, StandardPorts.ANY_VALUE.getId(), Object.class);

        Entity entity = getInput(context, StandardPorts.ENTITY.getId(), Entity.class);

        if (attrName != null && !attrName.trim().isEmpty() && entity != null) {
            context.setPersistentAttribute(entity, attrName, attrValue);
        }

        return next(StandardPorts.FLOW_OUT.getId());
    }
}