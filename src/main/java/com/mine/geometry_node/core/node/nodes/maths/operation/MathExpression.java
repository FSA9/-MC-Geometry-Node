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
        return buildDef(1);
    }

    @Override
    public NodeDef getDefinition(NodeData instanceData) {
        int portCount = 1;

        if (instanceData != null && instanceData.properties.containsKey(PropertyKeys.DYNAMIC_BRANCH_INPUT_COUNT.id())) {
            Object countObj = instanceData.properties.get(PropertyKeys.DYNAMIC_BRANCH_INPUT_COUNT.id());
            if (countObj instanceof Number n) {
                portCount = n.intValue();
            } else if (countObj instanceof String s) {
                try { portCount = Integer.parseInt(s); } catch (NumberFormatException ignored) {}
            }
        }

        portCount = Math.max(1, Math.min(portCount, 26));
        return buildDef(portCount);
    }

    private NodeDef buildDef(int portCount) {
        String comment = """
                计算自定义数学表达式。
                支持运算符: +, -, *, /, ^ (幂)
                支持函数: sin, cos, tan, sqrt, abs
                支持变量: A-Z (动态输入端口)""";

        NodeDef.Builder builder = NodeDef.builder(TYPE_ID, NodeType.MATH, Component.translatable("geometry_node.node.math_expression"))
                .comment(comment)
                .addMeta(SchemaKeys.MAX_DYNAMIC_INPUT, 26);

        builder.addRow(new PortRow(null, StandardPorts.VALUE.toOutput(), UIHint.DEFAULT, null, null));
        builder.addRow(new PortRow(StandardPorts.EXPRESSION.toInput(), null, UIHint.INPUT, null, null));

        for (int i = 1; i <= portCount; i++) {
            char varName = (char) ('A' + (i - 1));
            String portId = "var_" + i;

            PortDef dynamicPort = new PortDef(portId, Component.literal(String.valueOf(varName)), PortType.FLOAT, 0.0f);

            builder.addRow(new PortRow(
                    dynamicPort,
                    null,
                    UIHint.DEFAULT,
                    null,
                    Map.of(
                            PortMetaKeys.IS_DYNAMIC, true,
                            PortMetaKeys.DYNAMIC_INDEX, i
                    )
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

        String cacheKey = "AST_" + context.getCurrentNodeId() + "_" + mainExpr.hashCode();
        ASTNode mainAst = (ASTNode) context.getTempData(cacheKey);
        if (mainAst == null) {
            mainAst = ExpressionCompiler.compile(mainExpr);
            context.setTempData(cacheKey, mainAst);
        }

        Map<String, Double> evalVars = new HashMap<>();
        Map<String, ASTNode> substitutions = new HashMap<>();
        Map<String, String> mergedBindings = new HashMap<>();

        double serverTick = context.getLevel() != null ? context.getLevel().getGameTime() : 0.0;
        evalVars.put("tick", serverTick);

        int portCount = 1;
        Object countObj = context.getNodeProperty(PropertyKeys.DYNAMIC_BRANCH_INPUT_COUNT.id());
        if (countObj instanceof Number n) {
            portCount = n.intValue();
        } else if (countObj instanceof String s) {
            try { portCount = Integer.parseInt(s); } catch (Exception ignored) {}
        }
        portCount = Math.max(1, Math.min(portCount, 26));

        for (int i = 1; i <= portCount; i++) {
            char varName = (char) ('A' + (i - 1));
            String portId = "var_" + i;

            Float val = getInput(context, portId, Float.class);
            double numVal = val != null ? val.doubleValue() : 0.0;
            evalVars.put(String.valueOf(varName), numVal);

            ExpressionData inputExpr = getInput(context, portId, ExpressionData.class);

            if (inputExpr != null && inputExpr.formula() != null && !inputExpr.formula().isEmpty() && !inputExpr.formula().equals("0")) {
                ASTNode subAst = ExpressionCompiler.compile(inputExpr.formula());
                substitutions.put(String.valueOf(varName), subAst);
                mergedBindings.putAll(inputExpr.bindings());
            } else {
                substitutions.put(String.valueOf(varName), new ASTNodes.ConstantNode(numVal));
            }
        }

        try {
            double resultValue = mainAst.evaluate(evalVars);
            ASTNode graftedAst = mainAst.substitute(substitutions);
            String finalFormula = graftedAst.toFormulaString();
            return new DynamicData((float) resultValue, new ExpressionData(finalFormula, mergedBindings));
        } catch (Exception e) {
            System.err.println("[MathExpression] Compute Error: " + e.getMessage());
            return new DynamicData(0.0f, ExpressionData.ZERO);
        }
    }
}