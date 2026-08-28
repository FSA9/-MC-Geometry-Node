package com.mine.geometry_node.core.node.nodes.behavior.condition;

import com.mine.geometry_node.core.engine.behavior.document.BehaviorNodeTypes;
import com.mine.geometry_node.core.node.nodes.behavior.blackboard.BlackboardNodePorts;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortDef;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.PortType;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;

/** Editor definitions shared by the additional behavior conditions. */
public final class BehaviorUtilityConditionNode extends BaseNode {
    public enum Kind {
        BLACKBOARD_VALUE_CHANGED(BehaviorNodeTypes.BLACKBOARD_VALUE_CHANGED),
        CAN_NAVIGATE_TO(BehaviorNodeTypes.CAN_NAVIGATE_TO);

        private final String typeId;

        Kind(String typeId) {
            this.typeId = typeId;
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
                .addRow(new PortRow(structurePort(BehaviorNodeTypes.PARENT_PORT),
                        null, UIHint.DEFAULT, null, null));
        return switch (kind) {
            case BLACKBOARD_VALUE_CHANGED -> builder
                    .comment(BlackboardNodePorts.comment(BehaviorNodeTypes.BLACKBOARD_VALUE_CHANGED))
                    .addRow(BlackboardNodePorts.scopeRow())
                    .addRow(input(BehaviorNodeTypes.BLACKBOARD_KEY_PORT, PortType.STRING, ""))
                    .build();
            case CAN_NAVIGATE_TO -> builder.addRow(input(
                    BehaviorNodeTypes.TARGET_PORT, PortType.ENTITY, null)).build();
        };
    }

    private static PortRow input(String id, PortType type, Object value) {
        return new PortRow(PortDef.create(id, "geometry_node.port." + id, type, value),
                null, UIHint.INPUT, null, null);
    }

    private static PortDef structurePort(String id) {
        return PortDef.create(id, "geometry_node.port." + id, PortType.BEHAVIOR_STRUCTURE);
    }

    private static String path(String typeId) {
        return typeId.substring(typeId.indexOf(':') + 1);
    }
}
