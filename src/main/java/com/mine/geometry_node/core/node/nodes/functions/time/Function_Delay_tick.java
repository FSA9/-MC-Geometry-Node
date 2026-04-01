package com.mine.geometry_node.core.node.nodes.functions.time;

import com.mine.geometry_node.core.execution.ExecutionContext;
import com.mine.geometry_node.core.execution.ExecutionResult;
import com.mine.geometry_node.core.node.nodes.*;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;

public class Function_Delay_tick extends BaseNode {
    public static final String TYPE_ID = "function_delay_s";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.FLOW_CONTROL, Component.translatable("geometry_node.node.function_delay_s"))
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.TIME.toInput(), null, UIHint.INPUT, null, null))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        Float tick = getInput(context, StandardPorts.TIME.getId(), Float.class);

        if (tick > 0) {
            long ticks = (long) (tick * 1);
            return ExecutionResult.delay(ticks, StandardPorts.FLOW_OUT.getId());
        }

        return next(StandardPorts.FLOW_OUT.getId());
    }
}