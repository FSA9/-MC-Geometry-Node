package com.mine.geometry_node.core.node.nodes.events.entity;

import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.nodes.events.BaseEventNode;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import net.minecraft.network.chat.Component;

public class OnEntityPotionEffectApply extends BaseEventNode {

    public static final String TYPE_ID = "on_entity_potion_effect_apply";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.EVENT, Component.translatable("geometry_node.node.on_entity_potion_effect_apply"))
                .addRow(new PortRow(null, StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.ENTITY.toOutput(), UIHint.DEFAULT, null, null))
                // 输出药水效果的注册名 (例如 "minecraft:speed")
                .addRow(new PortRow(null, StandardPorts.TYPE.toOutput(), UIHint.DEFAULT, null, null))
                // 输出药水等级 (Amplifier)
                .addRow(new PortRow(null, StandardPorts.INT_VALUE.toOutput(), UIHint.DEFAULT, null, null))
                .build();
    }
}
