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

public class GetItemType extends BaseNode {

    public static final String TYPE_ID = "get_item_type";
    public static final String PROPERTY_SELECTED = PortMetaKeys.DYNAMIC_REGISTRY_ID.id();

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.DATA, Component.translatable("geometry_node.node.get_item_type"))
                .addRow(new PortRow(null, StandardPorts.ITEM_TYPE.toOutput(), null, null, null))
                .addRow(new PortRow(
                        null,
                        null,
                        UIHint.SELECT,
                        null,
                        Map.of(
                                PortMetaKeys.BIND_PROPERTY, PROPERTY_SELECTED,
                                PortMetaKeys.OPTIONS, RegistryDataManager.getAllItems().toArray(new String[0])
                        )
                ))
                .build();
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        if (StandardPorts.ITEM_TYPE.getId().equals(portName)) {
            return context.getNodeProperty(PROPERTY_SELECTED);
        }
        return null;
    }
}