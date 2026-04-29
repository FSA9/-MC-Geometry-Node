package com.mine.geometry_node.core.node.nodes.data.type;

import com.mine.geometry_node.core.execution.ExecutionContext;
import com.mine.geometry_node.core.node.RegistryDataManager;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.nodes.*;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;

import java.util.Map;

public class GetSound extends BaseNode {

    public static final String TYPE_ID = "get_sound";
    public static final String PROPERTY_SELECTED = PortMetaKeys.DYNAMIC_REGISTRY_ID.id();

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.DATA, Component.translatable("geometry_node.node.get_sound"))
                .addRow(new PortRow(null, StandardPorts.SOUND_TYPE.toOutput(), null, null, null))
                .addRow(new PortRow(
                        null,
                        null,
                        UIHint.SELECT,
                        null,
                        Map.of(
                                PortMetaKeys.BIND_PROPERTY, PROPERTY_SELECTED,
                                PortMetaKeys.OPTIONS, RegistryDataManager.getAllSounds().toArray(new String[0])
                        )
                ))
                .build();
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        if (StandardPorts.SOUND_TYPE.getId().equals(portName)) {
            return context.getNodeProperty(PROPERTY_SELECTED);
        }
        return null;
    }
}