package com.mine.geometry_node.core.node.nodes.data.entity.attribution;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.nodes.*;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

import java.util.List;

public class GetEntityDimension extends BaseNode {

    public static final String TYPE_ID = "get_entity_dimension";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.DATA, Component.translatable("geometry_node.node.get_entity_dimension"))
                .addRow(new PortRow(
                        StandardPorts.ENTITY.toInput(),
                        StandardPorts.DIMENSION.toOutput(),
                        UIHint.DEFAULT, null, null
                ))
                .build();
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        if (!StandardPorts.DIMENSION.getId().equals(portName)) return null;

        List<Entity> entities = getInputList(context, StandardPorts.ENTITY.getId(), Entity.class);

        if (entities.isEmpty()) return null;

        Entity firstEntity = entities.getFirst();
        return firstEntity.level().dimension().identifier().toString(); // 例如 "minecraft:overworld"
    }
}