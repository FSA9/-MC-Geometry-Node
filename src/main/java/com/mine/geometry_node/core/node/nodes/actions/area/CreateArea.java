package com.mine.geometry_node.core.node.nodes.actions.area;

import com.mine.geometry_node.core.engine.graph.data.GraphDataContext;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.engine.blueprint.spatial.area.AreaAddress;
import com.mine.geometry_node.core.engine.blueprint.spatial.area.AreaEntityQuery;
import com.mine.geometry_node.core.engine.blueprint.spatial.area.AreaResourceStore;
import com.mine.geometry_node.core.engine.blueprint.spatial.area.AreaShape;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceId;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceIds;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceReferences;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceTypeRegistry;
import com.mine.geometry_node.core.engine.graph.expression.ExpressionData;
import com.mine.geometry_node.core.engine.graph.expression.ExpressionSpec;
import com.mine.geometry_node.core.engine.graph.expression.LiveValue;
import com.mine.geometry_node.core.engine.graph.expression.LiveValues;
import com.mine.geometry_node.core.node.definition.node.NodeComment;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.PortDef;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.PortType;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import com.mine.geometry_node.core.utils.RateLimitedLog;
import com.mine.geometry_node.core.node.util.ResourceNodeSupport;
import com.mine.geometry_node.core.node.util.AnchorNodeSupport;
import com.mine.geometry_node.core.engine.spatial.SpatialAnchor;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.Map;

public final class CreateArea extends BaseNode {
    public static final String TYPE_ID = "create_area";
    public static final String SHAPE_PORT = "area_shape";
    public static final String HEIGHT_PORT = StandardPorts.HEIGHT.getId();
    public static final double DEFAULT_RADIUS = 1.0D;
    public static final double DEFAULT_HEIGHT = 2.0D;
    public static final PortDef CENTER_PORT = AnchorNodeSupport.WORLD_OFFSET;
    public static final PortDef WORLD_ROTATION_PORT = AnchorNodeSupport.WORLD_ROTATION;
    public static final PortDef LOCAL_OFFSET_PORT = AnchorNodeSupport.LOCAL_OFFSET;
    public static final PortDef SIZE_PORT = StandardPorts.SIZE_3
            .toInput(new Vec3(1, 1, 1)).liveExpression();
    public static final PortDef ROTATION_PORT = AnchorNodeSupport.LOCAL_ROTATION;
    public static final PortDef RADIUS_PORT = StandardPorts.RADIUS
            .toInput((float) DEFAULT_RADIUS).liveExpression();
    public static final PortDef HEIGHT_INPUT = PortDef.create(HEIGHT_PORT,
            "geometry_node.port.area_height", PortType.FLOAT, (float) DEFAULT_HEIGHT).liveExpression();

    @Override
    public NodeDef getDefaultDefinition() {
        return buildDef(AreaShape.BOX);
    }

    @Override
    public NodeDef getDefinition(NodeData instanceData) {
        Object rawShape = instanceData != null ? instanceData.inputs.get(SHAPE_PORT) : null;
        return buildDef(AreaShape.fromId(rawShape instanceof String text ? text : null));
    }

