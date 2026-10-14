package com.mine.geometry_node.core.node.nodes.actions.schematic;

import com.mine.geometry_node.core.engine.graph.data.GraphDataContext;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceId;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceReferences;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceTypeRegistry;
import com.mine.geometry_node.core.engine.system.schematic.SchematicProjectionService;
import com.mine.geometry_node.core.node.definition.node.NodeComment;
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

public class RemoveSchematicProjection extends BaseNode {
    public static final String TYPE_ID = "remove_schematic_projection";

    @Override
    public NodeDef getDefaultDefinition() {
        return definition(null);
    }

    @Override
    public NodeDef getDefinition(NodeData data) {
        return definition(data);
    }

    private NodeDef definition(NodeData data) {
        var builder = NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.remove_schematic_projection"))
                .comment(NodeComment.builder(TYPE_ID)
                        .text("summary")
                        .output(StandardPorts.FLOW_OUT, "flow_out")
                        .output(StandardPorts.BOOL, "bool")
                        .input(StandardPorts.FLOW_IN, "flow_in")
                        .input(StandardPorts.RESOURCE_ID, "resource_id")
                        .build())
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.BOOL.toOutput(), UIHint.DEFAULT, null, null))
                .addPassthroughInput(StandardPorts.RESOURCE_ID.toInput(""), UIHint.INPUT);
        return ResourceNodeSupport.addLegacyInput(builder, data, StandardPorts.KEY.getId()).build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        boolean success = false;
        if (context.getLevel() instanceof ServerLevel level) {
            GraphResourceId resourceId = resolveResourceId(context);
            if (resourceId != null && resourceId.type().equals(GraphResourceTypeRegistry.SCHEMATIC_PROJECTION)) {
                success = SchematicProjectionService.INSTANCE.remove(level.getServer(), resourceId);
            }
        }
        context.setNodeResult(StandardPorts.BOOL.getId(), success);
        return next(StandardPorts.FLOW_OUT.getId());
    }

    @Override
    public Object compute(GraphDataContext context, String portName) {
        if (StandardPorts.BOOL.getId().equals(portName)) {
            Object value = context.getNodeResult(portName);
            return value instanceof Boolean bool && bool;
        }
        return null;
    }

    private GraphResourceId resolveResourceId(ExecutionContext context) {
        String encoded = ResourceNodeSupport.read(context, StandardPorts.KEY.getId());
        try {
            return GraphResourceReferences.resolveExisting(context, GraphResourceTypeRegistry.SCHEMATIC_PROJECTION, encoded);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
