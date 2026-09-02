package com.mine.geometry_node.core.node.nodes.events.entity;

import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeComment;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.nodes.events.BaseEventNode;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import net.minecraft.network.chat.Component;

public class OnProjectileShoot extends BaseEventNode {

    public static final String TYPE_ID = "on_projectile_shoot";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.EVENT, Component.translatable("geometry_node.node.on_projectile_shoot"))
                .comment(NodeComment.builder(TYPE_ID)
                        .text("summary")
                        .output(StandardPorts.PROJECTILE, "projectile")
                        .output(StandardPorts.SOURCE_ENTITY, "source_entity")
                        .output(StandardPorts.ITEM_STACK, "weapon_item_stack")
                        .output(StandardPorts.XYZ, "shoot_position")
                        .output(StandardPorts.VECTOR, "initial_velocity")
                        .build())
                .addRow(new PortRow(null, StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                // 投射物实体本身
                .addRow(new PortRow(null, StandardPorts.PROJECTILE.toOutput(), UIHint.DEFAULT, null, null))
                // 发射者 (Owner)
                .addRow(new PortRow(null, StandardPorts.SOURCE_ENTITY.toOutput(), UIHint.DEFAULT, null, null))
                // 发射该箭的武器快照；不具备统一武器来源的投射物输出空 ItemStack。
                .addRow(new PortRow(null, StandardPorts.ITEM_STACK.toOutput()
                        .withDisplayName("geometry_node.port.item_stack.weapon"), UIHint.DEFAULT, null, null))
                // 发射的精准位置
                .addRow(new PortRow(null, StandardPorts.XYZ.toOutput(), UIHint.DEFAULT, null, null))
                // 初始发射速度向量 (可用于通过节点修改轨道)
                .addRow(new PortRow(null, StandardPorts.VECTOR.toOutput(), UIHint.DEFAULT, null, null))
                .build();
    }
}
