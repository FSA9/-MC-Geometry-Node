package com.mine.geometry_node.core.node.nodes.events.player;

import com.mine.geometry_node.core.engine.blueprint.event.GraphEventFields;
import com.mine.geometry_node.core.node.RegistryDataManager;
import com.mine.geometry_node.core.node.event.EventPrecheckSpec;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.nodes.events.BaseEventNode;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;

import java.util.Map;

public class OnPlayerKeyEvent extends BaseEventNode {

    public static final String TYPE_ID = "on_player_key_event";

    // 静态定义可选的按键和动作（严格限制玩家输入）
    public static final String[] VALID_KEYS = {
            "skill_1", "skill_2", "skill_3", "skill_4", "skill_5",
            "skill_6", "skill_7", "skill_8", "skill_9", "skill_10",
            "ctrl", "shift", "alt"
    };
    public static final String[] VALID_ACTIONS = {"PRESS", "RELEASE", "DOUBLE_CLICK"};

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.EVENT, Component.translatable("geometry_node.node.on_player_key_event"))
                .addMeta(EventPrecheckSpec.META_KEY, EventPrecheckSpec.builder()
                        .staticValueEqualsEventField(StandardPorts.NAME.getId(), GraphEventFields.KEY_ID)
                        .staticValueEqualsEventField(StandardPorts.TYPE.getId(), GraphEventFields.ACTION)
                        .build())
                // --- 输出 ---
                .addRow(new PortRow(null, StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.ENTITY.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.TIME.toOutput(), UIHint.DEFAULT, null, null))

                // --- 输入 (通过 UIHint.SELECT 和 PortMetaKeys 限制为下拉框) ---
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
