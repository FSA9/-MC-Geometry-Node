package com.mine.geometry_node.core.node.nodes.actions.entity;

import com.mine.geometry_node.core.execution.ExecutionContext;
import com.mine.geometry_node.core.execution.ExecutionResult;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

import java.util.List;

public class LeashEntity extends BaseNode {

    public static final String TYPE_ID = "leash_entity";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.leash_entity"))
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.ENTITY.toInput(), null, UIHint.DEFAULT, null, null)) // 被牵住的实体 (必须是 Mob)
                .addRow(new PortRow(StandardPorts.SOURCE_ENTITY.toInput(), null, UIHint.DEFAULT, null, null)) // 牵绳子的实体
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        List<Entity> mobsToLeash = getInputList(context, StandardPorts.ENTITY.getId(), Entity.class);
        Entity leashHolder = getInput(context, StandardPorts.SOURCE_ENTITY.getId(), Entity.class);

        if (leashHolder != null && !mobsToLeash.isEmpty()) {
            for (Entity entity : mobsToLeash) {
                // 原版机制：只有 Mob 才能被拴绳牵着
                if (entity instanceof Mob mob) {
                    // true 表示强制发送数据包同步给客户端
                    mob.setLeashedTo(leashHolder, true);
                }
            }
        }

        return next(StandardPorts.FLOW_OUT.getId());
    }
}