package com.mine.geometry_node.core.node.nodes.data.type;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.node.RegistryDataManager;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.nodes.*;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import net.minecraft.network.chat.Component;

import java.util.Map;

public class GetEffect extends BaseNode {

    public static final String TYPE_ID = "get_effect";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.DATA, Component.translatable("geometry_node.node.get_effect"))
                .addRow(new PortRow(null, StandardPorts.TYPE.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(
                        StandardPorts.STRING.toInput().hiddenPin(),
                        null,
                        UIHint.SELECT,
                        null,
                        Map.of(PortMetaKeys.OPTIONS, RegistryDataManager.getAllEffects().toArray(new String[0]))
                ))
                .build();
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        if (StandardPorts.TYPE.getId().equals(portName)) {
            return getInput(context, StandardPorts.STRING.getId(), String.class);
        }
        return null;
    }
}