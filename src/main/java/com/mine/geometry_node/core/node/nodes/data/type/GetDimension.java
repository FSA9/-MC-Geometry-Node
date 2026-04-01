package com.mine.geometry_node.core.node.nodes.data.type;

import com.mine.geometry_node.core.execution.ExecutionContext;
import com.mine.geometry_node.core.node.nodes.*;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;

import java.util.Map;

public class GetDimension extends BaseNode {

    public static final String TYPE_ID = "get_dimension";
    public static final String PROPERTY_SELECTED = "selected_dimension";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.DATA, Component.translatable("geometry_node.node.get_dimension"))
                .addRow(new PortRow(
                        null,
                        StandardPorts.DIMENSION.toOutput(),
                        UIHint.CUSTOM,
                        "dynamic_registry_select",
                        Map.of(
                                "property_key", PROPERTY_SELECTED,
                                "registry", "minecraft:dimension"
                        )
                ))
                .build();
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        if (StandardPorts.DIMENSION.getId().equals(portName)) {
            String selectedDimension = (String) context.getNodeProperty(PROPERTY_SELECTED);

            return selectedDimension != null ? selectedDimension : "minecraft:overworld";
        }
        return null;
    }
}