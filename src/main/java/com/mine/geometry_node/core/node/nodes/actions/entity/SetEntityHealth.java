package com.mine.geometry_node.core.node.nodes.actions.entity;

import com.mine.geometry_node.core.execution.ExecutionContext;
import com.mine.geometry_node.core.execution.ExecutionResult;
import com.mine.geometry_node.core.node.nodes.*;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

public class SetEntityHealth extends BaseNode {

    public static final String TYPE_ID = "set_entity_health";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.set_entity_health"))
                // 第一行：执行流输入与输出
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                // 第二行：实体输入
                .addRow(new PortRow(StandardPorts.ENTITY.toInput(), null, UIHint.DEFAULT, null, null))
                // 第三行：数值输入（提供UI输入框）
                .addRow(new PortRow(StandardPorts.VALUE.toInput(), null, UIHint.INPUT, null, null))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        List<Entity> entities = getInputList(context, StandardPorts.ENTITY.getId(), Entity.class);
        Float health = getInput(context, StandardPorts.VALUE.getId(), Float.class);

        if (health != null && !entities.isEmpty()) {
            for (Entity entity : entities) {
                if (entity instanceof LivingEntity living) {
                    living.setHealth(health);
                }
            }
        }

        // 继续向下执行
        return next(StandardPorts.FLOW_OUT.getId());
    }
}