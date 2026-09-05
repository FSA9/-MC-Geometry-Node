package com.mine.geometry_node.core.node.nodes.data.container;

import com.mine.geometry_node.core.engine.graph.data.GraphDataContext;
import com.mine.geometry_node.core.engine.graph.value.GraphValueSnapshot;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.TypeConverter;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import net.minecraft.network.chat.Component;

import java.util.Map;

public class DictHasValue extends BaseNode {

    public static final String TYPE_ID = "dict_has_value";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.DATA, Component.translatable("geometry_node.node.dict_has_value"))
                .addRow(new PortRow(null, StandardPorts.BOOL.toOutput(), UIHint.DEFAULT, null, null))
                .addPassthroughInput(StandardPorts.DICT.toInput(), UIHint.DEFAULT, null, null)
                .addPassthroughInput(StandardPorts.ANY_VALUE.toInput(), UIHint.DEFAULT, null, null)
                .build();
    }

    @Override
    public Object compute(GraphDataContext context, String portName) {
        if (!StandardPorts.BOOL.getId().equals(portName)) return null;

        Map<String, Object> dict = TypeConverter.convertStringMap(
                getInput(context, StandardPorts.DICT.getId(), Object.class), context);
        Object targetValue = getInput(context, StandardPorts.ANY_VALUE.getId(), Object.class);

        if (dict != null && targetValue != null && !dict.isEmpty()) {
            return dict.values().stream()
                    .anyMatch(value -> GraphValueSnapshot.equivalent(value, targetValue));
        }

        return false;
    }
}
