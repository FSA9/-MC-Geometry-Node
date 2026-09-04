package com.mine.geometry_node.core.node.nodes.behavior.blackboard;

import com.mine.geometry_node.core.node.definition.port.StandardPorts;

import com.mine.geometry_node.core.engine.behavior.blackboard.BehaviorBlackboardView;
import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateScope;
import com.mine.geometry_node.core.engine.graph.data.GraphDataContext;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.PortDef;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import net.minecraft.network.chat.Component;

/** Pure data view of one dynamically stored value in an explicit scope. */
public final class BehaviorGetBlackboardNode extends BaseNode {
    public static final String TYPE_ID = "geometry_node:behavior_get_blackboard";

    @Override
    public NodeDef getDefaultDefinition() {
        NodeDef.Builder builder = NodeDef.builder(TYPE_ID, NodeType.DATA,
                Component.translatable("geometry_node.node.behavior_get_blackboard"))
                .comment(BlackboardNodePorts.comment(TYPE_ID))
                .addRow(new PortRow(null, StandardPorts.ANY_VALUE.toOutput(), UIHint.DEFAULT, null, null));
        BlackboardNodePorts.addScopeInput(builder);
        return builder.addPassthroughInput(keyPort(), UIHint.INPUT)
                .build();
    }

    @Override
    public Object compute(GraphDataContext context, String portName) {
        if (!StandardPorts.ANY_VALUE.getId().equals(portName)) return null;
        if (!(context instanceof BehaviorBlackboardView blackboard)) return null;
        ScopedStateScope scope = scope(context);
        String key = getInput(context, StandardPorts.KEY.getId(), String.class);
        return scope != null ? blackboard.getBlackboard(scope, key != null ? key : "") : null;
    }

    private static PortDef keyPort() {
        return StandardPorts.KEY.toInput("").hiddenPin();
    }

    private static ScopedStateScope scope(GraphDataContext context) {
        return BlackboardNodePorts.scope(context.getStaticInput(StandardPorts.BLACKBOARD_SCOPE.getId()));
    }
}
