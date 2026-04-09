package com.mine.geometry_node.core.node.nodes.functions.graph;

import com.mine.geometry_node.core.execution.ExecutionContext;
import com.mine.geometry_node.core.execution.ExecutionResult;
import com.mine.geometry_node.core.node.nodes.*;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;

public class FinishGraph extends BaseNode {

    public static final String TYPE_ID = "finish_graph";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.finish_graph"))
                .addRow(new PortRow(
                        StandardPorts.FLOW_IN.toExec(),
                        null,
                        UIHint.DEFAULT, null, null
                ))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        return finish();
    }
}