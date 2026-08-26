package com.mine.geometry_node.core.node.nodes.behavior;

import com.mine.geometry_node.core.engine.behavior.document.BehaviorNodeTypes;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortDef;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.PortType;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;

/** Ordered, memory-form fallback selector. */
public final class BehaviorSelectorNode extends BaseNode {
    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(BehaviorNodeTypes.SELECTOR, NodeType.FLOW_CONTROL,
                        Component.translatable("geometry_node.node.behavior_selector"))
                .addRow(new PortRow(structurePort(BehaviorNodeTypes.PARENT_PORT),
                        structurePort(BehaviorNodeTypes.CHILDREN_PORT), UIHint.DEFAULT, null, null))
                .build();
    }

    private static PortDef structurePort(String id) {
        return PortDef.create(id, "geometry_node.port." + id, PortType.BEHAVIOR_STRUCTURE);
    }
}
