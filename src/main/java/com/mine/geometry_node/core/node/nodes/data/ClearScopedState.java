package com.mine.geometry_node.core.node.nodes.data;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateScope;
import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateTarget;
import com.mine.geometry_node.core.node.definition.node.NodeComment;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

public final class ClearScopedState extends BaseNode {
    public static final String TYPE_ID = "clear_scoped_state";

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
                        Component.translatable("geometry_node.node.clear_scoped_state"))
                .comment(NodeComment.builder(TYPE_ID)
                        .text("summary")
                        .input(StandardPorts.NAME, "name")
                        .build())
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(),
                        StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null));
        ScopedStateNodeSupport.addScopeInput(builder);
        if (ScopedStateNodeSupport.usesEntity(scope)) {
            builder.addPassthroughInput(StandardPorts.ENTITY.toInput(), UIHint.DEFAULT, null, null);
        } else if (scope == ScopedStateScope.WORLD) {
            ScopedStateNodeSupport.addDimensionInput(builder);
        }
        return builder.addPassthroughInput(StandardPorts.NAME.toInput(), UIHint.INPUT, null, null)
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        String key = ScopedStateNodeSupport.requireKey(
                getInput(context, StandardPorts.NAME.getId(), String.class));
        ScopedStateScope scope = ScopedStateNodeSupport.selectedScope(context);
        Entity entity = ScopedStateNodeSupport.usesEntity(scope)
                ? getInputFromList(context, StandardPorts.ENTITY.getId(), 0, Entity.class) : null;
        ScopedStateTarget target = ScopedStateNodeSupport.resolveTarget(context, scope, entity);

        if (target != null) {
            context.clearScopedState(target, key);
        }
        return next(StandardPorts.FLOW_OUT.getId());
    }
}
