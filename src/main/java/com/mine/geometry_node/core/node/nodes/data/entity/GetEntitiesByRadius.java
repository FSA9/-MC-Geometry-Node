package com.mine.geometry_node.core.node.nodes.data.entity;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.spatial.AreaEntityQuery;
import com.mine.geometry_node.core.engine.blueprint.spatial.AreaShape;
import com.mine.geometry_node.core.engine.blueprint.spatial.AreaTargetType;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.nodes.*;
import com.mine.geometry_node.core.node.port.PortDef;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.PortType;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;

public class GetEntitiesByRadius extends BaseNode {

    public static final String TYPE_ID = "get_entities_by_radius";
    private static final String TARGET_PORT = "target";

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
                .addRow(new PortRow(
                        PortDef.create(TARGET_PORT, "geometry_node.port.area_target", PortType.STRING, AreaTargetType.ALL.id()).hiddenPin(),
                        null,
                        UIHint.SELECT, null,
                        Map.of(PortMetaKeys.OPTIONS, AreaTargetType.OPTIONS)
                ))
                .build();
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        if (!StandardPorts.LIST.getId().equals(portName)) return null;

        Vec3 center = getInput(context, StandardPorts.CENTER.getId(), Vec3.class);
        Float radius = getInput(context, StandardPorts.RADIUS.getId(), Float.class);
        String targetId = getInput(context, TARGET_PORT, String.class);

        if (center == null || radius == null || !Float.isFinite(radius) || context.getLevel() == null) {
            return List.of();
        }

        double safeRadius = Math.max(0.001D, Math.abs(radius));
        double diameter = safeRadius * 2.0D;
        return AreaEntityQuery.find(
                context.getLevel(),
                AreaShape.SPHERE,
                center,
                new Vec3(diameter, diameter, diameter),
                Vec3.ZERO,
                AreaTargetType.fromId(targetId),
                e -> !e.isSpectator()
        );
    }
}
