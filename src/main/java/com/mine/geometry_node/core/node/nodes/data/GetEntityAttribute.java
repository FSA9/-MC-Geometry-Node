package com.mine.geometry_node.core.node.nodes.data;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.service.PersistentAttributeTarget;
import com.mine.geometry_node.core.node.nodes.*;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

public class GetEntityAttribute extends BaseNode {

    public static final String TYPE_ID = "get_entity_attribute";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.DATA, Component.translatable("geometry_node.node.get_entity_attribute"))
                .addRow(new PortRow(StandardPorts.ENTITY.toInput(), StandardPorts.ANY_VALUE.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.NAME.toInput(), null, UIHint.INPUT, null, null))
                .build();
    }

    @Override
    @Nullable
    public Object compute(ExecutionContext context, String portName) {
        String attrName = getInput(context, StandardPorts.NAME.getId(), String.class);
        Entity entity = getInput(context, StandardPorts.ENTITY.getId(), Entity.class);

        if (attrName != null && !attrName.trim().isEmpty() && entity != null) {
            return context.getPersistentAttribute(PersistentAttributeTarget.entity(entity), attrName);
        }
        return null;
    }
}
