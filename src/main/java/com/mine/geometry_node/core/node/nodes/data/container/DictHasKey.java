package com.mine.geometry_node.core.node.nodes.data.container;

import com.mine.geometry_node.core.engine.graph.data.GraphDataContext;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.TypeConverter;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import net.minecraft.network.chat.Component;

import java.util.Map;

public class DictHasKey extends BaseNode {

    public static final String TYPE_ID = "dict_has_key";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.DATA, Component.translatable("geometry_node.node.dict_has_key"))
                .addRow(new PortRow(null, StandardPorts.BOOL.toOutput(), UIHint.DEFAULT, null, null))
                .addPassthroughInput(StandardPorts.DICT.toInput(), UIHint.DEFAULT, null, null)
                .addPassthroughInput(StandardPorts.STRING.toInput(""), UIHint.INPUT, null, null)
                .build();
    }

    @Override
    public Object compute(GraphDataContext context, String portName) {
        if (!StandardPorts.BOOL.getId().equals(portName)) return null;

        Map<String, Object> dict = TypeConverter.convertStringMap(
                getInput(context, StandardPorts.DICT.getId(), Object.class), context);
        String key = getInput(context, StandardPorts.STRING.getId(), String.class);

        if (dict != null && key != null && !key.trim().isEmpty()) {
            return dict.containsKey(key.trim());
        }

        return false;
    }
}
