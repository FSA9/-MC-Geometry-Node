package com.mine.geometry_node.core.node.nodes.maths.vector;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.value.dynamic.DynamicData;
import com.mine.geometry_node.core.engine.graph.expression.ExpressionData;
import com.mine.geometry_node.core.node.nodes.*;
import com.mine.geometry_node.core.node.definition.port.*;
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

        if (inExpr != null) {
            int component = isX ? 0 : isY ? 1 : 2;
            outExpr = ExpressionData.scalar(inExpr.component(component), inExpr.bindings());
        }

        // 重新包装为 DynamicData 输出
        return new DynamicData(physicalOut, outExpr);
    }
}