    private NodeDef buildDef(AreaShape shape) {
        NodeComment.Builder comment = NodeComment.builder(TYPE_ID)
                .text("summary")
                .input(StandardPorts.FLOW_IN, "flow_in")
                        .output(StandardPorts.FLOW_OUT, "flow_out")
                        .output(StandardPorts.AREA, "area")
                .input(StandardPorts.AREA_ID, "area_id")
                .input(StandardPorts.SPATIAL_ANCHOR, "geometry_node.anchor.help")
                .input(SHAPE_PORT, "shape")
                .input(CENTER_PORT.id(), "geometry_node.transform.world_offset.help")
                .input(WORLD_ROTATION_PORT.id(), "geometry_node.transform.world_rotation.help")
                .input(LOCAL_OFFSET_PORT.id(), "geometry_node.transform.local_offset.help")
                .input(ROTATION_PORT.id(), "geometry_node.transform.local_rotation.help");
        switch (shape) {
            case SPHERE -> comment.input(StandardPorts.RADIUS, "radius");
            case CYLINDER -> comment.input(StandardPorts.RADIUS, "radius")
                    .input(HEIGHT_PORT, "height");
            case BOX -> comment.input(StandardPorts.SIZE_3, "size");
        }

        NodeDef.Builder builder = NodeDef.builder(TYPE_ID, NodeType.ACTION,
                        Component.translatable("geometry_node.node." + TYPE_ID))
                .comment(comment.build())
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(),
                        UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.AREA.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(ResourceNodeSupport.creationRow(StandardPorts.AREA_ID.getId(), StandardPorts.RESOURCE_ID.getId()));
        builder.addRow(AnchorNodeSupport.row(StandardPorts.SPATIAL_ANCHOR));
        builder.addPassthroughInput(PortDef.create(SHAPE_PORT, "geometry_node.port.area_shape", PortType.STRING,
                                AreaShape.BOX.id()).hiddenPin(), UIHint.SELECT, null,
                        Map.of(PortMetaKeys.OPTIONS, AreaShape.OPTIONS))
                .addPassthroughInput(CENTER_PORT, UIHint.VECTOR)
                .addPassthroughInput(WORLD_ROTATION_PORT, UIHint.VECTOR)
                .addRow(PortRow.passthrough(LOCAL_OFFSET_PORT, UIHint.VECTOR, null, null))
                .addRow(PortRow.passthrough(ROTATION_PORT, UIHint.VECTOR, null, null));

        switch (shape) {
            case SPHERE -> builder.addPassthroughInput(RADIUS_PORT, UIHint.INPUT);
            case CYLINDER -> {
                builder.addPassthroughInput(RADIUS_PORT, UIHint.INPUT);
                builder.addPassthroughInput(HEIGHT_INPUT, UIHint.INPUT);
            }
            case BOX -> {
                builder.addPassthroughInput(SIZE_PORT, UIHint.VECTOR);
            }
        }
        return builder.build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        context.setNodeResult(StandardPorts.AREA.getId(), null);
        context.setNodeResult(StandardPorts.RESOURCE_ID.getId(), "");
        String areaId = getInput(context, StandardPorts.AREA_ID.getId(), String.class);
        ServerLevel hostLevel = context.getLevel();
        SpatialAnchor anchor = AnchorNodeSupport.capture(context, StandardPorts.SPATIAL_ANCHOR);
        if (anchor == null || hostLevel == null) return next(StandardPorts.FLOW_OUT.getId());
        anchor = anchor.withDimension(anchor.dimensionOr(hostLevel.dimension()));
        ServerLevel areaLevel = hostLevel.getServer().getLevel(anchor.dimension());
        if (areaLevel == null) return next(StandardPorts.FLOW_OUT.getId());
        try {
            AreaShape shape = AreaShape.fromId(getInput(context, SHAPE_PORT, String.class));
            Vec3 center = valueOr(getInput(context, CENTER_PORT.id(), Vec3.class), Vec3.ZERO);
            Vec3 size = AreaEntityQuery.sanitizeSize(valueOr(
                    getInput(context, StandardPorts.SIZE_3.getId(), Vec3.class), new Vec3(1, 1, 1)));
            float radius = positive(valueOr(getInput(context, StandardPorts.RADIUS.getId(), Float.class),
                    (float) DEFAULT_RADIUS));
            float height = positive(valueOr(getInput(context, HEIGHT_PORT, Float.class), (float) DEFAULT_HEIGHT));
            Vec3 rotation = AnchorNodeSupport.vector(context, ROTATION_PORT);
            Vec3 worldRotation = AnchorNodeSupport.vector(context, WORLD_ROTATION_PORT);
            Vec3 localOffset = AnchorNodeSupport.vector(context, LOCAL_OFFSET_PORT);
            if (anchor.requiresEntity()) {
                Entity entity = areaLevel.getEntity(anchor.entityId());
                if (entity == null || entity.isRemoved()) return next(StandardPorts.FLOW_OUT.getId());
            }
            String stableId = context.getCurrentNodeStableId();
            if (stableId == null || stableId.isBlank()) stableId = Integer.toString(context.getCurrentNodeId());
            AreaAddress address = GraphResourceReferences.resolveArea(context, stableId, areaId, areaLevel.dimension(), true);
            if (address == null || !anchor.dimension().equals(address.dimension()))
                return next(StandardPorts.FLOW_OUT.getId());
            GraphResourceId resourceOwner = GraphResourceIds.forKey(context, stableId,
                    GraphResourceTypeRegistry.AREA, address.id(), anchor.requiresEntity() ? anchor.entityId() : null);
            ExpressionData centerExpression = AnchorNodeSupport.positionExpression(anchor,
                    AnchorNodeSupport.readExpression(context, StandardPorts.SPATIAL_ANCHOR), center,
                    getInputExpression(context, CENTER_PORT.id()));
            center = AnchorNodeSupport.position(anchor, center);
            LiveValue<Vec3> liveCenter = captureXyz(CENTER_PORT, center, centerExpression);
            LiveValue<Vec3> liveSize = captureXyz(SIZE_PORT, size,
                    getInputExpression(context, StandardPorts.SIZE_3.getId()));
            LiveValue<Vec3> liveRotation = captureXyz(ROTATION_PORT, rotation,
                    getInputExpression(context, ROTATION_PORT.id()));
            LiveValue<Vec3> liveWorldRotation = captureXyz(WORLD_ROTATION_PORT, worldRotation,
                    getInputExpression(context, WORLD_ROTATION_PORT.id()));
            LiveValue<Vec3> liveLocalOffset = captureXyz(LOCAL_OFFSET_PORT, localOffset,
                    getInputExpression(context, LOCAL_OFFSET_PORT.id()));
            LiveValue<Float> liveRadius = LiveValues.captureFloat(RADIUS_PORT, radius,
                    ExpressionSpec.fromScalar(getInputExpression(context, StandardPorts.RADIUS.getId())));
            LiveValue<Float> liveHeight = LiveValues.captureFloat(HEIGHT_INPUT, height,
                    ExpressionSpec.fromScalar(getInputExpression(context, HEIGHT_PORT)));
            reportDiagnostics(context, address.id(), "center", liveCenter);
            reportDiagnostics(context, address.id(), "size", liveSize);
            reportDiagnostics(context, address.id(), "rotation", liveRotation);
            reportDiagnostics(context, address.id(), "radius", liveRadius);
            reportDiagnostics(context, address.id(), "height", liveHeight);
            var resource = AreaResourceStore.INSTANCE.upsert(areaLevel.getServer(), address, resourceOwner,
                    shape, areaLevel.getGameTime(), liveCenter, liveSize, liveRotation, liveRadius, liveHeight,
                    liveWorldRotation, liveLocalOffset, anchor.isEntity() ? anchor : SpatialAnchor.world(anchor.dimension(), Vec3.ZERO));
            context.setNodeResult(StandardPorts.AREA.getId(), resource.reference());
            context.setNodeResult(StandardPorts.RESOURCE_ID.getId(), GraphResourceReferences.encode(address));
        } catch (IllegalArgumentException failure) {
            if (RateLimitedLog.acquire(context, "area_resource:" + failure.getMessage()))
                GeometryNode.LOGGER.warn("Cannot create area: {}", failure.getMessage());
        }
        return next(StandardPorts.FLOW_OUT.getId());
    }

