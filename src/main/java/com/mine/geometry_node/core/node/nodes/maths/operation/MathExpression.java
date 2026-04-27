package com.mine.geometry_node.core.node.nodes.maths.operation;

import com.mine.geometry_node.core.execution.ExecutionContext;
import com.mine.geometry_node.core.execution.datatypes.DynamicData;
import com.mine.geometry_node.core.execution.datatypes.ExpressionData;
import com.mine.geometry_node.core.node.NodeData;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.meta.PropertyKeys;
import com.mine.geometry_node.core.node.meta.SchemaKeys;
import com.mine.geometry_node.core.node.nodes.*;
import com.mine.geometry_node.core.node.port.*;
import com.mine.geometry_node.core.utils.ASTNode;
import com.mine.geometry_node.core.utils.ExpressionCompiler;
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
        int portCount = 1; // 默认最少 1 个变量 (A)

        // 1. 像 Switch 一样，从 Properties 中读取 UI 保存的动态端口数量
        if (instanceData != null && instanceData.properties.containsKey(PropertyKeys.DYNAMIC_BRANCH_INPUT_COUNT.id())) {
            Object countObj = instanceData.properties.get(PropertyKeys.DYNAMIC_BRANCH_INPUT_COUNT.id());
            if (countObj instanceof Number n) {
                portCount = n.intValue();
            } else if (countObj instanceof String s) {
                try { portCount = Integer.parseInt(s); } catch (NumberFormatException ignored) {}
            }
        }

        // 2. 兜底保护，最多 26 个字母 (A - Z)
        portCount = Math.max(1, Math.min(portCount, 26));

        // 3. 根据数量生成 A, B, C, D...
        List<String> dynamicPorts = new ArrayList<>();
        for (int i = 0; i < portCount; i++) {
            // 通过 ASCII 码偏移生成字母: 0 -> 'A', 1 -> 'B', 2 -> 'C'
            char varName = (char) ('A' + i);
            dynamicPorts.add(String.valueOf(varName));
        }

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

        String mainExpr = getInput(context, StandardPorts.EXPRESSION.getId(), String.class);
        if (mainExpr == null || mainExpr.trim().isEmpty()) {
            mainExpr = "0"; // 防止没填表达式时崩溃
        }

        Map<String, Double> evalVars = new HashMap<>();     // 物理计算用
        Map<String, String> mergedBindings = new HashMap<>(); // 视觉协议用
        String finalFormula = mainExpr;

        double serverTick = context.getLevel() != null ? context.getLevel().getGameTime() : 0.0;
        evalVars.put("tick", serverTick);

        // 【关键修改】：去掉 hasPort，无论如何都去要一次数据
        for (char c = 'A'; c <= 'Z'; c++) {
            String varName = String.valueOf(c);

            // 1. 尝试获取活公式
            ExpressionData inputExpr = getInput(context, varName, ExpressionData.class);
            // 2. 尝试获取死数字 (如果没连线，会返回 null)
            Float val = getInput(context, varName, Float.class);

            // 存入字典。如果没连线 val 就是 null，兜底存入 0.0，彻底告别 unactivated 报错！
            evalVars.put(varName, val != null ? val.doubleValue() : 0.0);

            // 如果连线了，并且传来了公式，就进行字符串嵌套
            if (inputExpr != null && inputExpr.formula() != null && !inputExpr.formula().isEmpty() && !inputExpr.formula().equals("0")) {
                finalFormula = finalFormula.replace(varName, "(" + inputExpr.formula() + ")");
                mergedBindings.putAll(inputExpr.bindings());
            }
        }

        try {
            // 【核心优化】：服务端 AST 缓存复用机制
            // 利用当前节点的运行时 ID 作为黑板 Key，确保 AST 树与图进程同生共死
            String cacheKey = "AST_" + context.getCurrentNodeId();

            // 尝试从图进程的临时黑板中获取已经编译好的 AST 树
            // 注意：这里的 ASTNode 必须是你移到 core 包之后的那个！
            ASTNode ast = (ASTNode) context.getTempData(cacheKey);

            if (ast == null) {
                // 如果没有，说明是本图进程第一次走到这个节点，触发编译并塞入黑板缓存
                ast = ExpressionCompiler.compile(mainExpr);
                context.setTempData(cacheKey, ast);
            }

            // 物理层面的 20FPS 运算：直接跑 AST 树，极速完成
            double resultValue = ast.evaluate(evalVars);

            // 视觉层面的 动态公式打包
            return new DynamicData((float) resultValue, new ExpressionData(finalFormula, mergedBindings));

        } catch (Exception e) {
            System.err.println("[MathExpression] Compute Error: '" + mainExpr + "' -> " + e.getMessage());
            return new DynamicData(0.0f, ExpressionData.ZERO);
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
                    while ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9') || ch == '_') {
                        nextChar();
                    }
                    String func = str.substring(startPos, this.pos);

                    // 1. 拦截内置的时间变量 tick (忽略大小写)
                    if (func.equalsIgnoreCase("tick")) {
                        x = vars.getOrDefault("tick", 0.0);
                    }
                    // 2. 拦截单一大写字母 (A-Z 端口变量)
                    else if (func.length() == 1 && Character.isUpperCase(func.charAt(0))) {
                        x = vars.getOrDefault(func, 0.0);
                    }
                    // 3. 解析标准数学函数 (这部分必须有括号和后续参数)
                    else {
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