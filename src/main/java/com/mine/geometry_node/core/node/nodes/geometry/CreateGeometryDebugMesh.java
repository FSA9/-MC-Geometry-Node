package com.mine.geometry_node.core.node.nodes.geometry;

import com.mine.geometry_node.core.engine.graph.debug.DebugRendererSessionManager;
import com.mine.geometry_node.core.engine.graph.debug.DebugRenderChannel;
import com.mine.geometry_node.core.engine.graph.debug.DebugSourceId;
import com.mine.geometry_node.core.engine.graph.debug.geometry.GeometryDebugElement;
import com.mine.geometry_node.core.engine.graph.debug.geometry.GeometryDebugMeshFactory;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceId;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceIdCodec;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceIds;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceTypeRegistry;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.node.definition.node.NodeComment;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import com.mine.geometry_node.core.node.value.geometry.GeometryValue;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class CreateGeometryDebugMesh extends BaseNode {
    public static final String TYPE_ID = "create_geometry_debug_mesh";

    private static final int MAX_MESHES_PER_EXECUTION = 128;
    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.create_geometry_debug_mesh"))
                .comment(NodeComment.builder(TYPE_ID)
                        .text("summary")
                        .input(StandardPorts.FLOW_IN, "flow_in")
                        .output(StandardPorts.FLOW_OUT, "flow_out")
                        .input(StandardPorts.GEOMETRY, "geometry")
                        .input(StandardPorts.WORLD_POSITION, "world_position")
                        .input(StandardPorts.WORLD_ROTATION, "world_rotation")
                        .input(StandardPorts.PIVOT, "pivot")
                        .input(StandardPorts.TRANSLATION, "translation")
                        .input(StandardPorts.ROTATION, "rotation")
                        .input(StandardPorts.SIZE_3, "size_3")
                        .input(StandardPorts.KEY, "key")
                        .output(StandardPorts.RESOURCE_ID, "resource_id")
                        .build())
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.GEOMETRY.toInput(), null, UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.WORLD_POSITION.toInput(Vec3.ZERO), null, UIHint.VECTOR, null, null))
                .addRow(new PortRow(StandardPorts.WORLD_ROTATION.toInput(Vec3.ZERO), null, UIHint.VECTOR, null, null))
                .addRow(new PortRow(StandardPorts.PIVOT.toInput(Vec3.ZERO), null, UIHint.VECTOR, null, null))
                .addRow(new PortRow(StandardPorts.TRANSLATION.toInput(Vec3.ZERO), null, UIHint.VECTOR, null, null))
                .addRow(new PortRow(StandardPorts.ROTATION.toInput(Vec3.ZERO), null, UIHint.VECTOR, null, null))
                .addRow(new PortRow(StandardPorts.SIZE_3.toInput(new Vec3(1, 1, 1)), null, UIHint.VECTOR, null, null))
                .addRow(new PortRow(StandardPorts.KEY.toInput(""), StandardPorts.RESOURCE_ID.toOutput(), UIHint.INPUT, null, null))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)) {
            return next(StandardPorts.FLOW_OUT.getId());
        }

        GeometryValue geometry = getInput(context, StandardPorts.GEOMETRY.getId(), GeometryValue.class);
        if (geometry == null || geometry.isEmpty()) {
            return next(StandardPorts.FLOW_OUT.getId());
        }

        Vec3 worldPosition = valueOr(getInput(context, StandardPorts.WORLD_POSITION.getId(), Vec3.class), Vec3.ZERO);
        Vec3 worldRotation = valueOr(getInput(context, StandardPorts.WORLD_ROTATION.getId(), Vec3.class), Vec3.ZERO);
        Vec3 pivot = valueOr(getInput(context, StandardPorts.PIVOT.getId(), Vec3.class), Vec3.ZERO);
        Vec3 translation = valueOr(getInput(context, StandardPorts.TRANSLATION.getId(), Vec3.class), Vec3.ZERO);
        Vec3 rotation = valueOr(getInput(context, StandardPorts.ROTATION.getId(), Vec3.class), Vec3.ZERO);
        Vec3 scale = valueOr(getInput(context, StandardPorts.SIZE_3.getId(), Vec3.class), new Vec3(1, 1, 1));
        String key = getInput(context, StandardPorts.KEY.getId(), String.class);
        GraphResourceId resourceId = GraphResourceIds.forKey(context, stableNodeId(context),
                GraphResourceTypeRegistry.GEOMETRY_DEBUG, key);
        DebugSourceId sourceId = DebugSourceId.graph(DebugRenderChannel.GEOMETRY, resourceId);

        List<GeometryDebugElement> meshes = GeometryDebugMeshFactory.buildMeshes(
                sourceId,
                context.getGraphId(),
                "created",
                geometry,
                MAX_MESHES_PER_EXECUTION,
                worldPosition,
                worldRotation,
                pivot,
                translation,
                rotation,
                scale
        );
        if (!meshes.isEmpty()) {
            DebugRendererSessionManager.replaceSourceGeometry(level, sourceId, meshes);
            context.setTempData(tempKey(context), GraphResourceIdCodec.encode(resourceId));
        }
        return next(StandardPorts.FLOW_OUT.getId());
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        if (StandardPorts.RESOURCE_ID.getId().equals(portName)) {
            return context.getTempData(tempKey(context));
        }
        return null;
    }

    private static String tempKey(ExecutionContext context) {
        return TYPE_ID + ":input:" + context.getCurrentNodeId();
    }

    private static String stableNodeId(ExecutionContext context) {
        String stableId = context.getCurrentNodeStableId();
        return stableId == null || stableId.isBlank()
                ? Integer.toString(context.getCurrentNodeId()) : stableId;
    }

    private static <T> T valueOr(T value, T fallback) {
        return value != null ? value : fallback;
    }

}
