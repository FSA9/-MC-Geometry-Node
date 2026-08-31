package com.mine.geometry_node.core.node.nodes.data.type;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import net.minecraft.network.chat.Component;

import java.util.Map;

public class GetPortType extends BaseNode {

    public static final String TYPE_ID = "get_port_type";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.DATA, Component.translatable("geometry_node.node.get_port_type"))
                .addRow(new PortRow(null, StandardPorts.STRING.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(
                        StandardPorts.STRING.toInput().hiddenPin(),
                        null,
                        UIHint.SELECT,
                        null,
                        Map.of(PortMetaKeys.DYNAMIC_REGISTRY_ID, "geometry_node:port_types")
                ))
                .build();
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        if (StandardPorts.STRING.getId().equals(portName)) {
            return getInput(context, StandardPorts.STRING.getId(), String.class);
        }
        return null;
    }
}