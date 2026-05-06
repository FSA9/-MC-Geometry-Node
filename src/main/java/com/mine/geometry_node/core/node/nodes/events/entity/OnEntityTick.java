package com.mine.geometry_node.core.node.nodes.events.entity;

import com.mine.geometry_node.core.execution.ExecutionContext;
import com.mine.geometry_node.core.execution.ExecutionResult;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.nodes.events.BaseEventNode;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;

public class OnEntityTick extends BaseEventNode {

    public static final String TYPE_ID = "on_entity_tick";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.EVENT, Component.translatable("geometry_node.node.on_entity_tick"))
                .addRow(new PortRow(null, StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.ENTITY.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.INTERVAL.toInput(1), null, UIHint.INPUT, null, null))
                .addRow(new PortRow(StandardPorts.OFFSET.toInput(0), null, UIHint.INPUT, null, null))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        Integer interval = getInput(context, StandardPorts.INTERVAL.getId(), Integer.class);
        Integer offset = getInput(context, StandardPorts.OFFSET.getId(), Integer.class);

        if (interval == null || interval <= 0) interval = 1;
        if (offset == null || offset < 0) offset = 0;

        if (interval == 1) {
            return next(StandardPorts.FLOW_OUT.getId());
        }

        long currentTick = context.getLevel().getGameTime();
        if (currentTick % interval == offset) {
            return next(StandardPorts.FLOW_OUT.getId());
        } else {
            return finish();
        }
    }
}