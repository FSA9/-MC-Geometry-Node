package com.mine.geometry_node.core.node.nodes.data;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateScope;
import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateTarget;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.nodes.*;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

public final class GetScopedState extends BaseNode {

    public static final String TYPE_ID = "get_scoped_state";

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
                        Component.translatable("geometry_node.node.get_scoped_state"))
                .addRow(ScopedStateNodeSupport.scopeRow(null));
        if (ScopedStateNodeSupport.usesEntity(scope)) {
            builder.addRow(new PortRow(StandardPorts.ENTITY.toInput(), null, UIHint.DEFAULT, null, null));
        } else if (scope == ScopedStateScope.WORLD) {
            builder.addRow(ScopedStateNodeSupport.dimensionRow(null));
        }
        return builder
                .addRow(new PortRow(StandardPorts.NAME.toInput(), StandardPorts.ANY_VALUE.toOutput(), UIHint.INPUT, null, null))
                .build();
    }

    @Override
    @Nullable
    public Object compute(ExecutionContext context, String portName) {
        String attrName = getInput(context, StandardPorts.NAME.getId(), String.class);
        ScopedStateScope scope = ScopedStateNodeSupport.selectedScope(context);
        Entity entity = ScopedStateNodeSupport.usesEntity(scope)
                ? getInput(context, StandardPorts.ENTITY.getId(), Entity.class) : null;
        ScopedStateTarget target = ScopedStateNodeSupport.resolveTarget(context, scope, entity);

        if (attrName == null || attrName.trim().isEmpty()) {
            throw new IllegalStateException("Scoped state key cannot be empty");
        }
        if (target == null) {
            throw new IllegalStateException("Scoped state target is unavailable for " + scope);
        }
        return context.getScopedState(target, attrName.trim());
    }
}
