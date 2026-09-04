package com.mine.geometry_node.core.node.nodes.behavior.condition;

import com.mine.geometry_node.core.engine.behavior.runtime.BehaviorNodeExecutor;
import com.mine.geometry_node.core.engine.behavior.runtime.executor.BehaviorConditionExecutors;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.behavior.BehaviorExecutableNode;
import com.mine.geometry_node.core.node.nodes.behavior.blackboard.BlackboardNodePorts;
import net.minecraft.network.chat.Component;

public final class BehaviorBlackboardValueChangedNode extends BaseNode
        implements BehaviorExecutableNode {
    public static final String TYPE_ID = "behavior_blackboard_value_changed";

    @Override
    public NodeDef getDefaultDefinition() {
        NodeDef.Builder builder = NodeDef.builder(TYPE_ID, NodeType.FLOW_CONTROL,
                        Component.translatable(
                                "geometry_node.node.behavior_blackboard_value_changed"))
                .comment(BlackboardNodePorts.comment(TYPE_ID))
                .addRow(new PortRow(StandardPorts.BEHAVIOR_PARENT.toInput(), null, UIHint.DEFAULT, null, null));
        BlackboardNodePorts.addScopeInput(builder);
        return builder.addPassthroughInput(StandardPorts.KEY.toInput(""), UIHint.INPUT)
                .build();
    }

    @Override
    public BehaviorNodeExecutor behaviorExecutor() {
        return BehaviorConditionExecutors.blackboardValueChanged();
    }
}
