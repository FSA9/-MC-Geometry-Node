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

import java.util.List;

public class SetInvulnerableTicks extends BaseNode {

    public static final String TYPE_ID = "set_invulnerable_ticks";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.set_invulnerable_ticks"))
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.ENTITY.toInput(), null, UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.TICK.toInput(), null, UIHint.INPUT, null, null)) // 使用 TICK 作为整数输入
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        List<Entity> entities = getInputList(context, StandardPorts.ENTITY.getId(), Entity.class);
        Integer ticks = getInput(context, StandardPorts.TICK.getId(), Integer.class);

        if (ticks != null && ticks >= 0 && !entities.isEmpty()) {
            for (Entity entity : entities) {
                entity.invulnerableTime = ticks;
            }
        }

        return next(StandardPorts.FLOW_OUT.getId());
    }
}