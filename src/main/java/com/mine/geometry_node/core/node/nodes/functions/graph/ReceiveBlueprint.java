package com.mine.geometry_node.core.node.nodes.functions.graph;

import com.mine.geometry_node.core.execution.ExecutionContext;
import com.mine.geometry_node.core.execution.ExecutionResult;
import com.mine.geometry_node.core.node.nodes.*;
import com.mine.geometry_node.core.node.port.*;
import net.minecraft.network.chat.Component;

public class ReceiveBlueprint extends BaseNode {

    public static final String TYPE_ID = "receive_blueprint";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.EVENT, Component.translatable("geometry_node.node.receive_blueprint"))
                .addRow(new PortRow(
                        null,
                        StandardPorts.FLOW_OUT.toExec(),
                        UIHint.DEFAULT, null, null
                ))
                .addRow(new PortRow(
                        PortDef.create("frequency", "geometry_node.port.frequency", PortType.STRING, ""),
                        null, UIHint.INPUT, null, null
                ))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        return next(StandardPorts.FLOW_OUT.getId());
    }
}