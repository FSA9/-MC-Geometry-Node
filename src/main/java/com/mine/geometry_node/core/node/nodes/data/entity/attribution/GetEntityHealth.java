package com.mine.geometry_node.core.node.nodes.data.entity.attribution;

import com.mine.geometry_node.core.engine.graph.data.GraphDataContext;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.nodes.*;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

public class GetEntityHealth extends BaseNode {

    public static final String TYPE_ID = "get_entity_health";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.DATA, Component.translatable("geometry_node.node.get_entity_health"))
                .addRow(new PortRow(null, StandardPorts.FLOAT_VALUE.toOutput(), UIHint.DEFAULT, null, null))
                .addPassthroughInput(StandardPorts.ENTITY.toInput(), UIHint.DEFAULT, null, null)
                .build();
    }

    @Override
    public Object compute(GraphDataContext context, String portName) {
        if (!StandardPorts.FLOAT_VALUE.getId().equals(portName)) {
            return null;
        }

        List<Entity> entities = getInputList(context, StandardPorts.ENTITY.getId(), Entity.class);
        
        if (entities.isEmpty()) {
            return null;
        }

        Entity firstEntity = entities.getFirst();

        if (firstEntity instanceof LivingEntity living) {
            return living.getHealth();
        }

        return null;
    }
}
