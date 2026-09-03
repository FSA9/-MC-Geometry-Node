package com.mine.geometry_node.core.node.nodes.data.entity;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.graph.debug.DebugRendererSessionManager;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceIds;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceTypeRegistry;
import com.mine.geometry_node.core.engine.blueprint.spatial.area.AreaEntityQuery;
import com.mine.geometry_node.core.engine.blueprint.spatial.area.AreaShape;
import com.mine.geometry_node.core.engine.blueprint.spatial.area.AreaTargetType;
import com.mine.geometry_node.core.engine.graph.data.GraphDataContext;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.nodes.*;
import com.mine.geometry_node.core.node.definition.port.PortDef;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.PortType;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
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
                .addPassthroughInput(StandardPorts.CENTER.toInput(), UIHint.DEFAULT, null, null)
                .addPassthroughInput(StandardPorts.RADIUS.toInput(), UIHint.INPUT, null, null)
                .addPassthroughInput(PortDef.create(TARGET_PORT, "geometry_node.port.area_target", PortType.STRING, AreaTargetType.ALL.id()).hiddenPin(), UIHint.SELECT, null, Map.of(PortMetaKeys.OPTIONS, AreaTargetType.OPTIONS))
                .build();
    }

    @Override
    public Object compute(GraphDataContext context, String portName) {
        if (!StandardPorts.LIST.getId().equals(portName)) return null;

        Vec3 center = getInput(context, StandardPorts.CENTER.getId(), Vec3.class);
        Float radius = getInput(context, StandardPorts.RADIUS.getId(), Float.class);
        String targetId = getInput(context, TARGET_PORT, String.class);

        if (center == null || radius == null || !Float.isFinite(radius) || context.getLevel() == null) {
            return List.of();
        }

        double safeRadius = Math.max(0.001D, Math.abs(radius));
        double diameter = safeRadius * 2.0D;
        Vec3 size = new Vec3(diameter, diameter, diameter);
        List<Entity> result = AreaEntityQuery.find(
                context.getLevel(),
                AreaShape.SPHERE,
                center,
                size,
                Vec3.ZERO,
                AreaTargetType.fromId(targetId),
                e -> !e.isSpectator()
        );
        if (context instanceof ExecutionContext blueprintContext) {
            DebugRendererSessionManager.showTransientQueryArea(
                    context.getLevel(), GraphResourceIds.node(blueprintContext,
                            stableNodeId(blueprintContext), GraphResourceTypeRegistry.AREA_QUERY),
                    AreaShape.SPHERE.id(), center, size, Vec3.ZERO
            );
        }
        return result;
    }

    private static String stableNodeId(ExecutionContext context) {
        String stableId = context.getCurrentNodeStableId();
        return stableId == null || stableId.isBlank()
                ? Integer.toString(context.getCurrentNodeId()) : stableId;
    }
}
