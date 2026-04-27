package com.mine.geometry_node.core.node.nodes.maths.vector;

import com.mine.geometry_node.core.execution.ExecutionContext;
import com.mine.geometry_node.core.execution.datatypes.DynamicData;
import com.mine.geometry_node.core.execution.datatypes.ExpressionData;
import com.mine.geometry_node.core.node.nodes.*;
import com.mine.geometry_node.core.node.port.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

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
        Vec3 physicalVec = getInput(context, StandardPorts.VECTOR.getId(), Vec3.class);
        float physicalOut = 0f;

        // 【修复1】：忽略大小写，并兼容标准端口 ID
        boolean isX = portName.equalsIgnoreCase("x") || portName.equals(StandardPorts.X.getId());
        boolean isY = portName.equalsIgnoreCase("y") || portName.equals(StandardPorts.Y.getId());

        if (physicalVec != null) {
            physicalOut = isX ? (float) physicalVec.x : (isY ? (float) physicalVec.y : (float) physicalVec.z);
        }

        ExpressionData inExpr = getInput(context, StandardPorts.VECTOR.getId(), ExpressionData.class);
        ExpressionData outExpr = ExpressionData.ZERO;

        if (inExpr != null && inExpr.formula() != null) {
            String f = inExpr.formula().trim();
            if (f.startsWith("vec3(") && f.endsWith(")")) {
                String[] parts = f.substring(5, f.length() - 1).split(",");
                if (parts.length == 3) {
                    // 【修复2】：同步使用安全的布尔判断提取公式
                    String targetFormula = isX ? parts[0].trim() : (isY ? parts[1].trim() : parts[2].trim());
                    outExpr = new ExpressionData(targetFormula, inExpr.bindings());
                }
            } else {
                outExpr = new ExpressionData(f, inExpr.bindings());
            }
        }
        return new DynamicData(physicalOut, outExpr);
    }
}