package com.mine.geometry_node.core.node.nodes.maths;

import com.mine.geometry_node.core.engine.graph.data.GraphDataContext;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.nodes.*;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import net.minecraft.network.chat.Component;

public class SnapshotValue extends BaseNode {

    public static final String TYPE_ID = "snapshot_value";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.MATH, Component.translatable("geometry_node.node.snapshot_value"))
                .addRow(new PortRow(null, StandardPorts.RESULT_VALUE.toOutput(), UIHint.DEFAULT, null, null))
                .addPassthroughInput(StandardPorts.GENERIC_VALUE.toInput(), UIHint.DEFAULT)
                .build();
    }

    @Override
    public Object compute(GraphDataContext context, String portName) {
        if (!StandardPorts.RESULT_VALUE.getId().equals(portName)) return null;

        return getInput(context, StandardPorts.GENERIC_VALUE.getId(), Object.class);
    }
}
