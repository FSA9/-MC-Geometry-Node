package com.mine.geometry_node.core.node.nodes.data.entity;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.node.nodes.*;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public class GetEntitiesByRadius extends BaseNode {

    public static final String TYPE_ID = "get_entities_by_radius";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.DATA, Component.translatable("geometry_node.node.get_entities_by_radius"))
                .addRow(new PortRow(null, StandardPorts.LIST.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.CENTER.toInput(),
                        null,
                        UIHint.DEFAULT, null, null
                ))
                .addRow(new PortRow(StandardPorts.RADIUS.toInput(),
                        null,
                        UIHint.INPUT, null, null
                ))
                .build();
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        if (!StandardPorts.LIST.getId().equals(portName)) return null;

        Vec3 center = getInput(context, StandardPorts.CENTER.getId(), Vec3.class);
        Float radius = getInput(context, StandardPorts.RADIUS.getId(), Float.class);

        AABB aabb = new AABB(
                center.x - radius, center.y - radius, center.z - radius,
                center.x + radius, center.y + radius, center.z + radius
        );
        List<Entity> broadPhaseEntities = context.getLevel().getEntities(null, aabb);

        List<Entity> exactEntities = new ArrayList<>();
        double radiusSqr = radius * radius;

        for (Entity entity : broadPhaseEntities) {
            if (entity.distanceToSqr(center) <= radiusSqr) {
                exactEntities.add(entity);
            }
        }

        return exactEntities;
    }
}
