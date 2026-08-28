package com.mine.geometry_node.core.node.nodes.behavior.control;

import com.mine.geometry_node.core.node.port.StandardPorts;

import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.nodes.behavior.BehaviorExecutableNode;
import com.mine.geometry_node.core.engine.behavior.runtime.BehaviorNodeExecutor;
import com.mine.geometry_node.core.engine.behavior.runtime.executor.BehaviorControlExecutors;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;

/** Leaf call site for invoking another behavior-tree asset. */
public final class BehaviorSubtreeNode extends BaseNode implements BehaviorExecutableNode {
    public static final String TYPE_ID = "geometry_node:behavior_subtree";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.FLOW_CONTROL,
                        Component.translatable("geometry_node.node.behavior_subtree"))
                .addRow(new PortRow(StandardPorts.BEHAVIOR_PARENT.toInput(), null, UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.SUBTREE_ASSET.toInput("").hiddenPin(),
                        null, UIHint.INPUT, null, null))
                .build();
    }

    @Override
    public BehaviorNodeExecutor behaviorExecutor() {
        return BehaviorControlExecutors.subtree();
    }
}
