package com.mine.geometry_node.core.node.nodes.actions.marker;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.engine.system.marker.MarkerService;
import com.mine.geometry_node.core.engine.system.marker.MarkerTypeRegistry;
import com.mine.geometry_node.core.engine.system.marker.model.MarkerAddress;
import com.mine.geometry_node.core.engine.system.marker.model.MarkerAnchor;
import com.mine.geometry_node.core.engine.system.marker.model.MarkerRequest;
import com.mine.geometry_node.core.node.NodeComment;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.Map;

public final class CreateMarker extends BaseNode {
    public static final String TYPE_ID = "create_marker";
    public static final String ANCHOR_COORDINATE = "coordinate";
    public static final String ANCHOR_ENTITY = "entity";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.create_marker"))
                .comment(NodeComment.builder(TYPE_ID)
                        .text("summary")
                        .input(StandardPorts.FLOW_IN, "flow_in")
                        .output(StandardPorts.FLOW_OUT, "flow_out")
                        .output(StandardPorts.BOOL, "bool")
                        .input(StandardPorts.KEY, "input")
                        .input(StandardPorts.MARKER_TYPE, "marker_type")
                        .input(StandardPorts.ONLY_SELF_VISIBLE, "only_self_visible")
                        .input(StandardPorts.PLAYER, "player")
                        .input(StandardPorts.ANCHOR_TYPE, "anchor_type")
                        .input(StandardPorts.XYZ, "xyz")
                        .input(StandardPorts.TARGET_ENTITY, "target_entity")
                        .input(StandardPorts.MESSAGE, "message")
                        .input(StandardPorts.SHOW_DISTANCE, "show_distance")
                        .input(StandardPorts.TICK, "tick")
                        .build())
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.BOOL.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.KEY.toInput(""), null, UIHint.INPUT, null, null))
                .addRow(new PortRow(StandardPorts.MARKER_TYPE.toInput(MarkerTypeRegistry.DEFAULT_TYPE_ID).hiddenPin(),
                        null, UIHint.SELECT, null,
                        Map.of(PortMetaKeys.DYNAMIC_REGISTRY_ID, MarkerTypeRegistry.DYNAMIC_REGISTRY_ID)))
                .addRow(new PortRow(StandardPorts.ONLY_SELF_VISIBLE.toInput(true), null, UIHint.CHECKBOX, null, null))
                .addRow(new PortRow(StandardPorts.PLAYER.toInput(), null, UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.ANCHOR_TYPE.toInput(ANCHOR_COORDINATE).hiddenPin(),
                        null, UIHint.SELECT, null,
                        Map.of(
                                PortMetaKeys.OPTIONS, new String[]{ANCHOR_COORDINATE, ANCHOR_ENTITY},
                                PortMetaKeys.OPTION_LABELS, new String[]{
                                        "geometry_node.marker.anchor.coordinate",
                                        "geometry_node.marker.anchor.entity"
                                }
                        )))
                .addRow(new PortRow(StandardPorts.XYZ.toInput(Vec3.ZERO), null, UIHint.VECTOR, null, null))
                .addRow(new PortRow(StandardPorts.TARGET_ENTITY.toInput(), null, UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.MESSAGE.toInput(""), null, UIHint.INPUT, null, null))
                .addRow(new PortRow(StandardPorts.SHOW_DISTANCE.toInput(true), null, UIHint.CHECKBOX, null, null))
                .addRow(new PortRow(StandardPorts.TICK.toInput(0), null, UIHint.INPUT, null,
                        Map.of(PortMetaKeys.NUMERIC_MIN, 0)))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        boolean success = false;
        ServerLevel level = context.getLevel();
        String key = getInput(context, StandardPorts.KEY.getId(), String.class);
        if (level != null && key != null && !key.isBlank()) {
            try {
                boolean onlySelf = valueOr(getInput(context, StandardPorts.ONLY_SELF_VISIBLE.getId(), Boolean.class), true);
                MarkerAddress address = resolveAddress(context, key.trim(), onlySelf);
                MarkerAnchor anchor = resolveAnchor(context, level);
                if (address != null && anchor != null) {
                    String typeId = valueOr(getInput(context, StandardPorts.MARKER_TYPE.getId(), String.class),
                            MarkerTypeRegistry.DEFAULT_TYPE_ID);
                    String text = valueOr(getInput(context, StandardPorts.MESSAGE.getId(), String.class), "");
                    boolean showDistance = valueOr(getInput(context, StandardPorts.SHOW_DISTANCE.getId(), Boolean.class), true);
                    int duration = Math.max(0, valueOr(getInput(context, StandardPorts.TICK.getId(), Integer.class), 0));
                    success = MarkerService.INSTANCE.upsert(level,
                            new MarkerRequest(address, typeId, anchor, text, showDistance, duration));
                }
            } catch (IllegalArgumentException ignored) {
                success = false;
            }
        }
        context.setTempData(tempKey(context), success);
        return next(StandardPorts.FLOW_OUT.getId());
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        return StandardPorts.BOOL.getId().equals(portName) ? context.getTempData(tempKey(context)) : null;
    }

    private MarkerAddress resolveAddress(ExecutionContext context, String key, boolean onlySelf) {
        if (!onlySelf) return MarkerAddress.all(key);
        Entity viewer = getInput(context, StandardPorts.PLAYER.getId(), Entity.class);
        if (viewer == null) viewer = context.getGraphOwnerEntity();
        if (viewer == null) viewer = context.getEntity();
        return viewer instanceof ServerPlayer player ? MarkerAddress.self(player.getUUID(), key) : null;
    }

    private MarkerAnchor resolveAnchor(ExecutionContext context, ServerLevel fallbackLevel) {
        String anchorType = valueOr(getInput(context, StandardPorts.ANCHOR_TYPE.getId(), String.class), ANCHOR_COORDINATE);
        if (ANCHOR_ENTITY.equals(anchorType)) {
            Entity target = getInput(context, StandardPorts.TARGET_ENTITY.getId(), Entity.class);
            if (target == null || target.isRemoved() || !target.isAlive() || !(target.level() instanceof ServerLevel targetLevel)) {
                return null;
            }
            return new MarkerAnchor.Entity(targetLevel.dimension(), target.getUUID());
        }
        Vec3 position = valueOr(getInput(context, StandardPorts.XYZ.getId(), Vec3.class), Vec3.ZERO);
        return new MarkerAnchor.Coordinate(fallbackLevel.dimension(), position);
    }

    private static String tempKey(ExecutionContext context) {
        return TYPE_ID + ":" + context.getCurrentNodeId() + ":" + StandardPorts.BOOL.getId();
    }

    private static <T> T valueOr(T value, T fallback) {
        return value != null ? value : fallback;
    }
}
