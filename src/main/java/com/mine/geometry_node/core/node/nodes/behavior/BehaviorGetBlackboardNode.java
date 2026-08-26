package com.mine.geometry_node.core.node.nodes.behavior;

import com.mine.geometry_node.core.engine.behavior.document.BehaviorNodeTypes;
import com.mine.geometry_node.core.engine.graph.data.GraphDataContext;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortDef;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.PortType;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;

/** Pure data view of one declared instance-blackboard key. */
public final class BehaviorGetBlackboardNode extends BaseNode {
    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(BehaviorNodeTypes.GET_BLACKBOARD, NodeType.DATA,
                        Component.translatable("geometry_node.node.behavior_get_blackboard"))
                .addRow(new PortRow(keyPort(), PortDef.create(BehaviorNodeTypes.BLACKBOARD_VALUE_PORT,
                        "geometry_node.port." + BehaviorNodeTypes.BLACKBOARD_VALUE_PORT,
                        PortType.ANY), UIHint.INPUT, null, null))
                .build();
    }

    @Override
    public Object compute(GraphDataContext context, String portName) {
        if (!BehaviorNodeTypes.BLACKBOARD_VALUE_PORT.equals(portName)) return null;
        String key = getInput(context, BehaviorNodeTypes.BLACKBOARD_KEY_PORT, String.class);
        return key != null && !key.isBlank() ? context.getVariable(key.trim()) : null;
    }

    private static PortDef keyPort() {
        return PortDef.create(BehaviorNodeTypes.BLACKBOARD_KEY_PORT,
                "geometry_node.port." + BehaviorNodeTypes.BLACKBOARD_KEY_PORT,
                PortType.STRING, "").hiddenPin();
    }
}
