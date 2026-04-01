package com.mine.geometry_node.core.node.nodes.data.type;

import com.mine.geometry_node.core.execution.ExecutionContext;
import com.mine.geometry_node.core.node.nodes.*;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;

import java.util.Map;

public class GetDamageType extends BaseNode {

    public static final String TYPE_ID = "get_damage_type";

    public static final String PROPERTY_SELECTED = "selected_damage_type";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.DATA, Component.translatable("geometry_node.node.get_damage_type"))
                .addRow(new PortRow(
                        null,
                        StandardPorts.DAMAGE_TYPE.toOutput(),
                        UIHint.CUSTOM,
                        "dynamic_registry_select",
                        Map.of(
                                "property_key", PROPERTY_SELECTED,
                                "registry", "minecraft:damage_type"
                        )
                ))
                .build();
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        if (StandardPorts.DAMAGE_TYPE.getId().equals(portName)) {

            String selectedType = (String) context.getNodeProperty(PROPERTY_SELECTED);

            return selectedType;
        }
        return null;
    }
}