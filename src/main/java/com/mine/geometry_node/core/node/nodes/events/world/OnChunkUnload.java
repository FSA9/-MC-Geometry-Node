package com.mine.geometry_node.core.node.nodes.events.world;

import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.nodes.events.BaseEventNode;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import net.minecraft.network.chat.Component;

public class OnChunkUnload extends BaseEventNode {
    public static final String TYPE_ID = "on_chunk_unload";
    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.EVENT, Component.translatable("geometry_node.node.on_chunk_unload"))
                .addRow(new PortRow(null, StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.XYZ.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.DIMENSION.toOutput(), UIHint.DEFAULT, null, null))
                .build();
    }
}
