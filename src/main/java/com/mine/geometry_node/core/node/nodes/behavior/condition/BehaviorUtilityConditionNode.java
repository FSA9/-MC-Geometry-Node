package com.mine.geometry_node.core.node.nodes.behavior.condition;

import com.mine.geometry_node.core.node.port.StandardPorts;

import com.mine.geometry_node.core.node.nodes.behavior.blackboard.BlackboardNodePorts;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;

/** Editor definitions shared by the additional behavior conditions. */
public final class BehaviorUtilityConditionNode extends BaseNode {
    public enum Kind {
        BLACKBOARD_VALUE_CHANGED("geometry_node:behavior_blackboard_value_changed"),
        CAN_NAVIGATE_TO("geometry_node:behavior_can_navigate_to");

        private final String typeId;

        Kind(String typeId) {
            this.typeId = typeId;
        }

        public String typeId() {
            return typeId;
        }
    }

    private final Kind kind;

    public BehaviorUtilityConditionNode(Kind kind) {
        this.kind = kind;
    }

    @Override
    public NodeDef getDefaultDefinition() {
        NodeDef.Builder builder = NodeDef.builder(kind.typeId, NodeType.FLOW_CONTROL,
                        Component.translatable("geometry_node.node." + path(kind.typeId)))
                .addRow(new PortRow(StandardPorts.BEHAVIOR_PARENT.toInput(),
                        null, UIHint.DEFAULT, null, null));
        return switch (kind) {
            case BLACKBOARD_VALUE_CHANGED -> builder
                    .comment(BlackboardNodePorts.comment(Kind.BLACKBOARD_VALUE_CHANGED.typeId))
                    .addRow(BlackboardNodePorts.scopeRow())
                    .addRow(input(StandardPorts.KEY, ""))
                    .build();
            case CAN_NAVIGATE_TO -> builder.addRow(input(StandardPorts.TARGET, null)).build();
        };
    }

    private static PortRow input(StandardPorts port, Object value) {
        return new PortRow(port.toInput(value),
                null, UIHint.INPUT, null, null);
    }

    private static String path(String typeId) {
        return typeId.substring(typeId.indexOf(':') + 1);
    }
}
