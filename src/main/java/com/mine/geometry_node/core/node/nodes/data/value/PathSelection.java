package com.mine.geometry_node.core.node.nodes.data.value;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import net.minecraft.network.chat.Component;

public class PathSelection extends BaseNode {
    public static final String TYPE_ID = "path_selection";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.DATA, Component.translatable("geometry_node.node.path_selection"))
                .addPassthroughInput(StandardPorts.PATH.toInput(""), UIHint.PATH, null, null)
                .build();
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        if (StandardPorts.PATH.getId().equals(portName)) {
            return getInput(context, StandardPorts.PATH.getId(), String.class);
        }
        return null;
    }
}
