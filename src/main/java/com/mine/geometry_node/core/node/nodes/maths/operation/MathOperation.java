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
            // 比较运算
            ">", "<", "==", ">=", "<=", "!=",
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
        String operator = (String) instanceData.inputs.getOrDefault(StandardPorts.STRING.toInput().id(), "+");
        return buildDef(operator);
    }

    private NodeDef buildDef(String operator) {
        NodeDef.Builder builder = NodeDef.builder(TYPE_ID, NodeType.MATH, Component.translatable("geometry_node.node.math_operation"));

        // 输出端口
        builder.addRow(new PortRow(null, StandardPorts.VALUE.toOutput(), UIHint.DEFAULT, null, null));
        // 下拉选择框
        builder.addRow(new PortRow(
                StandardPorts.STRING.toInput().hiddenPin(), null, UIHint.SELECT, null,
                Map.of(PortMetaKeys.OPTIONS, ALL_OPERATORS)));
        builder.addRow(new PortRow(StandardPorts.VALUE.toInputWithIndex(1), null, UIHint.INPUT, null, null));
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

        // 获取原始输入值（不急着赋默认值，用于比较运算的 null 检测）
        Float rawV1 = getInput(context, StandardPorts.VALUE.getIdWithIndex(1), Float.class);
        Float rawV2 = null;
        if (isBinaryOperator(operator)) {
            rawV2 = getInput(context, StandardPorts.VALUE.getIdWithIndex(2), Float.class);
        }

        // 常规运算的回落值
        float v1 = rawV1 != null ? rawV1 : 0.0f;
        float v2 = rawV2 != null ? rawV2 : 0.0f;

        // 执行计算
        return switch (operator) {
            // --- 比较运算 (任一为 null 直接返回 0.0f，否则计算真假返回 1.0f / 0.0f) ---
            case ">"  -> (rawV1 != null && rawV2 != null && v1 > v2)  ? 1.0f : 0.0f;
            case "<"  -> (rawV1 != null && rawV2 != null && v1 < v2)  ? 1.0f : 0.0f;
            case "==" -> (rawV1 != null && rawV2 != null && v1 == v2) ? 1.0f : 0.0f;
            case ">=" -> (rawV1 != null && rawV2 != null && v1 >= v2) ? 1.0f : 0.0f;
            case "<=" -> (rawV1 != null && rawV2 != null && v1 <= v2) ? 1.0f : 0.0f;
            case "!=" -> (rawV1 != null && rawV2 != null && v1 != v2) ? 1.0f : 0.0f;

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

    private boolean isBinaryOperator(String op) {
        return switch (op) {
            case "+", "-", "*", "/", "%", "^", "min", "max", "atan2",
                 ">", "<", "==", ">=", "<=", "!=" -> true;
            default -> false;
        };
    }
}