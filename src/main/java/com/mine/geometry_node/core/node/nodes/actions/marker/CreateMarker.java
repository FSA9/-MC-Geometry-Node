package com.mine.geometry_node.core.node.nodes.actions.marker;

import com.mine.geometry_node.core.engine.graph.data.GraphDataContext;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceReferences;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.engine.system.marker.MarkerService;
import com.mine.geometry_node.core.engine.system.marker.MarkerTypeRegistry;
import com.mine.geometry_node.core.engine.system.marker.model.MarkerAddress;
import com.mine.geometry_node.core.engine.system.marker.model.MarkerAnchor;
import com.mine.geometry_node.core.engine.system.marker.model.MarkerRequest;
import com.mine.geometry_node.core.node.definition.node.NodeComment;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.PortRow;
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
    public static final String ANCHOR_COORDINATE = "coordinate";
    public static final String ANCHOR_ENTITY = "entity";

    @Override
    public NodeDef getDefaultDefinition() {
        return buildDefinition(ANCHOR_COORDINATE);
    }

    @Override
    public NodeDef getDefinition(NodeData instanceData) {
        Object binding = instanceData == null ? null : instanceData.inputs.get(StandardPorts.ANCHOR_TYPE.getId());
        return buildDefinition(ANCHOR_ENTITY.equals(binding) ? ANCHOR_ENTITY : ANCHOR_COORDINATE);
    }

    private NodeDef buildDefinition(String bindingType) {
        boolean entityBinding = ANCHOR_ENTITY.equals(bindingType);
        NodeComment.Builder comment = NodeComment.builder(TYPE_ID)
                .text("summary")
                .input(StandardPorts.FLOW_IN, "flow_in")
                .output(StandardPorts.FLOW_OUT, "flow_out")
                .output(StandardPorts.BOOL, "bool")
                .input(StandardPorts.KEY, "key")
                .input(StandardPorts.MARKER_TYPE, "marker_type")
                .input(StandardPorts.PLAYER, "player")
                .input(StandardPorts.ANCHOR_TYPE, "anchor_type")
                .input(entityBinding ? StandardPorts.TARGET_ENTITY : StandardPorts.XYZ,
                        entityBinding ? "target_entity" : "xyz")
                .input(StandardPorts.MESSAGE, "message")
                .input(StandardPorts.SHOW_DISTANCE, "show_distance")
                .input(StandardPorts.TICK, "tick");
        NodeDef.Builder builder = NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.create_marker"))
                .comment(comment.build())
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.BOOL.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(ResourceNodeSupport.creationRow(StandardPorts.KEY.getId(), StandardPorts.RESOURCE_ID.getId()))
                .addPassthroughInput(StandardPorts.MESSAGE.toInput(""), UIHint.INPUT)
                .addPassthroughInput(StandardPorts.MARKER_TYPE.toInput(MarkerTypeRegistry.DEFAULT_TYPE_ID).hiddenPin(), UIHint.SELECT, null, Map.of(PortMetaKeys.DYNAMIC_REGISTRY_ID, MarkerTypeRegistry.DYNAMIC_REGISTRY_ID))
                .addPassthroughInput(StandardPorts.PLAYER.toInput(), UIHint.DEFAULT)
                .addPassthroughInput(StandardPorts.ANCHOR_TYPE.toInput(bindingType).hiddenPin(), UIHint.SELECT, null, Map.of(
                                PortMetaKeys.OPTIONS, new String[]{ANCHOR_COORDINATE, ANCHOR_ENTITY},
                                PortMetaKeys.OPTION_LABELS, new String[]{
                                        "geometry_node.marker.anchor.coordinate",
                                        "geometry_node.marker.anchor.entity"
                                }
                        ));
        if (entityBinding) {
            builder.addPassthroughInput(StandardPorts.TARGET_ENTITY.toInput(), UIHint.DEFAULT);
        } else {
            builder.addPassthroughInput(StandardPorts.XYZ.toInput(Vec3.ZERO), UIHint.VECTOR);
        }
        return builder.addPassthroughInput(StandardPorts.SHOW_DISTANCE.toInput(true), UIHint.CHECKBOX)
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
                MarkerAnchor anchor = resolveAnchor(context, level);
                if (address != null && anchor != null) {
                    String typeId = valueOr(getInput(context, StandardPorts.MARKER_TYPE.getId(), String.class),
                            MarkerTypeRegistry.DEFAULT_TYPE_ID);
                    String text = valueOr(getInput(context, StandardPorts.MESSAGE.getId(), String.class), "");
                    boolean showDistance = valueOr(getInput(context, StandardPorts.SHOW_DISTANCE.getId(), Boolean.class), true);
                    int duration = Math.max(0, valueOr(getInput(context, StandardPorts.TICK.getId(), Integer.class), 0));
                    success = MarkerService.INSTANCE.upsert(level,
                            new MarkerRequest(address, typeId, anchor, text, showDistance, duration));
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

    private MarkerAnchor resolveAnchor(ExecutionContext context, ServerLevel fallbackLevel) {
        String anchorType = valueOr(getInput(context, StandardPorts.ANCHOR_TYPE.getId(), String.class), ANCHOR_COORDINATE);
        if (ANCHOR_ENTITY.equals(anchorType)) {
            Entity target = getInputFromList(
                    context, StandardPorts.TARGET_ENTITY.getId(), 0, Entity.class);
            if (target == null || target.isRemoved() || !target.isAlive() || !(target.level() instanceof ServerLevel targetLevel)) {
                return null;
            }
            return new MarkerAnchor.Entity(targetLevel.dimension(), target.getUUID());
        }
        Vec3 position = valueOr(getInput(context, StandardPorts.XYZ.getId(), Vec3.class), Vec3.ZERO);
        return new MarkerAnchor.Coordinate(fallbackLevel.dimension(), position);
    }

    private static <T> T valueOr(T value, T fallback) {
        return value != null ? value : fallback;
    }
}
