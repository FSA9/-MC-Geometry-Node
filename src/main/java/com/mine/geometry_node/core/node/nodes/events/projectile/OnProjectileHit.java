package com.mine.geometry_node.core.node.nodes.events.projectile;

import com.mine.geometry_node.core.node.definition.node.NodeComment;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.nodes.events.BaseEventNode;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import net.minecraft.network.chat.Component;

public class OnProjectileHit extends BaseEventNode {

    public static final String TYPE_ID = "on_projectile_hit";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.EVENT, Component.translatable("geometry_node.node.on_projectile_hit"))
                .comment(NodeComment.builder(TYPE_ID)
                        .text("summary")
                        .text("interception")
                        .output(StandardPorts.PROJECTILE, "projectile")
                        .output(StandardPorts.SOURCE_ENTITY, "source_entity")
                        .output(StandardPorts.HIT_ENTITY, "hit_entity")
                        .output(StandardPorts.VECTOR, "impact_velocity")
                        .output(StandardPorts.XYZ, "hit_position")
                        .output(StandardPorts.HIT_NORMAL, "hit_normal")
                        .output(StandardPorts.PREVIOUS_POS, "previous_position")
                        .output(StandardPorts.BLOCK_STATE, "block_state")
                        .input(StandardPorts.INTERCEPT, "intercept")
                        .build())
                .addRow(new PortRow(null, StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                // 投射物本身
                .addRow(new PortRow(null, StandardPorts.PROJECTILE.toOutput(), UIHint.DEFAULT, null, null))
                // 投射物的发射者
                .addRow(new PortRow(null, StandardPorts.SOURCE_ENTITY.toOutput(), UIHint.DEFAULT, null, null))
                // 击中的实体
                .addRow(new PortRow(null, StandardPorts.HIT_ENTITY.toOutput(), UIHint.DEFAULT, null, null))
                // 碰撞瞬间的投射物运动矢量
                .addRow(new PortRow(null, StandardPorts.VECTOR.toOutput(), UIHint.DEFAULT, null, null))
                // 击中位置的精准坐标
                .addRow(new PortRow(null, StandardPorts.XYZ.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.HIT_NORMAL.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.PREVIOUS_POS.toOutput(), UIHint.DEFAULT, null, null))
                // 击中的方块状态
                .addRow(new PortRow(null, StandardPorts.BLOCK_STATE.toOutput(), UIHint.DEFAULT, null, null))
                .addStaticInput(StandardPorts.INTERCEPT.toInput(false)
                        .withDisplayName("geometry_node.port.intercept.projectile_hit_effects"), UIHint.CHECKBOX)
                .build();
    }
}
