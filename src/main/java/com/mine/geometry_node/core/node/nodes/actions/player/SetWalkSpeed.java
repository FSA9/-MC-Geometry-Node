package com.mine.geometry_node.core.node.nodes.actions.player;

import com.mine.geometry_node.core.execution.ExecutionContext;
import com.mine.geometry_node.core.execution.ExecutionResult;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.List;

public class SetWalkSpeed extends BaseNode {

    public static final String TYPE_ID = "set_walk_speed";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.set_walk_speed"))
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.ENTITY.toInput(), null, UIHint.DEFAULT, null, null))
                // 默认移速是 0.1f
                .addRow(new PortRow(StandardPorts.VALUE.toInput(0.1f), null, UIHint.INPUT, null, null))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        List<Entity> entities = getInputList(context, StandardPorts.ENTITY.getId(), Entity.class);
        Float speed = getInput(context, StandardPorts.VALUE.getId(), Float.class);

        if (speed != null && !entities.isEmpty()) {
            for (Entity entity : entities) {
                if (entity instanceof ServerPlayer player) {
                    // 设置 abilities 中的行走速度
                    player.getAbilities().setWalkingSpeed(speed);
                    // 必须触发更新才能将 abilities 数据包发给客户端
                    player.onUpdateAbilities();
                }
            }
        }

        return next(StandardPorts.FLOW_OUT.getId());
    }
}