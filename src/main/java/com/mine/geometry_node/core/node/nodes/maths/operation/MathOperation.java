package com.mine.geometry_node.core.node.nodes.maths.operation;

import com.mine.geometry_node.core.execution.ExecutionContext;
import com.mine.geometry_node.core.node.NodeData;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.meta.PropertyKeys;
import com.mine.geometry_node.core.node.nodes.*;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;

import java.util.Map;

public class MathOperation extends BaseNode {

    public static final String TYPE_ID = "math_operation";

    // 定义所有支持的数学运算
    private static final String[] ALL_OPERATORS = {
            // 二元运算
            "+", "-", "*", "/", "%", "^", "min", "max", "atan2",
            // 一元运算
            "sin", "cos", "tan", "asin", "acos", "atan",
            "sqrt", "abs", "ceil", "floor", "round", "log", "log10"
    };

    @Override
    public NodeDef getDefaultDefinition() {
        return buildDef("+");
    }

    @Override
    public NodeDef getDefinition(NodeData instanceData) {
        String operator = (String) instanceData.properties.getOrDefault(PropertyKeys.SELECTION.id(), "+");
        return buildDef(operator);
    }

    private NodeDef buildDef(String operator) {
        NodeDef.Builder builder = NodeDef.builder(TYPE_ID, NodeType.MATH, Component.translatable("geometry_node.node.math_operation"));

        // 输出端口
        builder.addRow(new PortRow(null, StandardPorts.VALUE.toOutput(), UIHint.DEFAULT, null, null));

        // 下拉选择框：操作符选择
        builder.addRow(new PortRow(
                null, null, UIHint.SELECT, null,
                Map.of(
                        PortMetaKeys.BIND_PROPERTY, PropertyKeys.SELECTION.id(),
                        PortMetaKeys.OPTIONS, ALL_OPERATORS
                )));

        // 第一个输入值 (所有运算都需要)
        builder.addRow(new PortRow(StandardPorts.VALUE.toInputWithIndex(1), null, UIHint.INPUT, null, null));

        // 如果是二元运算，才显示第二个输入端口
        if (isBinaryOperator(operator)) {
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
        String operator = (String) context.getNodeProperty(PropertyKeys.SELECTION.id());
        if (operator == null) operator = "+";

        // 获取第一个输入值
        Float v1 = getInput(context, StandardPorts.VALUE.getIdWithIndex(1), Float.class);
        if (v1 == null) v1 = 0.0f;

        // 如果是二元运算，获取第二个输入值
        Float v2 = 0.0f;
        if (isBinaryOperator(operator)) {
            v2 = getInput(context, StandardPorts.VALUE.getIdWithIndex(2), Float.class);
            if (v2 == null) v2 = 0.0f;
        }

        // 执行计算
        return switch (operator) {
            // --- 二元运算 ---
            case "+" -> v1 + v2;
            case "-" -> v1 - v2;
            case "*" -> v1 * v2;
            case "/" -> v2 != 0.0f ? v1 / v2 : 0.0f; // 防除零保护
            case "%" -> v2 != 0.0f ? v1 % v2 : 0.0f; // 防除零保护
            case "^" -> (float) Math.pow(v1, v2);
            case "min" -> Math.min(v1, v2);
            case "max" -> Math.max(v1, v2);
            case "atan2" -> (float) Math.atan2(v1, v2);

            // --- 一元运算 ---
            case "sin" -> (float) Math.sin(v1);
            case "cos" -> (float) Math.cos(v1);
            case "tan" -> (float) Math.tan(v1);
            case "asin" -> (float) Math.asin(v1);
            case "acos" -> (float) Math.acos(v1);
            case "atan" -> (float) Math.atan(v1);
            case "sqrt" -> v1 >= 0 ? (float) Math.sqrt(v1) : 0.0f; // 避免负数开平方产生 NaN
            case "abs" -> Math.abs(v1);
            case "ceil" -> (float) Math.ceil(v1);
            case "floor" -> (float) Math.floor(v1);
            case "round" -> (float) Math.round(v1);
            case "log" -> v1 > 0 ? (float) Math.log(v1) : 0.0f; // 避免无效对数
            case "log10" -> v1 > 0 ? (float) Math.log10(v1) : 0.0f;

            default -> 0.0f;
        };
    }

    /**
     * 判断是否为二元运算（需要两个输入值）
     */
    private boolean isBinaryOperator(String op) {
        return switch (op) {
            case "+", "-", "*", "/", "%", "^", "min", "max", "atan2" -> true;
            default -> false;
        };
    }
}