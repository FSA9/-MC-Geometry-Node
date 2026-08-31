package com.mine.geometry_node.core.node.nodes.behavior.control;

import com.mine.geometry_node.core.node.definition.port.StandardPorts;

import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.meta.SchemaKeys;
import com.mine.geometry_node.core.node.meta.StaticKeys;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.PortDef;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.PortType;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import net.minecraft.network.chat.Component;

import java.util.Map;

/** Shared editor definition for ordered behavior composites. */
final class BehaviorCompositeDefinition {
    private static final int DEFAULT_CHILD_COUNT = 2;
    private static final int MAX_CHILD_COUNT = 64;

    private BehaviorCompositeDefinition() {
    }

    static NodeDef create(String typeId, String titleKey, NodeData instanceData) {
        int count = resolveCount(instanceData);
        NodeDef.Builder builder = NodeDef.builder(typeId, NodeType.FLOW_CONTROL,
                        Component.translatable(titleKey))
                .addMeta(SchemaKeys.MIN_DYNAMIC_OUTPUT, 1)
                .addMeta(SchemaKeys.MAX_DYNAMIC_OUTPUT, MAX_CHILD_COUNT)
                .addRow(new PortRow(parentPort(), null, UIHint.DEFAULT, null, null));
        for (int i = 1; i <= count; i++) {
            builder.addRow(new PortRow(
                    null,
                    childPort(i),
                    UIHint.DEFAULT,
                    null,
                    Map.of(PortMetaKeys.IS_DYNAMIC, true, PortMetaKeys.DYNAMIC_INDEX, i)));
        }
        return builder.build();
    }

    private static int resolveCount(NodeData instanceData) {
        Object raw = instanceData != null
                ? instanceData.inputs.get(StaticKeys.DYNAMIC_BRANCH_OUTPUT_COUNT.id()) : null;
        int count = raw instanceof Number number ? number.intValue() : DEFAULT_CHILD_COUNT;
        if (raw instanceof String text) {
            try {
                count = Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                count = DEFAULT_CHILD_COUNT;
            }
        }
        return Math.max(1, Math.min(count, MAX_CHILD_COUNT));
    }

    private static PortDef parentPort() {
        return StandardPorts.BEHAVIOR_PARENT.toInput();
    }

    private static PortDef childPort(int index) {
        return new PortDef(StandardPorts.BEHAVIOR_CHILDREN.getIdWithIndex(index),
                Component.translatable("geometry_node.port.behavior_child_indexed", index),
                PortType.BEHAVIOR_STRUCTURE, null, false);
    }
}
