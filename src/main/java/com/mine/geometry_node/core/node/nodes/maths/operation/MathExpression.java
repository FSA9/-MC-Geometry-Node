package com.mine.geometry_node.core.node.nodes.maths.operation;

import com.mine.geometry_node.core.execution.ExecutionContext;
import com.mine.geometry_node.core.execution.datatypes.DynamicData;
import com.mine.geometry_node.core.execution.datatypes.ExpressionData;
import com.mine.geometry_node.core.node.NodeData;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.meta.StaticKeys;
import com.mine.geometry_node.core.node.meta.SchemaKeys;
import com.mine.geometry_node.core.node.nodes.*;
import com.mine.geometry_node.core.node.port.*;
import com.mine.geometry_node.core.utils.ASTNode;
import com.mine.geometry_node.core.utils.ASTNodes;
import com.mine.geometry_node.core.utils.ExpressionCompiler;
import com.mine.geometry_node.core.utils.VariableRegistry;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.Map;

public class MathExpression extends BaseNode {

    public static final String TYPE_ID = "math_expression";

    // 用于缓存编译后的树与它的专属注册表
    private record CachedAST(ASTNode node, VariableRegistry registry) {}

    @Override
    public NodeDef getDefaultDefinition() {
        return buildDef(1);
    }

    @Override
    public NodeDef getDefinition(NodeData instanceData) {
        int portCount = 1;

        if (instanceData != null && instanceData.inputs.containsKey(StaticKeys.DYNAMIC_BRANCH_INPUT_COUNT.id())) {
            Object countObj = instanceData.inputs.get(StaticKeys.DYNAMIC_BRANCH_INPUT_COUNT.id());
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

            PortDef dynamicPort = new PortDef(portId, Component.literal(String.valueOf(varName)), PortType.FLOAT, 0.0f, false);

            builder.addRow(new PortRow(
                    dynamicPort, null, UIHint.DEFAULT, null,
                    Map.of(PortMetaKeys.IS_DYNAMIC, true, PortMetaKeys.DYNAMIC_INDEX, i)
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

        // 1. 获取或编译主表达式的 AST 和 Registry
        String cacheKey = "AST_" + context.getCurrentNodeId() + "_" + mainExpr.hashCode();
        CachedAST cache = (CachedAST) context.getTempData(cacheKey);

        if (cache == null) {
            VariableRegistry newRegistry = new VariableRegistry();
            ASTNode newNode = ExpressionCompiler.compile(mainExpr, newRegistry);
            cache = new CachedAST(newNode, newRegistry);
            context.setTempData(cacheKey, cache);
        }

        ASTNode mainAst = cache.node();
        VariableRegistry mainRegistry = cache.registry();
        Map<String, Integer> indexMapping = mainRegistry.getMapping();

        // 2. 准备服务端求值用的极速数组
        double[] evalVars = new double[mainRegistry.getVarCount()];

        // 写入 tick (如果表达式里写了的话)
        int tickIdx = indexMapping.getOrDefault("tick", -1);
        if (tickIdx >= 0) {
            double serverTick = context.getLevel() != null ? context.getLevel().getGameTime() : 0.0;
            evalVars[tickIdx] = serverTick;
        }

        Map<String, ASTNode> substitutions = new HashMap<>();
        Map<String, String> mergedBindings = new HashMap<>();

        int portCount = 1;
        Object countObj = context.getStaticInput(StaticKeys.DYNAMIC_BRANCH_INPUT_COUNT.id());
        if (countObj instanceof Number n) {
            portCount = n.intValue();
        } else if (countObj instanceof String s) {
            try { portCount = Integer.parseInt(s); } catch (Exception ignored) {}
        }
        portCount = Math.max(1, Math.min(portCount, 26));

        // 3. 处理动态输入分支
        for (int i = 1; i <= portCount; i++) {
            char varName = (char) ('A' + (i - 1));
            String portId = "var_" + i;
            String varKey = String.valueOf(varName);

            Float val = getInput(context, portId, Float.class);
            double numVal = val != null ? val.doubleValue() : 0.0;

            // 如果该变量在表达式中存在，填入对应的数组索引中
            int varIdx = indexMapping.getOrDefault(varKey, -1);
            if (varIdx >= 0) {
                evalVars[varIdx] = numVal;
            }

            ExpressionData inputExpr = getInput(context, portId, ExpressionData.class);

            if (inputExpr != null && inputExpr.formula() != null && !inputExpr.formula().isEmpty() && !inputExpr.formula().equals("0")) {
                VariableRegistry dummyRegistry = new VariableRegistry();
                ASTNode subAst = ExpressionCompiler.compile(inputExpr.formula(), dummyRegistry);
                substitutions.put(varKey, subAst);
                mergedBindings.putAll(inputExpr.bindings());
            } else {
                substitutions.put(varKey, new ASTNodes.ConstantNode(numVal));
            }
        }

        try {
            // 4. 服务端求值 与 协议字符串嫁接
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