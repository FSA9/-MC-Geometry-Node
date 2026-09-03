package com.mine.geometry_node.core.node.nodes.maths;

import com.mine.geometry_node.core.engine.graph.data.GraphDataContext;
import com.mine.geometry_node.core.node.definition.node.NodeComment;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.nodes.*;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import net.minecraft.network.chat.Component;

public class RandomValue extends BaseNode {

    public static final String TYPE_ID = "random_value";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.MATH, Component.translatable("geometry_node.node.random_value"))
                .comment(NodeComment.builder(TYPE_ID)
                        .output(StandardPorts.FLOAT_VALUE, "output")
                        .input(StandardPorts.FLOAT_VALUE.getIdWithIndex(1), "min")
                        .input(StandardPorts.FLOAT_VALUE.getIdWithIndex(2), "max")
                        .build())
                .addRow(new PortRow(
                        null,
                        StandardPorts.FLOAT_VALUE.toOutput(),
                        UIHint.DEFAULT, null, null
                ))
                .addPassthroughInput(StandardPorts.FLOAT_VALUE.toInputWithIndex(1, 0.0f), UIHint.INPUT, null, null)
                .addPassthroughInput(StandardPorts.FLOAT_VALUE.toInputWithIndex(2, 1.0f), UIHint.INPUT, null, null)
                .build();
    }

    @Override
    public Object compute(GraphDataContext context, String portName) {
        if (!StandardPorts.FLOAT_VALUE.getId().equals(portName)) {
            return null;
        }

        Float min = getInput(context, StandardPorts.FLOAT_VALUE.getIdWithIndex(1), Float.class);
        Float max = getInput(context, StandardPorts.FLOAT_VALUE.getIdWithIndex(2), Float.class);

        float minVal = min != null ? min : 0.0f;
        float maxVal = max != null ? max : 1.0f;

        if (minVal > maxVal) {
            float temp = minVal;
            minVal = maxVal;
            maxVal = temp;
        }

        return minVal + (float) Math.random() * (maxVal - minVal);
    }
}
