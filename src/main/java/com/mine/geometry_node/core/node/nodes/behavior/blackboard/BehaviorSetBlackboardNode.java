package com.mine.geometry_node.core.node.nodes.behavior.blackboard;

import com.mine.geometry_node.core.node.definition.port.StandardPorts;

import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.nodes.behavior.BehaviorExecutableNode;
import com.mine.geometry_node.core.engine.behavior.runtime.BehaviorNodeExecutor;
import com.mine.geometry_node.core.engine.behavior.runtime.executor.BehaviorBlackboardExecutors;
import com.mine.geometry_node.core.node.definition.port.PortDef;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import net.minecraft.network.chat.Component;

/** Creates or overwrites one dynamic value in an explicit scope. */
public final class BehaviorSetBlackboardNode extends BaseNode implements BehaviorExecutableNode {
    public static final String TYPE_ID = "geometry_node:behavior_set_blackboard";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION,
                        Component.translatable("geometry_node.node.behavior_set_blackboard"))
                .comment(BlackboardNodePorts.comment(TYPE_ID))
                .addRow(new PortRow(parentPort(), null, UIHint.DEFAULT, null, null))
                .addRow(BlackboardNodePorts.scopeRow())
                .addRow(new PortRow(keyPort(), null, UIHint.INPUT, null, null))
                .addRow(new PortRow(StandardPorts.ANY_VALUE.toInput(), null, UIHint.DEFAULT, null, null))
                .build();
    }

    private static PortDef parentPort() {
        return StandardPorts.BEHAVIOR_PARENT.toInput();
    }

    private static PortDef keyPort() {
        return StandardPorts.KEY.toInput("").hiddenPin();
    }

    @Override
    public BehaviorNodeExecutor behaviorExecutor() {
        return BehaviorBlackboardExecutors.set();
    }
}
