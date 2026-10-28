package com.mine.geometry_node.core.node.nodes.actions.collision;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.engine.graph.collision.GeometryCollisionService;
import com.mine.geometry_node.core.engine.graph.data.GraphDataContext;
import com.mine.geometry_node.core.engine.graph.expression.ExpressionSpec;
import com.mine.geometry_node.core.engine.graph.expression.LiveValue;
import com.mine.geometry_node.core.engine.graph.expression.LiveValues;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceIdCodec;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceReferences;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceTypeRegistry;
import com.mine.geometry_node.core.node.definition.node.NodeComment;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.PortDef;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.util.ResourceNodeSupport;
import com.mine.geometry_node.core.node.util.AnchorNodeSupport;
import com.mine.geometry_node.core.engine.spatial.SpatialAnchor;
import com.mine.geometry_node.core.node.value.geometry.GeometryValue;
import com.mine.geometry_node.core.utils.RateLimitedLog;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/** Creates an independently owned collision resource from procedural geometry. */
public final class CreateGeometryCollision extends BaseNode {
    public static final String TYPE_ID = "create_geometry_collision";
    public static final PortDef POSITION = AnchorNodeSupport.WORLD_OFFSET;
    public static final PortDef WORLD_ROTATION = AnchorNodeSupport.WORLD_ROTATION;
    public static final PortDef PIVOT = StandardPorts.PIVOT.toInput(Vec3.ZERO).liveExpression();
    public static final PortDef TRANSLATION = AnchorNodeSupport.LOCAL_OFFSET;
    public static final PortDef ROTATION = AnchorNodeSupport.LOCAL_ROTATION;
    public static final PortDef SCALE = StandardPorts.SIZE_3.toInput(new Vec3(1, 1, 1)).liveExpression();

    @Override
    public NodeDef getDefaultDefinition() {
        NodeDef.Builder builder = NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node." + TYPE_ID))
                .comment(NodeComment.builder(TYPE_ID).text("summary")
                        .input(StandardPorts.SPATIAL_ANCHOR, "geometry_node.anchor.help")
                        .input(StandardPorts.GEOMETRY, "geometry")
                        .input(POSITION.id(), "geometry_node.transform.world_offset.help")
                        .input(StandardPorts.WORLD_ROTATION, "world_rotation")
                        .input(StandardPorts.PIVOT, "pivot")
                        .input(TRANSLATION.id(), "geometry_node.transform.local_offset.help")
                        .input(ROTATION.id(), "geometry_node.transform.local_rotation.help")
                        .input(StandardPorts.SIZE_3, "size_3")
                        .input(StandardPorts.KEY, "resource_id_input")
                        .build())
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(ResourceNodeSupport.creationRow(StandardPorts.KEY.getId(), StandardPorts.RESOURCE_ID.getId()))
                .addPassthroughInput(StandardPorts.GEOMETRY.toInput(), UIHint.DEFAULT);
        builder.addRow(AnchorNodeSupport.row(StandardPorts.SPATIAL_ANCHOR));
        return builder.addPassthroughInput(POSITION, UIHint.VECTOR)
                .addPassthroughInput(WORLD_ROTATION, UIHint.VECTOR)
                .addRow(PortRow.passthrough(TRANSLATION, UIHint.VECTOR, null, null))
                .addRow(PortRow.passthrough(ROTATION, UIHint.VECTOR, null, null))
                .addRow(PortRow.passthrough(PIVOT, UIHint.VECTOR, null, null))
                .addRow(PortRow.passthrough(SCALE, UIHint.VECTOR, null, null)).build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        context.setNodeResult(StandardPorts.RESOURCE_ID.getId(), "");
        GeometryValue geometry = getInput(context, StandardPorts.GEOMETRY.getId(), GeometryValue.class);
        if (context.getLevel() == null || geometry == null || geometry.isEmpty())
            return next(StandardPorts.FLOW_OUT.getId());
        try {
            String input = ResourceNodeSupport.read(context, StandardPorts.KEY.getId());
            var id = GraphResourceReferences.resolveCreate(context, stableNodeId(context), GraphResourceTypeRegistry.GEOMETRY_COLLISION, input);
            var target = context.getLevel().getServer().getLevel(id.scope().dimension());
            if (target == null) return next(StandardPorts.FLOW_OUT.getId());
            SpatialAnchor anchor = AnchorNodeSupport.read(context, StandardPorts.SPATIAL_ANCHOR);
            if (anchor == null || !anchor.dimensionOr(context.getLevel().dimension()).equals(target.dimension()))
                return next(StandardPorts.FLOW_OUT.getId());
            GeometryCollisionService.INSTANCE.create(target, id, geometry, anchor.isEntity() ? anchor : SpatialAnchor.world(anchor.dimension(), Vec3.ZERO),
                    capture(context, POSITION, anchor), capture(context, WORLD_ROTATION, anchor), capture(context, PIVOT, anchor),
                    capture(context, TRANSLATION, anchor), capture(context, ROTATION, anchor), capture(context, SCALE, anchor));
            context.setNodeResult(StandardPorts.RESOURCE_ID.getId(), GraphResourceIdCodec.encode(id));
        } catch (Exception failure) {
            if (RateLimitedLog.acquire(context, TYPE_ID + ':' + failure.getMessage()))
                GeometryNode.LOGGER.warn("Cannot create geometry collision: {}", failure.getMessage());
        }
        return next(StandardPorts.FLOW_OUT.getId());
    }

    private LiveValue<Vec3> capture(GraphDataContext context, PortDef port, SpatialAnchor anchor) {
        Vec3 value = getInput(context, port.id(), Vec3.class);
        var expression = getInputExpression(context, port.id());
        if (port == POSITION) {
            expression = AnchorNodeSupport.positionExpression(anchor,
                    AnchorNodeSupport.readExpression(context, StandardPorts.SPATIAL_ANCHOR), value, expression);
            value = AnchorNodeSupport.position(anchor, value);
        }
        var captured = LiveValues.captureXyz(port, value == null ? (Vec3) port.defaultValue() : value,
                ExpressionSpec.fromComponent(expression, 0), ExpressionSpec.fromComponent(expression, 1),
                ExpressionSpec.fromComponent(expression, 2));
        if (!captured.diagnostics().isEmpty())
            throw new IllegalArgumentException(String.join("; ", captured.diagnostics()));
        return captured;
    }

    @Override
    public Object compute(GraphDataContext context, String portName) {
        return StandardPorts.RESOURCE_ID.getId().equals(portName)
                ? context.getNodeResult(portName) : null;
    }

    static String stableNodeId(ExecutionContext context) {
        String id = context.getCurrentNodeStableId();
        return id == null || id.isBlank() ? Integer.toString(context.getCurrentNodeId()) : id;
    }
}
