package com.mine.geometry_node.core.node.nodes.data;

import com.mine.geometry_node.core.engine.graph.data.GraphDataContext;
import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateScope;
import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateTarget;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.nodes.*;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
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
                .addRow(new PortRow(null, StandardPorts.ANY_VALUE.toOutput(), UIHint.DEFAULT, null, null));
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
    @Nullable
    public Object compute(GraphDataContext context, String portName) {
        String attrName = getInput(context, StandardPorts.NAME.getId(), String.class);
        ScopedStateScope scope = ScopedStateNodeSupport.selectedScope(context);
        Entity entity = ScopedStateNodeSupport.usesEntity(scope)
                ? getInputFromList(context, StandardPorts.ENTITY.getId(), 0, Entity.class) : null;
        ScopedStateTarget target = ScopedStateNodeSupport.resolveTarget(context, scope, entity);

        if (target == null) return null;
        return context.getScopedState(target, ScopedStateNodeSupport.requireKey(attrName));
    }
}
