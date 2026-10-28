package com.mine.geometry_node.core.node.nodes.actions.marker;

import com.mine.geometry_node.core.engine.graph.data.GraphDataContext;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceReferences;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.engine.system.marker.MarkerService;
import com.mine.geometry_node.core.engine.system.marker.type.MarkerTypeRegistry;
import com.mine.geometry_node.core.engine.system.marker.model.MarkerAddress;
import com.mine.geometry_node.core.engine.system.marker.model.MarkerRequest;
import com.mine.geometry_node.core.node.definition.node.NodeComment;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.util.AnchorNodeSupport;
import com.mine.geometry_node.core.engine.spatial.SpatialAnchor;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.PortDef;
import com.mine.geometry_node.core.node.definition.port.PortType;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import com.mine.geometry_node.core.node.util.ResourceNodeSupport;

public final class CreateMarker extends BaseNode {
    public static final String TYPE_ID = "create_marker";
    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.create_marker"))
                .comment(NodeComment.builder(TYPE_ID).text("summary")
                        .input(StandardPorts.FLOW_IN, "flow_in").output(StandardPorts.FLOW_OUT, "flow_out")
                        .output(StandardPorts.BOOL, "bool").input(StandardPorts.KEY, "key")
                        .input(StandardPorts.MARKER_TYPE, "marker_type").input(StandardPorts.PLAYER, "player")
                        .input(StandardPorts.SPATIAL_ANCHOR, "spatial_anchor")
                        .input(AnchorNodeSupport.WORLD_OFFSET.id(), "world_offset")
                        .input(AnchorNodeSupport.LOCAL_OFFSET.id(), "local_offset")
                        .input(StandardPorts.MESSAGE, "message").input(StandardPorts.SHOW_DISTANCE, "show_distance")
                        .input(StandardPorts.TICK, "tick").build())
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.BOOL.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(ResourceNodeSupport.creationRow(StandardPorts.KEY.getId(), StandardPorts.RESOURCE_ID.getId()))
                .addPassthroughInput(StandardPorts.MESSAGE.toInput(""), UIHint.INPUT)
                .addPassthroughInput(StandardPorts.MARKER_TYPE.toInput(MarkerTypeRegistry.DEFAULT_TYPE_ID).hiddenPin(), UIHint.SELECT,
                        null, Map.of(PortMetaKeys.DYNAMIC_REGISTRY_ID, MarkerTypeRegistry.DYNAMIC_REGISTRY_ID))
                .addPassthroughInput(StandardPorts.PLAYER.toInput(), UIHint.DEFAULT)
                .addRow(AnchorNodeSupport.row(StandardPorts.SPATIAL_ANCHOR))
                .addPassthroughInput(PortDef.create("world_offset",
                        "geometry_node.port.world_offset", PortType.XYZ, Vec3.ZERO), UIHint.VECTOR)
                .addRow(PortRow.passthrough(PortDef.create("local_offset",
                        "geometry_node.port.local_offset", PortType.XYZ, Vec3.ZERO), UIHint.VECTOR, null, null))
                .addPassthroughInput(StandardPorts.SHOW_DISTANCE.toInput(true), UIHint.CHECKBOX)
                .addPassthroughInput(StandardPorts.TICK.toInput(0), UIHint.INPUT, null, Map.of(PortMetaKeys.NUMERIC_MIN, 0))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        boolean success = false;
        context.setNodeResult(StandardPorts.RESOURCE_ID.getId(), "");
        ServerLevel level = context.getLevel();
        String key = getInput(context, StandardPorts.KEY.getId(), String.class);
        if (level != null) {
            try {
                MarkerAddress address = resolveAddress(context, key);
                SpatialAnchor anchor = resolveAnchor(context, level);
                if (address != null && anchor != null) {
                    String typeId = valueOr(getInput(context, StandardPorts.MARKER_TYPE.getId(), String.class),
                            MarkerTypeRegistry.DEFAULT_TYPE_ID);
                    String text = valueOr(getInput(context, StandardPorts.MESSAGE.getId(), String.class), "");
                    boolean showDistance = valueOr(getInput(context, StandardPorts.SHOW_DISTANCE.getId(), Boolean.class), true);
                    int duration = Math.max(0, valueOr(getInput(context, StandardPorts.TICK.getId(), Integer.class), 0));
                    success = MarkerService.INSTANCE.upsert(level,
                            new MarkerRequest(address, typeId, anchor,
                                    AnchorNodeSupport.vector(context, AnchorNodeSupport.WORLD_OFFSET),
                                    AnchorNodeSupport.vector(context, AnchorNodeSupport.LOCAL_OFFSET),
                                    text, showDistance, duration));
                    if (success) context.setNodeResult(StandardPorts.RESOURCE_ID.getId(), GraphResourceReferences.encode(address));
                }
            } catch (IllegalArgumentException ignored) {
                success = false;
            }
        }
        context.setNodeResult(StandardPorts.BOOL.getId(), success);
        return next(StandardPorts.FLOW_OUT.getId());
    }

    @Override
    public Object compute(GraphDataContext context, String portName) {
        return (StandardPorts.BOOL.getId().equals(portName) || StandardPorts.RESOURCE_ID.getId().equals(portName))
                ? context.getNodeResult(portName) : null;
    }

    private MarkerAddress resolveAddress(ExecutionContext context, String key) {
        Entity viewer = getInputFromList(context, StandardPorts.PLAYER.getId(), 0, Entity.class);
        if (!(viewer instanceof ServerPlayer player)) return null;
        String stableId = context.getCurrentNodeStableId();
        if (stableId == null || stableId.isBlank()) stableId = Integer.toString(context.getCurrentNodeId());
        MarkerAddress address = GraphResourceReferences.resolveMarker(context, stableId, key, player.getUUID(), true);
        // Complete resource IDs must not override the explicitly supplied viewer.
        return player.getUUID().equals(address.viewerId()) ? address : null;
    }

    private SpatialAnchor resolveAnchor(ExecutionContext context, ServerLevel fallbackLevel) {
        SpatialAnchor anchor = AnchorNodeSupport.read(context, StandardPorts.SPATIAL_ANCHOR);
        if (anchor == null) return null;
        anchor = anchor.withDimension(anchor.dimensionOr(fallbackLevel.dimension()));
        ServerLevel anchorLevel = fallbackLevel.getServer().getLevel(anchor.dimension());
        if (anchorLevel == null) return null;
        if (anchor.isEntity()) {
            Entity entity = anchorLevel.getEntity(anchor.entityId());
            if (entity == null || entity.isRemoved() || !entity.isAlive()) return null;
        }
        return anchor;
    }

    private static <T> T valueOr(T value, T fallback) {
        return value != null ? value : fallback;
    }
}
