package com.mine.geometry_node.core.node.nodes.events.player;

import com.mine.geometry_node.core.engine.blueprint.event.GraphEventFields;
import com.mine.geometry_node.core.engine.blueprint.event.PlayerInputKeys;
import com.mine.geometry_node.core.node.definition.node.NodeComment;
import com.mine.geometry_node.core.node.RegistryDataManager;
import com.mine.geometry_node.core.node.event.EventPrecheckSpec;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.nodes.events.BaseEventNode;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import net.minecraft.network.chat.Component;

import java.util.Map;

public class OnPlayerKeyEvent extends BaseEventNode {

    public static final String TYPE_ID = "on_player_key_event";

    public static final String[] VALID_ACTIONS = {"PRESS", "RELEASE"};

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.EVENT, Component.translatable("geometry_node.node.on_player_key_event"))
                .comment(NodeComment.builder(TYPE_ID)
                        .text("summary")
                        .output(StandardPorts.FLOW_OUT, "flow_out")
                        .output(StandardPorts.ENTITY, "entity")
                        .output(StandardPorts.TICK, "tick")
                        .input(StandardPorts.NAME, "name")
                        .input(StandardPorts.TYPE, "type")
                        .build())
                .addMeta(EventPrecheckSpec.META_KEY, EventPrecheckSpec.builder()
                        .staticValueEqualsEventField(StandardPorts.NAME.getId(), GraphEventFields.KEY_ID)
                        .staticValueEqualsEventField(StandardPorts.TYPE.getId(), GraphEventFields.ACTION)
                        .build())
                .addRow(new PortRow(null, StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.ENTITY.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.TICK.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(
                        StandardPorts.NAME.toInput(""), null,
                        UIHint.SELECT, null,
                        Map.of(PortMetaKeys.OPTIONS, RegistryDataManager.withEmptyOption(PlayerInputKeys.ALL_KEYS)) // 注入按键选项
                ))
                .addRow(new PortRow(
                        StandardPorts.TYPE.toInput(""), null,
                        UIHint.SELECT, null,
                        Map.of(PortMetaKeys.OPTIONS, RegistryDataManager.withEmptyOption(VALID_ACTIONS)) // 注入动作选项
                ))
                .addRow(new PortRow(
                        StandardPorts.INTERCEPT.toInput(false), null,
                        UIHint.CHECKBOX, null, null
                ))
                .build();
    }
}
