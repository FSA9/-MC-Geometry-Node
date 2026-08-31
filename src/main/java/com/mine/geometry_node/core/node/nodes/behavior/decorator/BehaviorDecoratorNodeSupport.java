package com.mine.geometry_node.core.node.nodes.behavior.decorator;

import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import net.minecraft.network.chat.Component;

final class BehaviorDecoratorNodeSupport {
    private BehaviorDecoratorNodeSupport() {
    }

    static NodeDef.Builder builder(String typeId, String titleKey) {
        return NodeDef.builder(typeId, NodeType.FLOW_CONTROL, Component.translatable(titleKey))
                .addRow(structureRow());
    }

    static PortRow input(StandardPorts port, Object value) {
        return new PortRow(port.toInput(value), null, UIHint.INPUT, null, null);
    }

    static PortRow tickInput(int value, String translationKey) {
        return new PortRow(StandardPorts.TICK.toInput(value).withDisplayName(translationKey),
                null, UIHint.INPUT, null, null);
    }

    private static PortRow structureRow() {
        return new PortRow(StandardPorts.BEHAVIOR_PARENT.toInput(),
                StandardPorts.BEHAVIOR_CHILDREN.toOutput(), UIHint.DEFAULT, null, null);
    }
}
