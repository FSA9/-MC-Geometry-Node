package com.mine.geometry_node.core.node.nodes.metadata;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.engine.graph.data.GraphDataContext;
import com.mine.geometry_node.core.metadata.MetadataCapabilities;
import com.mine.geometry_node.core.node.definition.node.*;
import com.mine.geometry_node.core.node.definition.port.*;
import com.mine.geometry_node.core.node.document.NodeData;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Custom owner labels; independent of vanilla entity tags and registry tags. */
public final class CustomTagNode extends CustomMetadataNodeSupport {
    public enum Operation { GET, ADD, HAS, REMOVE }
    private final Operation operation;
    public CustomTagNode(Operation operation) { super(true); this.operation = Objects.requireNonNull(operation); }
    private String id() { return operation.name().toLowerCase(Locale.ROOT) + "_custom_tag"; }
    @Override public NodeDef getDefaultDefinition() { return definition(TargetType.ENTITY); }
    @Override public NodeDef getDefinition(NodeData data) { return definition(targetType(data)); }

    private NodeDef definition(TargetType target) {
        String id = id();
        boolean action = operation == Operation.ADD || operation == Operation.REMOVE;
        NodeDef.Builder builder = NodeDef.builder(id, action ? NodeType.ACTION : NodeType.DATA,
                Component.translatable("geometry_node.node." + id))
                .comment(NodeComment.builder(id).text("summary").build());
        if (action) builder.addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null));
        if (operation == Operation.GET)
            builder.addRow(new PortRow(null, PortDef.create(TAGS, "geometry_node.port.tags", PortType.LIST), UIHint.DEFAULT, null, null));
        else if (operation == Operation.HAS)
            builder.addRow(new PortRow(null, StandardPorts.BOOL.toOutput(), UIHint.DEFAULT, null, null));
        addTargetInputs(builder, target);
        if (operation != Operation.GET) builder.addPassthroughInput(StandardPorts.TAG.toInput(""), UIHint.INPUT);
        return builder.build();
    }

    @Override public Object compute(GraphDataContext context, String portName) {
        if (operation == Operation.GET && TAGS.equals(portName)) {
            MetadataCapabilities.Access access = access(context);
            return access == null ? List.of() : access.requireTags().values();
        }
        if (operation == Operation.HAS && StandardPorts.BOOL.getId().equals(portName)) {
            String tag = getInput(context, StandardPorts.TAG.getId(), String.class);
            MetadataCapabilities.Access access = access(context);
            return tag != null && access != null && access.requireTags().contains(tag);
        }
        return null;
    }

    @Override public ExecutionResult execute(ExecutionContext context) {
        if (operation == Operation.ADD || operation == Operation.REMOVE) {
            String tag = getInput(context, StandardPorts.TAG.getId(), String.class);
            if (tag == null || tag.isBlank()) return next(StandardPorts.FLOW_OUT.getId());
            MetadataCapabilities.Access access = access(context);
            if (access != null) {
                boolean changed = operation == Operation.ADD ? access.requireTags().add(tag) : access.requireTags().remove(tag);
                if (changed) context.clearFrameCache();
            }
        }
        return next(StandardPorts.FLOW_OUT.getId());
    }
}
