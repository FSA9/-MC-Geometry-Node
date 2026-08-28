package com.mine.geometry_node.core.node.nodes.data;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateScope;
import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateTarget;
import com.mine.geometry_node.core.node.NodeComment;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

public final class HasScopedState extends BaseNode {
    public static final String TYPE_ID = "has_scoped_state";

    @Override
    public NodeDef getDefaultDefinition() {
        return buildDefinition(ScopedStateNodeSupport.DEFAULT_SCOPE);
    }

    @Override
    public NodeDef getDefinition(NodeData instanceData) {
        return buildDefinition(ScopedStateNodeSupport.selectedScope(instanceData));
    }

    private NodeDef buildDefinition(ScopedStateScope scope) {
        NodeDef.Builder builder = NodeDef.builder(TYPE_ID, NodeType.DATA,
                        Component.translatable("geometry_node.node.has_scoped_state"))
                .comment(NodeComment.builder(TYPE_ID)
                        .text("summary")
                        .output(StandardPorts.BOOL, "bool")
                        .input(StandardPorts.NAME, "name")
                        .build())
                .addRow(ScopedStateNodeSupport.scopeRow(null));
        if (ScopedStateNodeSupport.usesEntity(scope)) {
            builder.addRow(new PortRow(
                    StandardPorts.ENTITY.toInput(), null, UIHint.DEFAULT, null, null));
        } else if (scope == ScopedStateScope.WORLD) {
            builder.addRow(ScopedStateNodeSupport.dimensionRow(null));
        }
        return builder
                .addRow(new PortRow(StandardPorts.NAME.toInput(),
                        StandardPorts.BOOL.toOutput(), UIHint.INPUT, null, null))
                .build();
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        if (!StandardPorts.BOOL.getId().equals(portName)) return null;

        String key = ScopedStateNodeSupport.requireKey(
                getInput(context, StandardPorts.NAME.getId(), String.class));
        ScopedStateScope scope = ScopedStateNodeSupport.selectedScope(context);
        Entity entity = ScopedStateNodeSupport.usesEntity(scope)
                ? getInput(context, StandardPorts.ENTITY.getId(), Entity.class) : null;
        ScopedStateTarget target = ScopedStateNodeSupport.requireTarget(context, scope, entity);
        return context.hasScopedState(target, key);
    }
}
