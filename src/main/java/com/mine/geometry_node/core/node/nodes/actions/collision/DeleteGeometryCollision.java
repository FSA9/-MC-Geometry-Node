package com.mine.geometry_node.core.node.nodes.actions.collision;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.engine.graph.collision.GeometryCollisionService;
import com.mine.geometry_node.core.engine.graph.data.GraphDataContext;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceReferences;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceTypeRegistry;
import com.mine.geometry_node.core.node.definition.node.NodeComment;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.util.ResourceNodeSupport;
import net.minecraft.network.chat.Component;

public final class DeleteGeometryCollision extends BaseNode {
    public static final String TYPE_ID = "delete_geometry_collision";

    @Override
    public NodeDef getDefaultDefinition() {
        return definition(null);
    }

    @Override
    public NodeDef getDefinition(NodeData data) {
        return definition(data);
    }

    private NodeDef definition(NodeData data) {
        var builder = NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node." + TYPE_ID))
                .comment(NodeComment.builder(TYPE_ID).text("summary")
                        .input(StandardPorts.RESOURCE_ID, "resource_id")
                        .output(StandardPorts.BOOL, "bool").build())
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.BOOL.toOutput(), UIHint.DEFAULT, null, null))
                .addPassthroughInput(StandardPorts.RESOURCE_ID.toInput(""), UIHint.INPUT);
        return ResourceNodeSupport.addLegacyInput(builder, data, StandardPorts.KEY.getId()).build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        boolean removed = false;
        String encoded = ResourceNodeSupport.read(context, StandardPorts.KEY.getId());
        if (context.getLevel() != null && !encoded.isBlank()) {
            try {
                var id = GraphResourceReferences.resolveExisting(context, GraphResourceTypeRegistry.GEOMETRY_COLLISION,
                        encoded);
                if (id.type().equals(GraphResourceTypeRegistry.GEOMETRY_COLLISION)) {
                    var level = context.getLevel().getServer().getLevel(id.scope().dimension());
                    if (level != null) removed = GeometryCollisionService.INSTANCE.remove(level, id);
                }
            } catch (IllegalArgumentException ignored) {
                // Invalid or foreign resource IDs must never delete another resource category.
            }
        }
        context.setNodeResult(StandardPorts.BOOL.getId(), removed);
        return next(StandardPorts.FLOW_OUT.getId());
    }

    @Override
    public Object compute(GraphDataContext context, String portName) {
        return StandardPorts.BOOL.getId().equals(portName) ? context.getNodeResult(portName) : null;
    }
}
