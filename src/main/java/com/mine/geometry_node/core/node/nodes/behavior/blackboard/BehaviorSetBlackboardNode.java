package com.mine.geometry_node.core.node.nodes.behavior.blackboard;

import com.mine.geometry_node.core.engine.behavior.document.BehaviorNodeTypes;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortDef;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.PortType;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;

/** Creates or overwrites one dynamic value in an explicit scope. */
public final class BehaviorSetBlackboardNode extends BaseNode {
    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(BehaviorNodeTypes.SET_BLACKBOARD, NodeType.ACTION,
                        Component.translatable("geometry_node.node.behavior_set_blackboard"))
                .comment(BlackboardNodePorts.comment(BehaviorNodeTypes.SET_BLACKBOARD))
                .addRow(new PortRow(parentPort(), null, UIHint.DEFAULT, null, null))
                .addRow(BlackboardNodePorts.scopeRow())
                .addRow(new PortRow(keyPort(), null, UIHint.INPUT, null, null))
                .addRow(new PortRow(PortDef.create(BehaviorNodeTypes.BLACKBOARD_VALUE_PORT,
                        "geometry_node.port." + BehaviorNodeTypes.BLACKBOARD_VALUE_PORT,
                        PortType.ANY), null, UIHint.DEFAULT, null, null))
                .build();
    }

    private static PortDef parentPort() {
        return PortDef.create(BehaviorNodeTypes.PARENT_PORT,
                "geometry_node.port." + BehaviorNodeTypes.PARENT_PORT, PortType.BEHAVIOR_STRUCTURE);
    }

    private static PortDef keyPort() {
        return PortDef.create(BehaviorNodeTypes.BLACKBOARD_KEY_PORT,
                "geometry_node.port." + BehaviorNodeTypes.BLACKBOARD_KEY_PORT,
                PortType.STRING, "").hiddenPin();
    }
}