    @Override
    public Object compute(GraphDataContext context, String portName) {
        return (StandardPorts.AREA.getId().equals(portName) || StandardPorts.RESOURCE_ID.getId().equals(portName))
                ? context.getNodeResult(portName) : null;
    }

    private static LiveValue<Vec3> captureXyz(PortDef port, Vec3 snapshot, ExpressionData expression) {
        return LiveValues.captureXyz(port, snapshot,
                ExpressionSpec.fromComponent(expression, 0),
                ExpressionSpec.fromComponent(expression, 1),
                ExpressionSpec.fromComponent(expression, 2));
    }

    private static void reportDiagnostics(GraphDataContext context, String areaId,
                                          String property, LiveValue<?> value) {
        for (String diagnostic : value.diagnostics()) {
            if (RateLimitedLog.acquire(context, "area_expression:" + areaId + ':' + property + ':' + diagnostic)) {
                GeometryNode.LOGGER.warn("Invalid Area expression for '{}' property '{}': {}",
                        areaId, property, diagnostic);
            }
        }
    }

    private static float positive(float value) {
        return Float.isFinite(value) ? Math.max(0.001F, Math.abs(value)) : 1.0F;
    }

    private static <T> T valueOr(T value, T fallback) {
        return value != null ? value : fallback;
    }
}
