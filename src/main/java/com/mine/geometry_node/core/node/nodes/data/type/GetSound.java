package com.mine.geometry_node.core.node.nodes.data.type;

import com.mine.geometry_node.core.execution.ExecutionContext;
import com.mine.geometry_node.core.node.RegistryDataManager;
import com.mine.geometry_node.core.node.nodes.*;
import net.minecraft.network.chat.Component;

import java.util.Map;

public class GetSound extends BaseNode {

    public static final String TYPE_ID = "get_sound";
    public static final String PROPERTY_SELECTED = "selected_sound";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.DATA, Component.translatable("geometry_node.node.get_sound"))
                .addRow(new PortRow(
                        null,
                        StandardPorts.TYPE.toOutput(),
                        UIHint.SELECT,
                        null,
                        Map.of(
                                "property_key", PROPERTY_SELECTED,
                                "options", RegistryDataManager.getAllSounds()
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