package com.mine.geometry_node.core.node.nodes.data.entity;

import com.mine.geometry_node.core.execution.ExecutionContext;
import com.mine.geometry_node.core.node.nodes.*;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;

import java.util.List;

public class GetEntitiesByAABB extends BaseNode {

    public static final String TYPE_ID = "get_entities_by_aabb";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.DATA, Component.translatable("geometry_node.node.get_entities_by_aabb"))
                .addRow(new PortRow(null, StandardPorts.LIST.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.XYZ.toInputWithIndex(1),
                            null,
                                    UIHint.VECTOR, null, null
                ))
                .addRow(new PortRow(StandardPorts.XYZ.toInputWithIndex(2),
                            null,
                                    UIHint.VECTOR, null, null
                ))
                .build();
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        if (!StandardPorts.LIST.getId().equals(portName)) return null;

        Vec3 pos1 = getInput(context, StandardPorts.XYZ.getIdWithIndex(1), Vec3.class);
        Vec3 pos2 = getInput(context, StandardPorts.XYZ.getIdWithIndex(2), Vec3.class);

        if (pos1 == null || pos2 == null || context.getLevel() == null) {
            return List.of();
        }

        AABB aabb = new AABB(pos1, pos2);

        return context.getLevel().getEntities(null, aabb);
    }
}