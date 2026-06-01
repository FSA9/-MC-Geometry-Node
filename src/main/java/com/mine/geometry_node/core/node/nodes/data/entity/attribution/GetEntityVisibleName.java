package com.mine.geometry_node.core.node.nodes.data.entity.attribution;

import com.mine.geometry_node.core.execution.ExecutionContext;
import com.mine.geometry_node.core.node.nodes.*;
import com.mine.geometry_node.core.node.port.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

import java.util.List;

public class GetEntityVisibleName extends BaseNode {

    public static final String TYPE_ID = "get_entity_visible_name";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.DATA, Component.translatable("geometry_node.node.get_entity_visible_name"))
                .addRow(new PortRow(
                        StandardPorts.ENTITY.toInput(),
                        PortDef.create("name", "geometry_node.port.name", PortType.STRING),
                        UIHint.DEFAULT, null, null
                ))
                .build();
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        if (!"name".equals(portName)) return null;

        List<Entity> entities = getInputList(context, StandardPorts.ENTITY.getId(), Entity.class);
        if (entities.isEmpty()) return null;

        return entities.getFirst().getName().getString();
    }
}