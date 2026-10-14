package com.mine.geometry_node.core.node.nodes.actions.resource;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.node.definition.node.NodeComment;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import net.minecraft.network.chat.Component;

/** Reserved editor actions; no tag mutation is performed until the tag service is implemented. */
public final class ResourceTagNode extends BaseNode {
    private final boolean add;

    public ResourceTagNode(boolean add) { this.add = add; }

    @Override
    public NodeDef getDefaultDefinition() {
        String id = add ? "add_resource_tag" : "remove_resource_tag";
        return NodeDef.builder(id, NodeType.ACTION, Component.translatable("geometry_node.node." + id))
                .comment(NodeComment.builder(id).text("summary")
                        .input(StandardPorts.RESOURCE_ID, "geometry_node.resource.help.existing")
                        .input(StandardPorts.TAG, "geometry_node.resource.help.tag")
                        .output(StandardPorts.FLOW_OUT, "geometry_node.resource.help.placeholder_flow").build())
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addPassthroughInput(StandardPorts.RESOURCE_ID.toInput(""), UIHint.INPUT)
                .addPassthroughInput(StandardPorts.TAG.toInput(""), UIHint.INPUT)
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        return next(StandardPorts.FLOW_OUT.getId());
    }
}
