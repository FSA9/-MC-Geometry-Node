package com.mine.geometry_node.core.node.nodes.behavior.decorator;

import com.mine.geometry_node.core.node.definition.port.StandardPorts;

import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.nodes.behavior.BehaviorExecutableNode;
import com.mine.geometry_node.core.engine.behavior.runtime.BehaviorNodeExecutor;
import com.mine.geometry_node.core.engine.behavior.runtime.executor.BehaviorDecoratorExecutors;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import net.minecraft.network.chat.Component;

/** Editor definitions shared by configurable behavior decorators. */
public final class BehaviorDecoratorNode extends BaseNode implements BehaviorExecutableNode {
    public enum Kind {
        REPEAT("geometry_node:behavior_repeat"),
        RETRY("geometry_node:behavior_retry"),
        TIMEOUT("geometry_node:behavior_timeout"),
        COOLDOWN("geometry_node:behavior_cooldown"),
        ALWAYS_SUCCEED("geometry_node:behavior_always_succeed"),
        ALWAYS_FAIL("geometry_node:behavior_always_fail");

        private final String typeId;

        Kind(String typeId) {
            this.typeId = typeId;
        }

        public String typeId() {
            return typeId;
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
            case REPEAT -> builder.addRow(input(StandardPorts.COUNT, 1)).build();
            case RETRY -> builder
                    .addRow(input(StandardPorts.COUNT, 1))
                    .addRow(tickInput(1, "geometry_node.port.tick.retry")).build();
            case TIMEOUT -> builder.addRow(tickInput(100, "geometry_node.port.tick.timeout")).build();
            case COOLDOWN -> builder.addRow(tickInput(20, "geometry_node.port.tick.cooldown")).build();
            case ALWAYS_SUCCEED, ALWAYS_FAIL -> builder.build();
        };
    }

    private static PortRow structureRow() {
        return new PortRow(StandardPorts.BEHAVIOR_PARENT.toInput(),
                StandardPorts.BEHAVIOR_CHILDREN.toOutput(), UIHint.DEFAULT, null, null);
    }

    private static PortRow input(StandardPorts port, Object value) {
        return new PortRow(port.toInput(value),
                null, UIHint.INPUT, null, null);
    }

    private static PortRow tickInput(int value, String translationKey) {
        return new PortRow(StandardPorts.TICK.toInput(value).withDisplayName(translationKey),
                null, UIHint.INPUT, null, null);
    }

    private static String path(String typeId) {
        return typeId.substring(typeId.indexOf(':') + 1);
    }

    @Override
    public BehaviorNodeExecutor behaviorExecutor() {
        return BehaviorDecoratorExecutors.forKind(kind);
    }
}
