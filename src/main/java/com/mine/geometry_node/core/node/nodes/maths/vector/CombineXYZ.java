package com.mine.geometry_node.core.node.nodes.maths.vector;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.graph.expression.ExpressionBinding;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.value.dynamic.DynamicData;
import com.mine.geometry_node.core.engine.graph.expression.ExpressionData;
import com.mine.geometry_node.core.node.nodes.*;
import com.mine.geometry_node.core.node.definition.port.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

public class CombineXYZ extends BaseNode {

    public static final String TYPE_ID = "combine_xyz";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.MATH, Component.translatable("geometry_node.node.combine_xyz"))
                .addRow(new PortRow(null, StandardPorts.VECTOR.toOutput(), UIHint.DEFAULT, null, null))
                .addPassthroughInput(StandardPorts.X.toInput(), UIHint.INPUT, null, null)
                .addPassthroughInput(StandardPorts.Y.toInput(), UIHint.INPUT, null, null)
                .addPassthroughInput(StandardPorts.Z.toInput(), UIHint.INPUT, null, null)
                .build();
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        if (!StandardPorts.VECTOR.getId().equals(portName)) return null;

        // 1. 物理层：获取三个轴的当前死数值
        Float xVal = getInput(context, StandardPorts.X.getId(), Float.class);
        Float yVal = getInput(context, StandardPorts.Y.getId(), Float.class);
        Float zVal = getInput(context, StandardPorts.Z.getId(), Float.class);
        Vec3 physicalVec = new Vec3(xVal != null ? xVal : 0, yVal != null ? yVal : 0, zVal != null ? zVal : 0);

        // 2. 协议层：获取三个轴的表达式
        ExpressionData xExpr = getInput(context, StandardPorts.X.getId(), ExpressionData.class);
        ExpressionData yExpr = getInput(context, StandardPorts.Y.getId(), ExpressionData.class);
        ExpressionData zExpr = getInput(context, StandardPorts.Z.getId(), ExpressionData.class);

        // 3. 组装 vec3 协议文本并合并绑定关系
        Map<String, ExpressionBinding> mergedBindings = new HashMap<>();
        String fx = "0", fy = "0", fz = "0";

        if (xExpr != null) { fx = xExpr.formula(); mergedBindings.putAll(xExpr.bindings()); }
        if (yExpr != null) { fy = yExpr.formula(); mergedBindings.putAll(yExpr.bindings()); }
        if (zExpr != null) { fz = zExpr.formula(); mergedBindings.putAll(zExpr.bindings()); }

        return new DynamicData(physicalVec, ExpressionData.vector(fx, fy, fz, mergedBindings));
    }
}
