package com.mine.geometry_node.core.node.nodes.data.container;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;

import java.util.Map;

public class DictHasKey extends BaseNode {

    public static final String TYPE_ID = "dict_has_key";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.DATA, Component.translatable("geometry_node.node.dict_has_key"))
                .addRow(new PortRow(StandardPorts.DICT.toInput(), StandardPorts.BOOL.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.STRING.toInput(""), null, UIHint.INPUT, null, null))
                .build();
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        if (!StandardPorts.BOOL.getId().equals(portName)) return null;

        Map<String, Object> dict = getInputDict(context, StandardPorts.DICT.getId());
        String key = getInput(context, StandardPorts.STRING.getId(), String.class);

        if (dict != null && key != null && !key.trim().isEmpty()) {
            return dict.containsKey(key.trim());
        }

        return false;
    }
}