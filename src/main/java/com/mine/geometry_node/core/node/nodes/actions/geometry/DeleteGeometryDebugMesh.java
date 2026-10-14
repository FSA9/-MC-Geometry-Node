package com.mine.geometry_node.core.node.nodes.actions.geometry;

import com.mine.geometry_node.core.engine.graph.debug.DebugRendererSessionManager;
import com.mine.geometry_node.core.engine.graph.debug.DebugRenderChannel;
import com.mine.geometry_node.core.engine.graph.debug.DebugSourceId;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceId;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceReferences;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceTypeRegistry;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.util.ResourceNodeSupport;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

public class DeleteGeometryDebugMesh extends BaseNode {
    public static final String TYPE_ID = "delete_geometry_debug_mesh";

    @Override
    public NodeDef getDefaultDefinition() {
        return definition(null);
    }

    @Override
    public NodeDef getDefinition(NodeData data) {
        return definition(data);
    }

    private NodeDef definition(NodeData data) {
        var builder = NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.delete_geometry_debug_mesh"))
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addPassthroughInput(StandardPorts.RESOURCE_ID.toInput(""), UIHint.INPUT);
        return ResourceNodeSupport.addLegacyInput(builder, data, StandardPorts.KEY.getId()).build();
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
        String encoded = ResourceNodeSupport.read(context, StandardPorts.KEY.getId());
        try {
            return GraphResourceReferences.resolveExisting(context, GraphResourceTypeRegistry.GEOMETRY_DEBUG, encoded);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
