package com.mine.geometry_node.core.node.nodes.metadata;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.engine.graph.data.GraphDataContext;
import com.mine.geometry_node.core.metadata.MetadataCapabilities;
import com.mine.geometry_node.core.node.definition.node.*;
import com.mine.geometry_node.core.node.definition.port.*;
import com.mine.geometry_node.core.node.document.NodeData;
import net.minecraft.network.chat.Component;
import java.util.Locale;

/** Public custom dictionaries on entities, dimensions, the server, resources and factions. */
public final class CustomDataNode extends CustomMetadataNodeSupport {
    public enum Operation { GET, SET, HAS, REMOVE }
    private final Operation operation;
    public CustomDataNode(Operation operation) { super(false); this.operation = java.util.Objects.requireNonNull(operation); }
    private String id() { return operation.name().toLowerCase(Locale.ROOT) + "_custom_data"; }
    @Override public NodeDef getDefaultDefinition() { return definition(TargetType.ENTITY); }
    @Override public NodeDef getDefinition(NodeData data) { return definition(targetType(data)); }

    private NodeDef definition(TargetType target) {
        String id = id();
        boolean action = operation == Operation.SET || operation == Operation.REMOVE;
        NodeDef.Builder builder = NodeDef.builder(id, action ? NodeType.ACTION : NodeType.DATA,
                Component.translatable("geometry_node.node." + id))
                .comment(NodeComment.builder(id).text("summary").build());
        if (action) builder.addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null));
        if (operation == Operation.GET)
            builder.addRow(new PortRow(null, StandardPorts.ANY_VALUE.toOutput(), UIHint.DEFAULT, null, null));
        else if (operation == Operation.SET)
            builder.addRow(new PortRow(null, StandardPorts.STATE_VALUE.toOutput(), UIHint.DEFAULT, null, null));
        else if (operation == Operation.HAS)
            builder.addRow(new PortRow(null, StandardPorts.BOOL.toOutput(), UIHint.DEFAULT, null, null));
        addTargetInputs(builder, target);
        builder.addPassthroughInput(StandardPorts.NAME.toInput(), UIHint.INPUT);
        if (operation == Operation.SET) builder.addPassthroughInput(StandardPorts.ANY_VALUE.toInput(), UIHint.INPUT);
        return builder.build();
    }

    @Override public Object compute(GraphDataContext context, String portName) {
        if (operation == Operation.HAS && StandardPorts.BOOL.getId().equals(portName)) {
            MetadataCapabilities.Access access = access(context);
            return access != null && access.requireData().contains(key(context));
        }
        if (operation == Operation.GET && StandardPorts.ANY_VALUE.getId().equals(portName)
                || operation == Operation.SET && StandardPorts.STATE_VALUE.getId().equals(portName)) {
            MetadataCapabilities.Access access = access(context);
            return access == null ? null : access.requireData().get(key(context));
        }
        return null;
    }

    @Override public ExecutionResult execute(ExecutionContext context) {
        if (operation == Operation.SET || operation == Operation.REMOVE) {
            Object value = operation == Operation.SET ? getInput(context, StandardPorts.ANY_VALUE.getId(), Object.class) : null;
            if (operation == Operation.SET && value == null) return next(StandardPorts.FLOW_OUT.getId());
            MetadataCapabilities.Access access = access(context);
            if (access != null) {
                boolean changed = operation == Operation.SET ? access.requireData().put(key(context), value)
                        : access.requireData().remove(key(context));
                if (changed) context.clearFrameCache();
            }
        }
        return next(StandardPorts.FLOW_OUT.getId());
    }
}
