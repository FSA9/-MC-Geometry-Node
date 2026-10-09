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
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceIds;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceTypeRegistry;
import com.mine.geometry_node.core.node.definition.node.NodeComment;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.PortDef;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.value.geometry.GeometryValue;
import com.mine.geometry_node.core.utils.RateLimitedLog;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Creates an independently owned collision resource from procedural geometry. */
public final class CreateGeometryCollision extends BaseNode {
    public static final String TYPE_ID = "create_geometry_collision";
    public static final PortDef POSITION = StandardPorts.WORLD_POSITION.toInput(Vec3.ZERO).liveExpression();
    public static final PortDef WORLD_ROTATION = StandardPorts.WORLD_ROTATION.toInput(Vec3.ZERO).liveExpression();
    public static final PortDef PIVOT = StandardPorts.PIVOT.toInput(Vec3.ZERO).liveExpression();
    public static final PortDef TRANSLATION = StandardPorts.TRANSLATION.toInput(Vec3.ZERO).liveExpression();
    public static final PortDef ROTATION = StandardPorts.ROTATION.toInput(Vec3.ZERO).liveExpression();
    public static final PortDef SCALE = StandardPorts.SIZE_3.toInput(new Vec3(1, 1, 1)).liveExpression();

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node." + TYPE_ID))
                .comment(NodeComment.builder(TYPE_ID).text("summary")
                        .input(StandardPorts.GEOMETRY, "geometry")
                        .input(StandardPorts.SOURCE_ENTITY, "source_entity")
                        .input(StandardPorts.WORLD_POSITION, "world_position")
                        .input(StandardPorts.WORLD_ROTATION, "world_rotation")
                        .input(StandardPorts.PIVOT, "pivot")
                        .input(StandardPorts.TRANSLATION, "translation")
                        .input(StandardPorts.ROTATION, "rotation")
                        .input(StandardPorts.SIZE_3, "size_3")
                        .input(StandardPorts.KEY, "key")
                        .output(StandardPorts.RESOURCE_ID, "resource_id").build())
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.RESOURCE_ID.toOutput(), UIHint.DEFAULT, null, null))
                .addPassthroughInput(StandardPorts.GEOMETRY.toInput(), UIHint.DEFAULT)
                .addPassthroughInput(StandardPorts.SOURCE_ENTITY.toInput(), UIHint.DEFAULT)
                .addPassthroughInput(POSITION, UIHint.VECTOR)
                .addPassthroughInput(WORLD_ROTATION, UIHint.VECTOR)
                .addPassthroughInput(PIVOT, UIHint.VECTOR)
                .addPassthroughInput(TRANSLATION, UIHint.VECTOR)
                .addPassthroughInput(ROTATION, UIHint.VECTOR)
                .addPassthroughInput(SCALE, UIHint.VECTOR)
                .addPassthroughInput(StandardPorts.KEY.toInput(""), UIHint.INPUT).build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        context.setNodeResult(StandardPorts.RESOURCE_ID.getId(), "");
        GeometryValue geometry = getInput(context, StandardPorts.GEOMETRY.getId(), GeometryValue.class);
        if (context.getLevel() == null || geometry == null || geometry.isEmpty())
            return next(StandardPorts.FLOW_OUT.getId());
        try {
            String key = getInput(context, StandardPorts.KEY.getId(), String.class);
            var id = GraphResourceIds.forKey(context, stableNodeId(context), GraphResourceTypeRegistry.GEOMETRY_COLLISION, key);
            Entity anchor = getInputFromList(context, StandardPorts.SOURCE_ENTITY.getId(), 0, Entity.class);
            GeometryCollisionService.INSTANCE.create(context.getLevel(), id, geometry, anchor,
                    capture(context, POSITION), capture(context, WORLD_ROTATION), capture(context, PIVOT),
                    capture(context, TRANSLATION), capture(context, ROTATION), capture(context, SCALE));
            context.setNodeResult(StandardPorts.RESOURCE_ID.getId(), GraphResourceIdCodec.encode(id));
        } catch (Exception failure) {
            if (RateLimitedLog.acquire(context, TYPE_ID + ':' + failure.getMessage()))
                GeometryNode.LOGGER.warn("Cannot create geometry collision: {}", failure.getMessage());
        }
        return next(StandardPorts.FLOW_OUT.getId());
    }

    private LiveValue<Vec3> capture(GraphDataContext context, PortDef port) {
        Vec3 value = getInput(context, port.id(), Vec3.class);
        var expression = getInputExpression(context, port.id());
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
