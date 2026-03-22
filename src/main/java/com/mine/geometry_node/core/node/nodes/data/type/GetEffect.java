package com.mine.geometry_node.core.node.nodes.data.type;

import com.mine.geometry_node.core.execution.ExecutionContext;
import com.mine.geometry_node.core.node.RegistryDataManager;
import com.mine.geometry_node.core.node.nodes.*;
import net.minecraft.network.chat.Component;

import java.util.Map;

public class GetEffect extends BaseNode {

    public static final String TYPE_ID = "get_effect";
    public static final String PROPERTY_SELECTED = "selected_effect";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.DATA, Component.translatable("geometry_node.node.get_effect"))
                .addRow(new PortRow(
                        null,
                        StandardPorts.TYPE.toOutput(),
                        UIHint.SELECT,
                        null,
                        Map.of(
                                "property_key", PROPERTY_SELECTED,
                                "options", RegistryDataManager.getAllEffects()
                        )
                ))
                .build();
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        if (StandardPorts.TYPE.getId().equals(portName)) {
            return context.getNodeProperty(PROPERTY_SELECTED);
        }
        return null;
    }
}