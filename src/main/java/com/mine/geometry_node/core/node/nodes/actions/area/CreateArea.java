package com.mine.geometry_node.core.node.nodes.actions.area;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.engine.blueprint.spatial.area.AreaAddress;
import com.mine.geometry_node.core.engine.blueprint.spatial.area.AreaAnchor;
import com.mine.geometry_node.core.engine.blueprint.spatial.area.AreaEntityQuery;
import com.mine.geometry_node.core.engine.blueprint.spatial.area.AreaResourceStore;
import com.mine.geometry_node.core.engine.blueprint.spatial.area.AreaShape;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceId;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceIds;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceTypeRegistry;
import com.mine.geometry_node.core.engine.graph.expression.ExpressionData;
import com.mine.geometry_node.core.engine.graph.expression.ExpressionSpec;
import com.mine.geometry_node.core.engine.graph.expression.LiveValue;
import com.mine.geometry_node.core.engine.graph.expression.LiveValues;
import com.mine.geometry_node.core.node.definition.node.NodeComment;
import com.mine.geometry_node.core.node.RegistryDataManager;
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
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;

public final class CreateArea extends BaseNode {
    public static final String TYPE_ID = "create_area";
    public static final String ANCHOR_PORT = "area_anchor";
    public static final String SHAPE_PORT = "area_shape";
    public static final String HEIGHT_PORT = StandardPorts.HEIGHT.getId();
    public static final double DEFAULT_RADIUS = 1.0D;
    public static final double DEFAULT_HEIGHT = 2.0D;
    public static final PortDef CENTER_PORT = StandardPorts.CENTER.toInput(Vec3.ZERO).liveExpression();
    public static final PortDef SIZE_PORT = StandardPorts.SIZE_3
            .toInput(new Vec3(1, 1, 1)).liveExpression();
    public static final PortDef ROTATION_PORT = StandardPorts.ROTATION.toInput(Vec3.ZERO).liveExpression();
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
        Object raw = instanceData != null ? instanceData.inputs.get(SHAPE_PORT) : null;
        return buildDef(AreaShape.fromId(raw instanceof String text ? text : null));
    }

    private NodeDef buildDef(AreaShape shape) {
        NodeComment.Builder comment = NodeComment.builder(TYPE_ID)
                .text("summary")
                .input(StandardPorts.FLOW_IN, "flow_in")
                .output(StandardPorts.FLOW_OUT, "flow_out")
                .output(StandardPorts.BOOL, "bool")
                .input(StandardPorts.AREA_ID, "area_id")
                .input(StandardPorts.DIMENSION, "dimension")
                .input(ANCHOR_PORT, "anchor")
                .input(SHAPE_PORT, "shape")
                .input(StandardPorts.CENTER, "center");
        switch (shape) {
            case SPHERE -> comment.input(StandardPorts.RADIUS, "radius");
            case CYLINDER -> comment.input(StandardPorts.RADIUS, "radius")
                    .input(HEIGHT_PORT, "height")
                    .input(StandardPorts.ROTATION, "rotation");
            case BOX -> comment.input(StandardPorts.SIZE_3, "size")
                    .input(StandardPorts.ROTATION, "rotation");
        }

        NodeDef.Builder builder = NodeDef.builder(TYPE_ID, NodeType.ACTION,
                        Component.translatable("geometry_node.node." + TYPE_ID))
                .comment(comment.build())
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(),
                        UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.BOOL.toOutput(), UIHint.DEFAULT, null, null))
                .addPassthroughInput(areaIdPort(""), UIHint.INPUT)
                .addPassthroughInput(StandardPorts.DIMENSION.toInput(RegistryDataManager.DEFAULT_DIMENSION), UIHint.SELECT, null, Map.of(PortMetaKeys.DYNAMIC_REGISTRY_ID, RegistryDataManager.DIMENSION_REGISTRY_ID))
                .addPassthroughInput(PortDef.create(ANCHOR_PORT, "geometry_node.port.area_anchor", PortType.STRING,
                                AreaAnchor.WORLD.id()).hiddenPin(), UIHint.SELECT, null, Map.of(PortMetaKeys.OPTIONS, AreaAnchor.OPTIONS))
                .addPassthroughInput(PortDef.create(SHAPE_PORT, "geometry_node.port.area_shape", PortType.STRING,
                                AreaShape.BOX.id()).hiddenPin(), UIHint.SELECT, null, Map.of(PortMetaKeys.OPTIONS, AreaShape.OPTIONS))
                .addPassthroughInput(CENTER_PORT, UIHint.VECTOR);

        switch (shape) {
            case SPHERE -> builder.addPassthroughInput(RADIUS_PORT, UIHint.INPUT);
            case CYLINDER -> {
                builder.addPassthroughInput(RADIUS_PORT, UIHint.INPUT);
                builder.addPassthroughInput(HEIGHT_INPUT, UIHint.INPUT);
                builder.addPassthroughInput(ROTATION_PORT, UIHint.VECTOR);
            }
            case BOX -> {
                builder.addPassthroughInput(SIZE_PORT, UIHint.VECTOR);
                builder.addPassthroughInput(ROTATION_PORT, UIHint.VECTOR);
            }
        }
        return builder.build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        boolean success = false;
        String areaId = getInput(context, StandardPorts.AREA_ID.getId(), String.class);
        ServerLevel hostLevel = context.getLevel();
        ServerLevel areaLevel = hostLevel != null
                ? RegistryDataManager.resolveDimension(hostLevel.getServer(),
                        getInput(context, StandardPorts.DIMENSION.getId(), String.class))
                : null;
        if (areaLevel != null && areaId != null && !areaId.isBlank()) {
            AreaShape shape = AreaShape.fromId(getInput(context, SHAPE_PORT, String.class));
            Vec3 center = valueOr(getInput(context, StandardPorts.CENTER.getId(), Vec3.class), Vec3.ZERO);
            Vec3 size = AreaEntityQuery.sanitizeSize(valueOr(
                    getInput(context, StandardPorts.SIZE_3.getId(), Vec3.class), new Vec3(1, 1, 1)));
            float radius = positive(valueOr(getInput(context, StandardPorts.RADIUS.getId(), Float.class),
                    (float) DEFAULT_RADIUS));
            float height = positive(valueOr(getInput(context, HEIGHT_PORT, Float.class),
                    (float) DEFAULT_HEIGHT));
            Vec3 rotation = shape == AreaShape.SPHERE
                    ? Vec3.ZERO
                    : valueOr(getInput(context, StandardPorts.ROTATION.getId(), Vec3.class), Vec3.ZERO);
            AreaAnchor anchor = AreaAnchor.fromId(getInput(context, ANCHOR_PORT, String.class));
            Entity owner = context.getGraphOwnerEntity();
            boolean validOwnerAnchor = anchor != AreaAnchor.OWNER || owner != null
                    && owner.level() instanceof ServerLevel ownerLevel
                    && ownerLevel.dimension().equals(areaLevel.dimension());
            UUID anchorId = anchor == AreaAnchor.OWNER && validOwnerAnchor ? owner.getUUID() : null;
            if (validOwnerAnchor) {
                String stableId = context.getCurrentNodeStableId();
                if (stableId == null || stableId.isBlank()) stableId = Integer.toString(context.getCurrentNodeId());
                AreaAddress address = AreaAddress.tryCreate(areaLevel.dimension(), areaId);
                if (address != null) {
                    GraphResourceId resourceOwner = GraphResourceIds.forKey(context, stableId,
                            GraphResourceTypeRegistry.AREA, address.id());
                    LiveValue<Vec3> liveCenter = captureXyz(CENTER_PORT, center,
                            getInputExpression(context, StandardPorts.CENTER.getId()));
                    LiveValue<Vec3> liveSize = captureXyz(SIZE_PORT, size,
                            getInputExpression(context, StandardPorts.SIZE_3.getId()));
                    LiveValue<Vec3> liveRotation = captureXyz(ROTATION_PORT, rotation,
                            getInputExpression(context, StandardPorts.ROTATION.getId()));
                    LiveValue<Float> liveRadius = LiveValues.captureFloat(RADIUS_PORT, radius,
                            ExpressionSpec.fromScalar(getInputExpression(
                                    context, StandardPorts.RADIUS.getId())));
                    LiveValue<Float> liveHeight = LiveValues.captureFloat(HEIGHT_INPUT, height,
                            ExpressionSpec.fromScalar(getInputExpression(context, HEIGHT_PORT)));
                    reportDiagnostics(address.id(), "center", liveCenter);
                    reportDiagnostics(address.id(), "size", liveSize);
                    reportDiagnostics(address.id(), "rotation", liveRotation);
                    reportDiagnostics(address.id(), "radius", liveRadius);
                    reportDiagnostics(address.id(), "height", liveHeight);
                    AreaResourceStore.INSTANCE.upsert(hostLevel.getServer(), address, resourceOwner,
                            shape, areaLevel.getGameTime(), liveCenter, liveSize, liveRotation,
                            liveRadius, liveHeight, anchorId);
                    success = true;
                }
            }
        }
        context.setTempData(tempKey(context), success);
        return next(StandardPorts.FLOW_OUT.getId());
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        return StandardPorts.BOOL.getId().equals(portName) ? context.getTempData(tempKey(context)) : null;
    }

    private static LiveValue<Vec3> captureXyz(PortDef port, Vec3 snapshot, ExpressionData expression) {
        return LiveValues.captureXyz(port, snapshot,
                ExpressionSpec.fromComponent(expression, 0),
                ExpressionSpec.fromComponent(expression, 1),
                ExpressionSpec.fromComponent(expression, 2));
    }

    private static void reportDiagnostics(String areaId, String property, LiveValue<?> value) {
        for (String diagnostic : value.diagnostics()) {
            GeometryNode.LOGGER.warn("Invalid Area expression for '{}' property '{}': {}",
                    areaId, property, diagnostic);
        }
    }

    private static PortDef areaIdPort(String defaultValue) {
        return StandardPorts.AREA_ID.toInput(defaultValue);
    }

    private static float positive(float value) {
        return Float.isFinite(value) ? Math.max(0.001F, Math.abs(value)) : 1.0F;
    }

    private static String tempKey(ExecutionContext context) {
        return TYPE_ID + ":" + context.getCurrentNodeId() + ":" + StandardPorts.BOOL.getId();
    }

    private static <T> T valueOr(T value, T fallback) {
        return value != null ? value : fallback;
    }
}
