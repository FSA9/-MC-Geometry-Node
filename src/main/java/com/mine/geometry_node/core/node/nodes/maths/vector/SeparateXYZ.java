package com.mine.geometry_node.core.node.nodes.maths.vector;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.node.value.dynamic.DynamicData;
import com.mine.geometry_node.core.node.value.dynamic.ExpressionData;
import com.mine.geometry_node.core.node.nodes.*;
import com.mine.geometry_node.core.node.port.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public class SeparateXYZ extends BaseNode {

    public static final String TYPE_ID = "separate_xyz";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.MATH, Component.translatable("geometry_node.node.separate_xyz"))
                .addRow(new PortRow(null, StandardPorts.X.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.Y.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.Z.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.VECTOR.toInput(), null, UIHint.VECTOR, null, null))
                .build();
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        // 1. 获取物理层数值
        Vec3 physicalVec = getInput(context, StandardPorts.VECTOR.getId(), Vec3.class);
        float physicalOut = 0f;

        // 识别当前请求的轴端口
        boolean isX = portName.equalsIgnoreCase("x") || portName.equals(StandardPorts.X.getId());
        boolean isY = portName.equalsIgnoreCase("y") || portName.equals(StandardPorts.Y.getId());

        if (physicalVec != null) {
            physicalOut = isX ? (float) physicalVec.x : (isY ? (float) physicalVec.y : (float) physicalVec.z);
        }

        // 2. 获取协议层公式
        ExpressionData inExpr = getInput(context, StandardPorts.VECTOR.getId(), ExpressionData.class);
        ExpressionData outExpr = ExpressionData.ZERO;

        if (inExpr != null && inExpr.formula() != null) {
            String f = inExpr.formula().trim();

            // 检查是否为标准的动态向量协议
            if (f.startsWith("vec3(") && f.endsWith(")")) {
                String inner = f.substring(5, f.length() - 1);

                // 【核心修复】：安全的分量提取算法
                // 通过记录括号层级，确保函数内部的逗号（如 max(a,b)）不会触发错误的切分
                List<String> parts = new ArrayList<>();
                int bracketLevel = 0;
                StringBuilder currentStr = new StringBuilder();

                for (char c : inner.toCharArray()) {
                    if (c == '(') bracketLevel++;
                    else if (c == ')') bracketLevel--;
                    else if (c == ',' && bracketLevel == 0) {
                        parts.add(currentStr.toString().trim());
                        currentStr.setLength(0);
                        continue;
                    }
                    currentStr.append(c);
                }
                parts.add(currentStr.toString().trim());

                if (parts.size() >= 3) {
                    // 根据请求的端口返回对应的分量公式，并透传变量绑定关系
                    String targetFormula = isX ? parts.get(0) : (isY ? parts.get(1) : parts.get(2));
                    outExpr = new ExpressionData(targetFormula, inExpr.bindings());
                }
            } else {
                // 如果输入不是向量协议（例如死坐标或单一变量），则作为标量直接透传公式
                outExpr = new ExpressionData(f, inExpr.bindings());
            }
        }

        // 重新包装为 DynamicData 输出
        return new DynamicData(physicalOut, outExpr);
    }
}