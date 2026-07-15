package com.mine.geometry_node.core.node.nodes.events.block;

import com.mine.geometry_node.core.engine.blueprint.event.GraphEventFields;
import com.mine.geometry_node.core.node.RegistryDataManager;
import com.mine.geometry_node.core.node.event.EventPrecheckSpec;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.nodes.*;
import com.mine.geometry_node.core.node.nodes.events.BaseEventNode;
import com.mine.geometry_node.core.node.port.PortDef;
import com.mine.geometry_node.core.node.port.PortType;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;

import java.util.Map;

public class OnBlockBreak extends BaseEventNode {

    public static final String TYPE_ID = "on_block_break";
    public static final String BLOCK_TYPE_PORT = GraphEventFields.BLOCK_TYPE;

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.EVENT, Component.translatable("geometry_node.node.on_block_break"))
                .addMeta(EventPrecheckSpec.META_KEY, EventPrecheckSpec.builder()
                        .staticValueEqualsEventField(BLOCK_TYPE_PORT, GraphEventFields.BLOCK_TYPE)
                        .build())
                .addRow(new PortRow(null, StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.ENTITY.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.BLOCK_STATE.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.DIMENSION.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.XYZ.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(
                        PortDef.create(BLOCK_TYPE_PORT, "geometry_node.port.block_type", PortType.STRING, "").hiddenPin(),
                        null,
                        UIHint.SELECT,
                        null,
                        Map.of(PortMetaKeys.OPTIONS, RegistryDataManager.withEmptyOption(RegistryDataManager.getAllBlocks()))
                ))
                .build();
    }
}
