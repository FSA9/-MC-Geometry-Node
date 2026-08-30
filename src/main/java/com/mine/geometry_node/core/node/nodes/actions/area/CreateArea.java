package com.mine.geometry_node.core.node.nodes.actions.area;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.engine.blueprint.spatial.AreaAddress;
import com.mine.geometry_node.core.engine.blueprint.spatial.AreaAnchor;
import com.mine.geometry_node.core.engine.blueprint.spatial.AreaEntityQuery;
import com.mine.geometry_node.core.engine.blueprint.spatial.AreaResourceStore;
import com.mine.geometry_node.core.engine.blueprint.spatial.AreaShape;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceId;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceIds;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceTypeRegistry;
import com.mine.geometry_node.core.node.NodeComment;
import com.mine.geometry_node.core.node.RegistryDataManager;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortDef;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.PortType;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
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
                .addRow(new PortRow(areaIdPort(""), null, UIHint.INPUT, null, null))
                .addRow(new PortRow(StandardPorts.DIMENSION.toInput(RegistryDataManager.DEFAULT_DIMENSION),
                        null, UIHint.SELECT, null,
                        Map.of(PortMetaKeys.DYNAMIC_REGISTRY_ID, RegistryDataManager.DIMENSION_REGISTRY_ID)))
                .addRow(new PortRow(
                        PortDef.create(ANCHOR_PORT, "geometry_node.port.area_anchor", PortType.STRING,
                                AreaAnchor.WORLD.id()).hiddenPin(),
                        null, UIHint.SELECT, null, Map.of(PortMetaKeys.OPTIONS, AreaAnchor.OPTIONS)))
                .addRow(new PortRow(
                        PortDef.create(SHAPE_PORT, "geometry_node.port.area_shape", PortType.STRING,
                                AreaShape.BOX.id()).hiddenPin(),
                        null, UIHint.SELECT, null, Map.of(PortMetaKeys.OPTIONS, AreaShape.OPTIONS)))
                .addRow(new PortRow(StandardPorts.CENTER.toInput(Vec3.ZERO), null, UIHint.VECTOR, null, null));

        switch (shape) {
            case SPHERE -> builder.addRow(new PortRow(StandardPorts.RADIUS.toInput((float) DEFAULT_RADIUS),
                    null, UIHint.INPUT, null, null));
            case CYLINDER -> {
                builder.addRow(new PortRow(StandardPorts.RADIUS.toInput((float) DEFAULT_RADIUS),
                        null, UIHint.INPUT, null, null));
                builder.addRow(new PortRow(PortDef.create(HEIGHT_PORT, "geometry_node.port.area_height",
                        PortType.FLOAT, (float) DEFAULT_HEIGHT), null, UIHint.INPUT, null, null));
                builder.addRow(new PortRow(StandardPorts.ROTATION.toInput(Vec3.ZERO), null, UIHint.VECTOR, null, null));
            }
            case BOX -> {
                builder.addRow(new PortRow(StandardPorts.SIZE_3.toInput(new Vec3(1, 1, 1)),
                        null, UIHint.VECTOR, null, null));
                builder.addRow(new PortRow(StandardPorts.ROTATION.toInput(Vec3.ZERO), null, UIHint.VECTOR, null, null));
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
            Vec3 size = readSize(context, shape);
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
                    AreaResourceStore.INSTANCE.upsert(hostLevel.getServer(), address, resourceOwner,
                            shape, center, size, rotation, anchorId);
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

    private Vec3 readSize(ExecutionContext context, AreaShape shape) {
        return switch (shape) {
            case SPHERE -> {
                float radius = positive(valueOr(getInput(context, StandardPorts.RADIUS.getId(), Float.class),
                        (float) DEFAULT_RADIUS));
                yield new Vec3(radius * 2.0D, radius * 2.0D, radius * 2.0D);
            }
            case CYLINDER -> {
                float radius = positive(valueOr(getInput(context, StandardPorts.RADIUS.getId(), Float.class),
                        (float) DEFAULT_RADIUS));
                float height = positive(valueOr(getInput(context, HEIGHT_PORT, Float.class),
                        (float) DEFAULT_HEIGHT));
                yield new Vec3(radius * 2.0D, height, radius * 2.0D);
            }
            case BOX -> AreaEntityQuery.sanitizeSize(valueOr(
                    getInput(context, StandardPorts.SIZE_3.getId(), Vec3.class), new Vec3(1, 1, 1)));
        };
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
