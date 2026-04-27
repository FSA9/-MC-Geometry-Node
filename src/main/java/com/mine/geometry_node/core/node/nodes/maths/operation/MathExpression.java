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
import com.mine.geometry_node.core.utils.ASTNodes;
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
            mainExpr = "0";
        }

        // 1. 编译或获取主公式 AST (修复缓存失效 Bug)
        // 缓存 Key 包含公式 Hash，确保公式改了能自动重编译
        String cacheKey = "AST_" + context.getCurrentNodeId() + "_" + mainExpr.hashCode();
        ASTNode mainAst = (ASTNode) context.getTempData(cacheKey);
        if (mainAst == null) {
            mainAst = ExpressionCompiler.compile(mainExpr);
            context.setTempData(cacheKey, mainAst);
        }

        Map<String, Double> evalVars = new HashMap<>();     // 物理计算变量表
        Map<String, ASTNode> substitutions = new HashMap<>(); // AST 嫁接表
        Map<String, String> mergedBindings = new HashMap<>(); // 视觉协议表

        double serverTick = context.getLevel() != null ? context.getLevel().getGameTime() : 0.0;
        evalVars.put("tick", serverTick);

        // 2. 遍历 A-Z 端口，进行数据采集与子树准备
        for (char c = 'A'; c <= 'Z'; c++) {
            String varName = String.valueOf(c);

            // 【修复 1】：坚决去掉 hasPort() 检查！顺应你的原始设计，无论如何都要去拉取数据
            // 物理数值采集
            Float val = getInput(context, varName, Float.class);
            double numVal = val != null ? val.doubleValue() : 0.0;
            evalVars.put(varName, numVal);

            // 视觉协议采集 (ExpressionData)
            ExpressionData inputExpr = getInput(context, varName, ExpressionData.class);

            if (inputExpr != null && inputExpr.formula() != null && !inputExpr.formula().isEmpty() && !inputExpr.formula().equals("0")) {
                // 将上游的活公式编译为子 AST
                ASTNode subAst = ExpressionCompiler.compile(inputExpr.formula());
                substitutions.put(varName, subAst);
                // 合并绑定协议
                mergedBindings.putAll(inputExpr.bindings());
            } else {
                // 【修复 2】：兜底保护！
                // 如果连线传来的是死数字，或者是没连线，必须将其转为常量节点嫁接进去！
                // 否则生成的公式里会残留字母 (例如 "A")，导致客户端找不到变量从而解析成 0.0！
                substitutions.put(varName, new ASTNodes.ConstantNode(numVal));
            }
        }

        try {
            // 3. 执行物理层计算（直接用主树跑，因为它不依赖字符串）
            double resultValue = mainAst.evaluate(evalVars);

            // 4. 执行视觉层嫁接（生成最终发往客户端的公式）
            // 彻底废弃 finalFormula.replace，改用 AST 嫁接还原
            ASTNode graftedAst = mainAst.substitute(substitutions);
            String finalFormula = graftedAst.toFormulaString();

            return new DynamicData((float) resultValue, new ExpressionData(finalFormula, mergedBindings));

        } catch (Exception e) {
            System.err.println("[MathExpression] Compute Error: " + e.getMessage());
            return new DynamicData(0.0f, ExpressionData.ZERO);
        }
    }
}