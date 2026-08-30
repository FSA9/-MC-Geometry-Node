package com.mine.geometry_node.core.node.nodes.geometry;

import com.mine.geometry_node.core.engine.graph.debug.DebugRendererSessionManager;
import com.mine.geometry_node.core.engine.graph.debug.DebugRenderChannel;
import com.mine.geometry_node.core.engine.graph.debug.DebugSourceId;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceId;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceIdCodec;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceIds;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceTypeRegistry;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

public class DeleteGeometryDebugMesh extends BaseNode {
    public static final String TYPE_ID = "delete_geometry_debug_mesh";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.delete_geometry_debug_mesh"))
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.RESOURCE_ID.toInput(""), null, UIHint.INPUT, null, null))
                .addRow(new PortRow(StandardPorts.KEY.toInput(""), null, UIHint.INPUT, null, null))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)) {
            return next(StandardPorts.FLOW_OUT.getId());
        }

        GraphResourceId resourceId = resolveResourceId(context);
        if (resourceId != null && resourceId.type().equals(GraphResourceTypeRegistry.GEOMETRY_DEBUG)) {
            ServerLevel resourceLevel = level.getServer().getLevel(resourceId.scope().dimension());
            if (resourceLevel != null) {
                DebugRendererSessionManager.removeSourceGeometry(resourceLevel,
                        DebugSourceId.graph(DebugRenderChannel.GEOMETRY, resourceId));
            }
        }
        return next(StandardPorts.FLOW_OUT.getId());
    }

    private GraphResourceId resolveResourceId(ExecutionContext context) {
        String encoded = getInput(context, StandardPorts.RESOURCE_ID.getId(), String.class);
        if (encoded != null && !encoded.isBlank()) {
            try {
                return GraphResourceIdCodec.decode(encoded.trim());
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        String key = getInput(context, StandardPorts.KEY.getId(), String.class);
        return key == null || key.isBlank()
                ? null
                : GraphResourceIds.forKey(context, stableNodeId(context),
                GraphResourceTypeRegistry.GEOMETRY_DEBUG, key);
    }

    private static String stableNodeId(ExecutionContext context) {
        String stableId = context.getCurrentNodeStableId();
        return stableId == null || stableId.isBlank()
                ? Integer.toString(context.getCurrentNodeId()) : stableId;
    }
}
