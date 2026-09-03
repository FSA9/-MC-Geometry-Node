package com.mine.geometry_node.core.node.nodes.data.entity;

import com.mine.geometry_node.core.engine.graph.debug.DebugRendererSessionManager;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceIds;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceTypeRegistry;
import com.mine.geometry_node.core.engine.graph.data.GraphDataContext;
import com.mine.geometry_node.core.engine.blueprint.spatial.area.AreaShape;
import com.mine.geometry_node.core.engine.blueprint.spatial.area.AreaTargetType;
import com.mine.geometry_node.core.engine.blueprint.spatial.RotatedBoxEntityQuery;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
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

public class GetEntitiesbyRotationBox extends BaseNode {

    public static final String TYPE_ID = "get_entities_by_rotation_box";
    private static final String TARGET_PORT = "target";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.DATA, Component.translatable("geometry_node.node.get_entities_by_rotation_box"))
                .addRow(new PortRow(null, StandardPorts.LIST.toOutput(), UIHint.DEFAULT, null, null))
                .addPassthroughInput(StandardPorts.CENTER.toInput(), UIHint.VECTOR, null, null)
                .addPassthroughInput(StandardPorts.SIZE_3.toInput(new Vec3(1, 1, 1)), UIHint.VECTOR, null, null)
                .addPassthroughInput(StandardPorts.ROTATION.toInput(Vec3.ZERO), UIHint.VECTOR, null, null)
                .addPassthroughInput(PortDef.create(TARGET_PORT, "geometry_node.port.area_target", PortType.STRING, AreaTargetType.ALL.id()).hiddenPin(), UIHint.SELECT, null, Map.of(PortMetaKeys.OPTIONS, AreaTargetType.OPTIONS))
                .build();
    }

    @Override
    public Object compute(GraphDataContext context, String portName) {
        if (!StandardPorts.LIST.getId().equals(portName)) return null;

        Vec3 center = getInput(context, StandardPorts.CENTER.getId(), Vec3.class);
        Vec3 size = getInput(context, StandardPorts.SIZE_3.getId(), Vec3.class);
        Vec3 rotation = getInput(context, StandardPorts.ROTATION.getId(), Vec3.class);
        String targetId = getInput(context, TARGET_PORT, String.class);

        if (center == null || size == null || context.getLevel() == null) {
            return List.of();
        }
        if (rotation == null) rotation = Vec3.ZERO;

        List<Entity> result = RotatedBoxEntityQuery.find(
                context.getLevel(),
                center,
                size,
                rotation,
                AreaTargetType.fromId(targetId),
                entity -> !entity.isSpectator()
        );
        String stableNodeId = context.getCurrentNodeStableId();
        if (stableNodeId != null && !stableNodeId.isBlank()) {
            DebugRendererSessionManager.showTransientQueryArea(
                    context.getLevel(), GraphResourceIds.node(context, stableNodeId,
                            GraphResourceTypeRegistry.AREA_QUERY),
                    AreaShape.BOX.id(), center, size, rotation
            );
        }
        return result;
    }
}
