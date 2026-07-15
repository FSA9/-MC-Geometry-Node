package com.mine.geometry_node.core.node.nodes.events.entity;

import com.mine.geometry_node.core.node.event.EventPrecheckSpec;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.nodes.events.BaseEventNode;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;

public class OnEntityTick extends BaseEventNode {

    public static final String TYPE_ID = "on_entity_tick";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.EVENT, Component.translatable("geometry_node.node.on_entity_tick"))
                .addMeta(EventPrecheckSpec.META_KEY, EventPrecheckSpec.builder()
                        .tickInterval(StandardPorts.INTERVAL.getId(), StandardPorts.OFFSET.getId())
                        .build())
                .addRow(new PortRow(null, StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.ENTITY.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.INTERVAL.toInput(1).hiddenPin(), null, UIHint.INPUT, null, null))
                .addRow(new PortRow(StandardPorts.OFFSET.toInput(0).hiddenPin(), null, UIHint.INPUT, null, null))
                .build();
    }
}
