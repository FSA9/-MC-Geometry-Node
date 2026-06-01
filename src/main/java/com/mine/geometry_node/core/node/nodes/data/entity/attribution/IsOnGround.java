package com.mine.geometry_node.core.node.nodes.data.entity.attribution;

import com.mine.geometry_node.core.execution.ExecutionContext;
import com.mine.geometry_node.core.node.nodes.*;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

import java.util.List;

public class IsOnGround extends BaseNode {

    public static final String TYPE_ID = "is_on_ground";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.DATA, Component.translatable("geometry_node.node.is_on_ground"))
                .addRow(new PortRow(
                        StandardPorts.ENTITY.toInput(),
                        StandardPorts.BOOL.toOutput(),
                        UIHint.DEFAULT, null, null
                ))
                .build();
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        if (!StandardPorts.BOOL.getId().equals(portName)) return null;

        List<Entity> entities = getInputList(context, StandardPorts.ENTITY.getId(), Entity.class);
        if (entities.isEmpty()) return false;

        return entities.getFirst().onGround();
    }
}
