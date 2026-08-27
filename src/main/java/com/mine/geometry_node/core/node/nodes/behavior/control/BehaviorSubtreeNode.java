package com.mine.geometry_node.core.node.nodes.behavior.control;

import com.mine.geometry_node.core.engine.behavior.document.BehaviorNodeTypes;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortDef;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.PortType;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;

/** Leaf call site for invoking another behavior-tree asset. */
public final class BehaviorSubtreeNode extends BaseNode {
    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(BehaviorNodeTypes.SUBTREE, NodeType.FLOW_CONTROL,
                        Component.translatable("geometry_node.node.behavior_subtree"))
                .addRow(new PortRow(PortDef.create(BehaviorNodeTypes.PARENT_PORT,
                                "geometry_node.port." + BehaviorNodeTypes.PARENT_PORT,
                                PortType.BEHAVIOR_STRUCTURE), null, UIHint.DEFAULT, null, null))
                .addRow(new PortRow(PortDef.create(BehaviorNodeTypes.SUBTREE_ASSET_PORT,
                                "geometry_node.port." + BehaviorNodeTypes.SUBTREE_ASSET_PORT,
                                PortType.STRING, "").hiddenPin(),
                        null, UIHint.INPUT, null, null))
                .build();
    }
}
