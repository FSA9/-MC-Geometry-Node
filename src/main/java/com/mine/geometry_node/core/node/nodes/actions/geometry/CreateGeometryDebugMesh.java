package com.mine.geometry_node.core.node.nodes.actions.geometry;

import com.mine.geometry_node.core.engine.graph.data.GraphDataContext;

import com.mine.geometry_node.core.engine.graph.debug.DebugRendererSessionManager;
import com.mine.geometry_node.core.engine.graph.debug.DebugRenderChannel;
import com.mine.geometry_node.core.engine.graph.debug.DebugSourceId;
import com.mine.geometry_node.core.engine.graph.debug.geometry.GeometryDebugElement;
import com.mine.geometry_node.core.engine.graph.debug.geometry.GeometryDebugPlacement;
import com.mine.geometry_node.core.engine.graph.debug.geometry.GeometryDebugMeshFactory;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceId;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceIdCodec;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceReferences;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceTypeRegistry;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.node.definition.node.NodeComment;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.util.ResourceNodeSupport;
import com.mine.geometry_node.core.node.util.AnchorNodeSupport;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.engine.spatial.SpatialAnchor;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.PortDef;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import com.mine.geometry_node.core.node.value.geometry.GeometryValue;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
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
                        .input(StandardPorts.SPATIAL_ANCHOR, "geometry_node.anchor.help")
                        .input(AnchorNodeSupport.WORLD_OFFSET.id(), "geometry_node.transform.world_offset.help")
                        .input(StandardPorts.WORLD_ROTATION, "world_rotation")
                        .input(StandardPorts.PIVOT, "pivot")
                        .input(AnchorNodeSupport.LOCAL_OFFSET.id(), "geometry_node.transform.local_offset.help")
                        .input(AnchorNodeSupport.LOCAL_ROTATION.id(), "geometry_node.transform.local_rotation.help")
                        .input(StandardPorts.SIZE_3, "size_3")
                        .input(StandardPorts.KEY, "resource_id_input")
                        .build())
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(ResourceNodeSupport.creationRow(StandardPorts.KEY.getId(), StandardPorts.RESOURCE_ID.getId()))
                .addPassthroughInput(StandardPorts.GEOMETRY.toInput(), UIHint.DEFAULT)
                .addRow(AnchorNodeSupport.row(StandardPorts.SPATIAL_ANCHOR))
                .addPassthroughInput(snapshotPort(AnchorNodeSupport.WORLD_OFFSET), UIHint.VECTOR)
                .addPassthroughInput(snapshotPort(AnchorNodeSupport.WORLD_ROTATION), UIHint.VECTOR)
                .addRow(PortRow.passthrough(snapshotPort(AnchorNodeSupport.LOCAL_OFFSET), UIHint.VECTOR, null, null))
                .addRow(PortRow.passthrough(snapshotPort(AnchorNodeSupport.LOCAL_ROTATION), UIHint.VECTOR, null, null))
                .addRow(PortRow.passthrough(StandardPorts.PIVOT.toInput(Vec3.ZERO), UIHint.VECTOR, null, null))
                .addRow(PortRow.passthrough(StandardPorts.SIZE_3.toInput(new Vec3(1, 1, 1)), UIHint.VECTOR, null, null))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        context.setNodeResult(StandardPorts.RESOURCE_ID.getId(), "");
        if (!(context.getLevel() instanceof ServerLevel level)) {
            return next(StandardPorts.FLOW_OUT.getId());
        }

        GeometryValue geometry = getInput(context, StandardPorts.GEOMETRY.getId(), GeometryValue.class);
        if (geometry == null || geometry.isEmpty()) {
            return next(StandardPorts.FLOW_OUT.getId());
        }

        SpatialAnchor shared = AnchorNodeSupport.capture(context, StandardPorts.SPATIAL_ANCHOR);
        if (shared == null) return next(StandardPorts.FLOW_OUT.getId());
        ServerLevel anchorLevel = level.getServer().getLevel(shared.dimensionOr(level.dimension()));
        Entity anchor = shared.isEntity() && anchorLevel != null ? anchorLevel.getEntity(shared.entityId()) : null;
        if (shared.isEntity() && (anchor == null || !anchor.isAlive() || anchor.isRemoved()))
            return next(StandardPorts.FLOW_OUT.getId());
        Vec3 worldPosition = AnchorNodeSupport.vector(context, AnchorNodeSupport.WORLD_OFFSET);
        Vec3 worldRotation = AnchorNodeSupport.vector(context, AnchorNodeSupport.WORLD_ROTATION);
        Vec3 pivot = valueOr(getInput(context, StandardPorts.PIVOT.getId(), Vec3.class), Vec3.ZERO);
        Vec3 translation = valueOr(getInput(context, AnchorNodeSupport.LOCAL_OFFSET.id(), Vec3.class), Vec3.ZERO);
        Vec3 rotation = valueOr(getInput(context, AnchorNodeSupport.LOCAL_ROTATION.id(), Vec3.class), Vec3.ZERO);
        Vec3 scale = valueOr(getInput(context, StandardPorts.SIZE_3.getId(), Vec3.class), new Vec3(1, 1, 1));
        String input = ResourceNodeSupport.read(context, StandardPorts.KEY.getId());
        GraphResourceId resourceId;
        try {
            resourceId = GraphResourceReferences.resolveCreate(context, stableNodeId(context),
                    GraphResourceTypeRegistry.GEOMETRY_DEBUG, input);
        } catch (IllegalArgumentException invalid) {
            return next(StandardPorts.FLOW_OUT.getId());
        }
        ServerLevel target = level.getServer().getLevel(resourceId.scope().dimension());
        if (target == null || !target.dimension().equals(shared.dimensionOr(level.dimension())) || (anchor != null && anchor.level() != target))
            return next(StandardPorts.FLOW_OUT.getId());
        DebugSourceId sourceId = DebugSourceId.graph(DebugRenderChannel.GEOMETRY, resourceId);

        List<GeometryDebugElement> meshes = GeometryDebugMeshFactory.buildMeshes(
                sourceId,
                context.getGraphId(),
                "created",
                geometry,
                MAX_MESHES_PER_EXECUTION,
                Vec3.ZERO,
                Vec3.ZERO,
                pivot,
                translation,
                rotation,
                scale
        );
        GeometryDebugPlacement placement = new GeometryDebugPlacement(shared, worldPosition, worldRotation);
        meshes = meshes.stream().map(mesh -> mesh.withPlacement(placement, anchor == null ? -1 : anchor.getId())).toList();
        if (!meshes.isEmpty()) {
            DebugRendererSessionManager.replaceSourceGeometry(target, sourceId, meshes);
            context.setNodeResult(StandardPorts.RESOURCE_ID.getId(), GraphResourceIdCodec.encode(resourceId));
        }
        return next(StandardPorts.FLOW_OUT.getId());
    }

    @Override
    public Object compute(GraphDataContext context, String portName) {
        if (StandardPorts.RESOURCE_ID.getId().equals(portName)) {
            return context.getNodeResult(StandardPorts.RESOURCE_ID.getId());
        }
        return null;
    }

    private static PortDef snapshotPort(PortDef port) {
        return new PortDef(port.id(), port.displayName(), port.type(), port.defaultValue(), port.hidePin(), false);
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
