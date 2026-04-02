package com.mine.geometry_node.core.node.nodes.maths.operation;

import com.mine.geometry_node.core.execution.ExecutionContext;
import com.mine.geometry_node.core.node.NodeData;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.meta.SchemaKeys;
import com.mine.geometry_node.core.node.nodes.*;
import com.mine.geometry_node.core.node.port.*;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MathExpression extends BaseNode {

    public static final String TYPE_ID = "math_expression";

    @Override
    public NodeDef getDefaultDefinition() {
        return buildDef(List.of("A"));
    }

    @Override
    public NodeDef getDefinition(NodeData instanceData) {
        List<String> dynamicPorts = new ArrayList<>();

        if (instanceData != null && instanceData.inputs != null) {
            for (String key : instanceData.inputs.keySet()) {
                if (key.length() == 1 && Character.isUpperCase(key.charAt(0))) {
                    dynamicPorts.add(key);
                }
            }
        }

        if (dynamicPorts.isEmpty()) {
            dynamicPorts.add("A");
        }

//        Collections.sort(dynamicPorts);
        return buildDef(dynamicPorts);
    }

    private NodeDef buildDef(List<String> variables) {
        String comment = """
                计算自定义数学表达式。
                支持运算符: +, -, *, /, ^ (幂)
                支持函数: sin, cos, tan, sqrt, abs
                支持变量: A-Z (动态输入端口)""";

        NodeDef.Builder builder = NodeDef.builder(TYPE_ID, NodeType.MATH, Component.translatable("geometry_node.node.math_expression"))
                .comment(comment)
                .addMeta(SchemaKeys.MAX_DYNAMIC_INPUT, 26);

        // 结果输出
        builder.addRow(new PortRow(null, StandardPorts.VALUE.toOutput(), UIHint.DEFAULT, null, null));

        // 公式输入框
        builder.addRow(new PortRow(StandardPorts.EXPRESSION.toInput(), null, UIHint.INPUT, null, null));

        // 动态变量输入端口（A - Z）
        for (String var : variables) {
            PortDef dynamicPort = new PortDef(var, Component.literal(var), PortType.FLOAT, 0.0f);

            builder.addRow(new PortRow(
                    dynamicPort,
                    null,
                    UIHint.DEFAULT,
                    null,
                    Map.of(PortMetaKeys.IS_DYNAMIC, true)
            ));
        }

        return builder.build();
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        if (!StandardPorts.VALUE.getId().equals(portName)) return null;

        // 获取公式字符串
        String expr = getInput(context, StandardPorts.EXPRESSION.getId(), String.class);
        if (expr == null || expr.trim().isEmpty()) return 0.0f;

        // 收集变量值
        Map<String, Double> vars = new HashMap<>();
        for (char c = 'A'; c <= 'Z'; c++) {
            String varName = String.valueOf(c);
            if (context.hasPort(varName)) {
                Float val = getInput(context, varName, Float.class);
                vars.put(varName, val != null ? val.doubleValue() : 0.0);
            }
        }

        // 执行数学解析
        try {
            double res = eval(expr, vars);
            return (float) res;
        } catch (Exception e) {
            System.err.println("[MathExpression] Expression analysis failed: '" + expr + "' -> " + e.getMessage());
            return 0.0f;
        }
    }

    // 经典的递归下降解析器
    private double eval(final String str, final Map<String, Double> vars) {
        return new Object() {
            int pos = -1, ch;

            void nextChar() {
                ch = (++pos < str.length()) ? str.charAt(pos) : -1;
            }

            boolean eat(int charToEat) {
                while (ch == ' ') nextChar();
                if (ch == charToEat) {
                    nextChar();
                    return true;
                }
                return false;
            }

            double parse() {
                nextChar();
                double x = parseExpression();
                if (pos < str.length()) throw new RuntimeException("未知字符: " + (char) ch);
                return x;
            }

            double parseExpression() {
                double x = parseTerm();
                for (;;) {
                    if      (eat('+')) x += parseTerm(); // 加
                    else if (eat('-')) x -= parseTerm(); // 减
                    else return x;
                }
            }

            double parseTerm() {
                double x = parseFactor();
                for (;;) {
                    if      (eat('*')) x *= parseFactor(); // 乘
                    else if (eat('/')) x /= parseFactor(); // 除
                    else return x;
                }
            }

            double parseFactor() {
                if (eat('+')) return parseFactor(); // 正号
                if (eat('-')) return -parseFactor(); // 负号

                double x;
                int startPos = this.pos;
                if (eat('(')) { // 括号
                    x = parseExpression();
                    eat(')');
                } else if ((ch >= '0' && ch <= '9') || ch == '.') { // 数字
                    while ((ch >= '0' && ch <= '9') || ch == '.') nextChar();
                    x = Double.parseDouble(str.substring(startPos, this.pos));
                } else if (ch >= 'a' && ch <= 'z' || ch >= 'A' && ch <= 'Z') { // 函数或变量
                    while (ch >= 'a' && ch <= 'z' || ch >= 'A' && ch <= 'Z') nextChar();
                    String func = str.substring(startPos, this.pos);

                    // 如果是单一大写字母 (A-Z)
                    if (func.length() == 1 && Character.isUpperCase(func.charAt(0))) {
                        if (!vars.containsKey(func)) {
                            System.err.println("[MathExpression] Error: unactivated variable detect: " + func);
                            x = 0.0;
                        } else {
                            x = vars.get(func);
                        }
                    } else {
                        // 标准数学函数
                        x = parseFactor();
                        if (func.equals("sqrt")) x = Math.sqrt(x);
                        else if (func.equals("sin")) x = Math.sin(x);
                        else if (func.equals("cos")) x = Math.cos(x);
                        else if (func.equals("tan")) x = Math.tan(x);
                        else if (func.equals("abs")) x = Math.abs(x);
                        else throw new RuntimeException("未知的函数名: " + func);
                    }
                } else {
                    throw new RuntimeException("Illegal expression detect: " + (char) ch);
                }

                if (eat('^')) x = Math.pow(x, parseFactor());
                return x;
            }
        }.parse();
    }
}