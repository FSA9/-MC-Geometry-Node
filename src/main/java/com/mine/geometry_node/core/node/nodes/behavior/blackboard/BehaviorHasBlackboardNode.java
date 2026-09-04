package com.mine.geometry_node.core.node.nodes.behavior.blackboard;

import com.mine.geometry_node.core.engine.behavior.blackboard.BehaviorBlackboardView;
import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateScope;
import com.mine.geometry_node.core.engine.graph.data.GraphDataContext;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.PortDef;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import net.minecraft.network.chat.Component;

/** Tests whether a dynamic key currently exists in an explicit scope. */
public final class BehaviorHasBlackboardNode extends BaseNode {
    public static final String TYPE_ID = "geometry_node:behavior_has_blackboard";

    @Override
    public NodeDef getDefaultDefinition() {
        NodeDef.Builder builder = NodeDef.builder(TYPE_ID, NodeType.DATA,
                Component.translatable("geometry_node.node.behavior_has_blackboard"))
                .comment(BlackboardNodePorts.comment(TYPE_ID))
                .addRow(new PortRow(null, StandardPorts.BOOL.toOutput(), UIHint.DEFAULT, null, null));
        BlackboardNodePorts.addScopeInput(builder);
        return builder.addPassthroughInput(keyPort(), UIHint.INPUT)
                .build();
    }

    @Override
    public Object compute(GraphDataContext context, String portName) {
        if (!StandardPorts.BOOL.getId().equals(portName)) return null;
        if (!(context instanceof BehaviorBlackboardView blackboard)) return false;
        ScopedStateScope scope = BlackboardNodePorts.scope(
                context.getStaticInput(StandardPorts.BLACKBOARD_SCOPE.getId()));
        String key = getInput(context, StandardPorts.KEY.getId(), String.class);
        return scope != null && blackboard.hasBlackboard(scope, key != null ? key : "");
    }

    private static PortDef keyPort() {
        return StandardPorts.KEY.toInput("").hiddenPin();
    }
}
