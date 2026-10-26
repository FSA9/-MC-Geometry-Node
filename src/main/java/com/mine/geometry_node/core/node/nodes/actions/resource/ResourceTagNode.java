package com.mine.geometry_node.core.node.nodes.actions.resource;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceReferences;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceTags;
import com.mine.geometry_node.core.utils.RateLimitedLog;
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

/** Mutates custom tags through the target owner's registered capability. */
public final class ResourceTagNode extends BaseNode {
    private final boolean add;

    public ResourceTagNode(boolean add) { this.add = add; }

    @Override
    public NodeDef getDefaultDefinition() {
        String id = add ? "add_resource_tag" : "remove_resource_tag";
        return NodeDef.builder(id, NodeType.ACTION, Component.translatable("geometry_node.node." + id))
                .addMeta(com.mine.geometry_node.core.node.meta.SchemaKeys.HIDDEN_FROM_MENU, true)
                .comment(NodeComment.builder(id).text("summary")
                        .input(StandardPorts.RESOURCE_ID, "geometry_node.resource.help.existing")
                        .input(StandardPorts.TAG, "geometry_node.resource.help.tag")
                        .output(StandardPorts.FLOW_OUT, "geometry_node.resource.help.tag_flow").build())
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addPassthroughInput(StandardPorts.RESOURCE_ID.toInput(""), UIHint.INPUT)
                .addPassthroughInput(StandardPorts.TAG.toInput(""), UIHint.INPUT)
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        if (context.getLevel() != null) {
            try {
                String encoded = getInput(context, StandardPorts.RESOURCE_ID.getId(), String.class);
                String tag = getInput(context, StandardPorts.TAG.getId(), String.class);
                if (tag == null) throw new IllegalArgumentException("Missing resource tag");
                var resource = GraphResourceReferences.parse(encoded);
                if (add) GraphResourceTags.INSTANCE.add(context.getLevel().getServer(), resource, tag);
                else GraphResourceTags.INSTANCE.remove(context.getLevel().getServer(), resource, tag);
            } catch (IllegalArgumentException | IllegalStateException | UnsupportedOperationException failure) {
                if (RateLimitedLog.acquire(context, "resource_tag:" + failure.getMessage()))
                    GeometryNode.LOGGER.warn("Cannot {} resource tag: {}", add ? "add" : "remove", failure.getMessage());
            }
        }
        return next(StandardPorts.FLOW_OUT.getId());
    }
}
