package com.mine.geometry_node.core.node.nodes.behavior.decorator;

import com.mine.geometry_node.core.engine.behavior.document.BehaviorNodeTypes;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortDef;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.PortType;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;

/** Editor definitions shared by configurable behavior decorators. */
public final class BehaviorDecoratorNode extends BaseNode {
    public enum Kind {
        REPEAT(BehaviorNodeTypes.REPEAT),
        RETRY(BehaviorNodeTypes.RETRY),
        TIMEOUT(BehaviorNodeTypes.TIMEOUT),
        COOLDOWN(BehaviorNodeTypes.COOLDOWN),
        ALWAYS_SUCCEED(BehaviorNodeTypes.ALWAYS_SUCCEED),
        ALWAYS_FAIL(BehaviorNodeTypes.ALWAYS_FAIL);

        private final String typeId;

        Kind(String typeId) {
            this.typeId = typeId;
        }
    }

    private final Kind kind;

    public BehaviorDecoratorNode(Kind kind) {
        this.kind = kind;
    }

    @Override
    public NodeDef getDefaultDefinition() {
        NodeDef.Builder builder = NodeDef.builder(kind.typeId, NodeType.FLOW_CONTROL,
                        Component.translatable("geometry_node.node." + path(kind.typeId)))
                .addRow(structureRow());
        return switch (kind) {
            case REPEAT -> builder.addRow(input(BehaviorNodeTypes.COUNT_PORT, PortType.INTEGER, 1)).build();
            case RETRY -> builder
                    .addRow(input(BehaviorNodeTypes.COUNT_PORT, PortType.INTEGER, 1))
                    .addRow(input(BehaviorNodeTypes.RETRY_INTERVAL_PORT, PortType.INTEGER, 1)).build();
            case TIMEOUT -> builder.addRow(input(BehaviorNodeTypes.TICKS_PORT, PortType.INTEGER, 100)).build();
            case COOLDOWN -> builder.addRow(input(
                    BehaviorNodeTypes.COOLDOWN_TICKS_PORT, PortType.INTEGER, 20)).build();
            case ALWAYS_SUCCEED, ALWAYS_FAIL -> builder.build();
        };
    }

    private static PortRow structureRow() {
        return new PortRow(structurePort(BehaviorNodeTypes.PARENT_PORT),
                structurePort(BehaviorNodeTypes.CHILDREN_PORT), UIHint.DEFAULT, null, null);
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
