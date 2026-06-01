package com.mine.geometry_node.core.node.nodes.events.entity;

import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.nodes.events.BaseEventNode;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;

public class OnProjectileHit extends BaseEventNode {

    public static final String TYPE_ID = "on_projectile_hit";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.EVENT, Component.translatable("geometry_node.node.on_projectile_hit"))
                .addRow(new PortRow(null, StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                // 投掷物本身 (如雪球、箭)
                .addRow(new PortRow(null, StandardPorts.ENTITY.toOutput(), UIHint.DEFAULT, null, null))
                // 投掷物的发射者
                .addRow(new PortRow(null, StandardPorts.SOURCE_ENTITY.toOutput(), UIHint.DEFAULT, null, null))
                // 击中的实体 (如果是击中方块，此项为 null)
                .addRow(new PortRow(null, StandardPorts.TRIGGER_ENTITY.toOutput(), UIHint.DEFAULT, null, null))
                // 碰撞瞬间的投掷物运动矢量
                .addRow(new PortRow(null, StandardPorts.VECTOR.toOutput(), UIHint.DEFAULT, null, null))
                // 击中位置的精准坐标
                .addRow(new PortRow(null, StandardPorts.XYZ.toOutput(), UIHint.DEFAULT, null, null))
                // 击中的方块状态 (如果是击中实体，此项为 null)
                .addRow(new PortRow(null, StandardPorts.BLOCK_STATE.toOutput(), UIHint.DEFAULT, null, null))
                .build();
    }
}