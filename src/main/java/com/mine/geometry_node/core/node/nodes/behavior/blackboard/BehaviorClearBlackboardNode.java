package com.mine.geometry_node.core.node.nodes.behavior.blackboard;

import com.mine.geometry_node.core.node.port.StandardPorts;

import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.nodes.behavior.BehaviorExecutableNode;
import com.mine.geometry_node.core.engine.behavior.runtime.BehaviorNodeExecutor;
import com.mine.geometry_node.core.engine.behavior.runtime.executor.BehaviorBlackboardExecutors;
import com.mine.geometry_node.core.node.port.PortDef;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;

/** Idempotently removes one dynamic key and its value from an explicit scope. */
public final class BehaviorClearBlackboardNode extends BaseNode implements BehaviorExecutableNode {
    public static final String TYPE_ID = "geometry_node:behavior_clear_blackboard";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION,
                        Component.translatable("geometry_node.node.behavior_clear_blackboard"))
                .comment(BlackboardNodePorts.comment(TYPE_ID))
                .addRow(new PortRow(parentPort(), null, UIHint.DEFAULT, null, null))
                .addRow(BlackboardNodePorts.scopeRow())
                .addRow(new PortRow(keyPort(), null, UIHint.INPUT, null, null))
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
        return BehaviorBlackboardExecutors.clear();
    }
}
