package com.mine.geometry_node.core.node.nodes.events.player;

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

public class OnPlayerKeyEvent extends BaseEventNode {

    public static final String TYPE_ID = "on_player_key_event";

    public static final String[] VALID_KEYS = {
            "space", "tab", "enter", "ctrl", "shift", "alt",
            "skill_1", "skill_2", "skill_3", "skill_4", "skill_5",
            "skill_6", "skill_7", "skill_8", "skill_9", "skill_10"
    };
    public static final String[] VALID_ACTIONS = {"PRESS", "RELEASE"};

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.EVENT, Component.translatable("geometry_node.node.on_player_key_event"))
                .comment(NodeComment.builder(TYPE_ID)
                        .text("summary")
                        .output(StandardPorts.FLOW_OUT, "flow_out")
                        .output(StandardPorts.ENTITY, "entity")
                        .output(StandardPorts.TIME, "time")
                        .input(StandardPorts.NAME, "name")
                        .input(StandardPorts.TYPE, "type")
                        .build())
                .addMeta(EventPrecheckSpec.META_KEY, EventPrecheckSpec.builder()
                        .staticValueEqualsEventField(StandardPorts.NAME.getId(), GraphEventFields.KEY_ID)
                        .staticValueEqualsEventField(StandardPorts.TYPE.getId(), GraphEventFields.ACTION)
                        .build())
                .addRow(new PortRow(null, StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.ENTITY.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, PortDef.create(
                        StandardPorts.TIME.getId(), StandardPorts.TIME.getTranslationKey(), PortType.INTEGER
                ), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(
                        StandardPorts.NAME.toInput(""), null,
                        UIHint.SELECT, null,
                        Map.of(PortMetaKeys.OPTIONS, RegistryDataManager.withEmptyOption(VALID_KEYS)) // 注入按键选项
                ))
                .addRow(new PortRow(
                        StandardPorts.TYPE.toInput(""), null,
                        UIHint.SELECT, null,
                        Map.of(PortMetaKeys.OPTIONS, RegistryDataManager.withEmptyOption(VALID_ACTIONS)) // 注入动作选项
                ))
                .build();
    }
}
