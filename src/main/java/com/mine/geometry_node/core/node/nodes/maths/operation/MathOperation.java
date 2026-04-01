package com.mine.geometry_node.core.node.nodes.maths.operation;

import com.mine.geometry_node.core.execution.ExecutionContext;
import com.mine.geometry_node.core.node.NodeData;
import com.mine.geometry_node.core.node.meta.PropertyKeys;
import com.mine.geometry_node.core.node.nodes.*;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;

import java.util.Map;

public class MathOperation extends BaseNode {

    public static final String TYPE_ID = "math_operation";

    @Override
    public NodeDef getDefaultDefinition() {
        return buildDef("+");
    }

    @Override
    public NodeDef getDefinition(NodeData instanceData) {
        String operator = (String) instanceData.properties.getOrDefault(PropertyKeys.OPERATOR.id(), "+");
        return buildDef(operator);
    }

    private NodeDef buildDef(String operator) {
        NodeDef.Builder builder = NodeDef.builder(TYPE_ID, NodeType.MATH, Component.translatable("geometry_node.node.math_operation"));
        builder.addRow(new PortRow(null, StandardPorts.VALUE.toOutput(), UIHint.DEFAULT, null, null));
        builder.addRow(PortRow.select(PropertyKeys.OPERATOR, new String[]{"+", "-", "sin", "cos"}));
        builder.addRow(new PortRow(StandardPorts.VALUE.toInputWithIndex(1), null, UIHint.INPUT, null, null));
        if ("+".equals(operator) || "-".equals(operator)) {
            builder.addRow(new PortRow(
                    StandardPorts.VALUE.toInputWithIndex(2),
                    null, UIHint.INPUT, null, null
            ));
        }

        return builder.build();
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        if (!StandardPorts.VALUE.getId().equals(portName)) return null;

        // 获取操作符
        String operator = (String) context.getNodeProperty(PropertyKeys.OPERATOR.id());
        if (operator == null) operator = "+";

        // 获取输入值
        Float v1 = getInput(context, StandardPorts.VALUE.getIdWithIndex(1), Float.class);
        if (v1 == null) v1 = 0.0f;

        switch (operator) {
            case "+":
                Float v2Add = getInput(context, StandardPorts.VALUE.getIdWithIndex(2), Float.class);
                return v1 + (v2Add != null ? v2Add : 0.0f);
            case "-":
                Float v2Sub = getInput(context, StandardPorts.VALUE.getIdWithIndex(2), Float.class);
                return v1 - (v2Sub != null ? v2Sub : 0.0f);
            case "sin":
                return (float) Math.sin(v1);
            case "cos":
                return (float) Math.cos(v1);
            default:
                return 0.0f;
        }
    }
}