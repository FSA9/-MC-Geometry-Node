package com.mine.geometry_node.core.node.nodes.behavior.blackboard;

import com.mine.geometry_node.core.engine.behavior.document.BehaviorNodeTypes;
import com.mine.geometry_node.core.engine.graph.data.GraphDataContext;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortDef;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.PortType;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;

/** Tests whether a declared instance-blackboard input currently has a value. */
public final class BehaviorHasBlackboardNode extends BaseNode {
    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(BehaviorNodeTypes.HAS_BLACKBOARD, NodeType.DATA,
                        Component.translatable("geometry_node.node.behavior_has_blackboard"))
                .addRow(new PortRow(keyPort(), StandardPorts.BOOL.toOutput(), UIHint.INPUT, null, null))
                .build();
    }

    @Override
    public Object compute(GraphDataContext context, String portName) {
        if (!StandardPorts.BOOL.getId().equals(portName)) return null;
        String key = getInput(context, BehaviorNodeTypes.BLACKBOARD_KEY_PORT, String.class);
        return key != null && !key.isBlank() && context.hasVariable(key.trim());
    }

    private static PortDef keyPort() {
        return PortDef.create(BehaviorNodeTypes.BLACKBOARD_KEY_PORT,
                "geometry_node.port." + BehaviorNodeTypes.BLACKBOARD_KEY_PORT,
                PortType.STRING, "").hiddenPin();
    }
}
