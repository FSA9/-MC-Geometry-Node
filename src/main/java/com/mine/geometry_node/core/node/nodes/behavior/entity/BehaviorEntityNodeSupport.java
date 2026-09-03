package com.mine.geometry_node.core.node.nodes.behavior.entity;

import com.mine.geometry_node.core.node.definition.port.PortDef;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.PortType;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;

final class BehaviorEntityNodeSupport {
    private BehaviorEntityNodeSupport() {
    }

    static PortRow parentRow() {
        return new PortRow(parentPort(), null, UIHint.DEFAULT, null, null);
    }

    static PortRow input(StandardPorts port, Object value) {
        UIHint hint = port.getType() == PortType.ENTITY || port.getType() == PortType.LIST
                ? UIHint.DEFAULT : UIHint.INPUT;
        return PortRow.passthrough(port.toInput(value), hint, null, null);
    }

    static PortRow tickInput(int value, String translationKey) {
        return PortRow.passthrough(StandardPorts.TICK.toInput(value).withDisplayName(translationKey),
                UIHint.INPUT, null, null);
    }

    private static PortDef parentPort() {
        return StandardPorts.BEHAVIOR_PARENT.toInput();
    }
}
