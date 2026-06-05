package com.mine.geometry_node.core.node.nodes.data;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.service.PersistentAttributeTarget;
import com.mine.geometry_node.core.node.nodes.*;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public class GetScopeAttribute extends BaseNode {

    public static final String TYPE_ID = "get_scope_attribute";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.DATA, Component.translatable("geometry_node.node.get_scope_attribute"))
                .addRow(new PortRow(null, StandardPorts.ANY_VALUE.toOutput(), null, null, null))
                .addRow(new PortRow(StandardPorts.SCOPE.toInput(), null, UIHint.INPUT, null, null))
                .addRow(new PortRow(StandardPorts.NAME.toInput(), null, UIHint.INPUT, null, null))
                .build();
    }

    @Override
    @Nullable
    public Object compute(ExecutionContext context, String portName) {
        String scopeId = getInput(context, StandardPorts.SCOPE.getId(), String.class);
        String attrName = getInput(context, StandardPorts.NAME.getId(), String.class);

        if (scopeId != null && !scopeId.trim().isEmpty() && attrName != null && !attrName.trim().isEmpty()) {
            PersistentAttributeTarget target = "GLOBAL".equals(scopeId)
                    ? PersistentAttributeTarget.global()
                    : PersistentAttributeTarget.scope(scopeId);
            return context.getPersistentAttribute(target, attrName);
        }
        return null;
    }
}
