package com.mine.geometry_node.core.node.nodes.actions.area;

import com.mine.geometry_node.core.engine.graph.data.GraphDataContext;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceReferences;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.engine.blueprint.spatial.area.AreaRef;
import com.mine.geometry_node.core.engine.blueprint.spatial.area.AreaResourceStore;
import com.mine.geometry_node.core.node.definition.node.NodeComment;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

public final class RemoveArea extends BaseNode {
    public static final String TYPE_ID = "remove_area";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node." + TYPE_ID))
                .comment(NodeComment.builder(TYPE_ID)
                        .text("summary")
                        .input(StandardPorts.FLOW_IN, "flow_in")
                        .output(StandardPorts.FLOW_OUT, "flow_out")
                        .output(StandardPorts.BOOL, "bool")
                        .input(StandardPorts.AREA, "area")
                        .input(StandardPorts.RESOURCE_ID, "geometry_node.resource.help.existing")
                        .build())
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(),
                        UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.BOOL.toOutput(), UIHint.DEFAULT, null, null))
                .addPassthroughInput(StandardPorts.AREA.toInput(), UIHint.DEFAULT)
                .addPassthroughInput(StandardPorts.RESOURCE_ID.toInput(""), UIHint.INPUT)
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        boolean success = false;
        ServerLevel hostLevel = context.getLevel();
        AreaRef reference = getInput(context, StandardPorts.AREA.getId(), AreaRef.class);
        String resourceId = getInput(context, StandardPorts.RESOURCE_ID.getId(), String.class);
        if (hostLevel != null) {
            if (resourceId != null && !resourceId.isBlank()) {
                try {
                    var address = GraphResourceReferences.resolveArea(context, null, resourceId,
                            reference == null ? hostLevel.dimension() : reference.address().dimension(), false);
                    success = AreaResourceStore.INSTANCE.remove(hostLevel.getServer(), address);
                } catch (IllegalArgumentException ignored) { }
            } else if (reference != null) {
                success = AreaResourceStore.INSTANCE.remove(hostLevel.getServer(), reference);
            }
        }
        context.setNodeResult(StandardPorts.BOOL.getId(), success);
        return next(StandardPorts.FLOW_OUT.getId());
    }

    @Override
    public Object compute(GraphDataContext context, String portName) {
        return StandardPorts.BOOL.getId().equals(portName) ? context.getNodeResult(portName) : null;
    }

}
