package com.mine.geometry_node.core.node.nodes.events.inventory;

import com.mine.geometry_node.core.engine.blueprint.event.GraphEventFields;
import com.mine.geometry_node.core.node.NodeComment;
import com.mine.geometry_node.core.node.RegistryDataManager;
import com.mine.geometry_node.core.node.event.EventPrecheckSpec;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.nodes.events.BaseEventNode;
import com.mine.geometry_node.core.node.port.PortDef;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.PortType;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;

import java.util.Map;

public class OnContainerOpen extends BaseEventNode {
    public static final String TYPE_ID = "on_container_open";
    public static final String CONTAINER_TYPE_PORT = GraphEventFields.CONTAINER_TYPE;

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.EVENT, Component.translatable("geometry_node.node.on_container_open"))
                .comment(NodeComment.builder(TYPE_ID)
                        .text("summary")
                        .output(StandardPorts.FLOW_OUT, "flow_out")
                        .output(StandardPorts.ENTITY, "entity")
                        .output(StandardPorts.PLAYER, "player")
                        .output(StandardPorts.TYPE, "type")
                        .output(StandardPorts.COUNT, "count")
                        .input(CONTAINER_TYPE_PORT, "container_type")
                        .build())
                .addMeta(EventPrecheckSpec.META_KEY, EventPrecheckSpec.builder()
                        .staticValueEqualsEventField(CONTAINER_TYPE_PORT, GraphEventFields.CONTAINER_TYPE)
                        .build())
                .addRow(new PortRow(null, StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.ENTITY.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.PLAYER.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.TYPE.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.COUNT.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(
                        PortDef.create(CONTAINER_TYPE_PORT, "geometry_node.port.container_type", PortType.STRING, "").hiddenPin(),
                        null,
                        UIHint.SELECT,
                        null,
                        Map.of(PortMetaKeys.OPTIONS, RegistryDataManager.withEmptyOption(RegistryDataManager.getAllMenus()))
                ))
                .build();
    }
}
