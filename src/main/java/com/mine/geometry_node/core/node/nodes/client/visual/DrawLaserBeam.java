package com.mine.geometry_node.core.node.nodes.client.visual;

import com.mine.geometry_node.core.execution.ExecutionContext;
import com.mine.geometry_node.core.execution.ExecutionResult;
import com.mine.geometry_node.core.execution.datatypes.ExpressionData;
import com.mine.geometry_node.core.node.nodes.*;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

public class DrawLaserBeam extends BaseNode {

    public static final String TYPE_ID = "draw_laser_beam";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.draw_laser_beam"))
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.START_POS.toInput(), null, UIHint.VECTOR, null, null))
                .addRow(new PortRow(StandardPorts.END_POS.toInput(), null, UIHint.VECTOR, null, null))
                .addRow(new PortRow(StandardPorts.COLOR.toInput(), null, UIHint.INPUT, null, null))
                .addRow(new PortRow(StandardPorts.SIZE_1.toInput(), null, UIHint.INPUT, null, null))
                .addRow(new PortRow(StandardPorts.TICK.toInput(), null, UIHint.INPUT, null, null))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        // 1. 获取基础物理数据 (20FPS的死坐标和死数值)
        Vec3 baseStart = getInput(context, StandardPorts.START_POS.getId(), Vec3.class);
        Vec3 baseEnd = getInput(context, StandardPorts.END_POS.getId(), Vec3.class);
        Integer color = getInput(context, StandardPorts.COLOR.getId(), Integer.class);
        Float baseSize = getInput(context, StandardPorts.SIZE_1.getId(), Float.class);
        Integer duration = getInput(context, StandardPorts.TICK.getId(), Integer.class);

        if (baseStart == null) baseStart = Vec3.ZERO;
        if (baseEnd == null) baseEnd = Vec3.ZERO;
        if (color == null) color = 0xFFFFFFFF;
        if (baseSize == null) baseSize = 0.5f;
        if (duration == null) duration = 20;

        // 2. 准备协议字典
        Map<String, String> expressions = new HashMap<>();
        Map<String, String> bindings = new HashMap<>();

        // 3. 处理动态粗细 (标量)
        ExpressionData sizeExpr = getInput(context, StandardPorts.SIZE_1.getId(), ExpressionData.class);
        if (sizeExpr != null && sizeExpr.formula() != null && !sizeExpr.formula().isEmpty() && !sizeExpr.formula().equals("0")) {
            expressions.put("size", sizeExpr.formula());
            bindings.putAll(sizeExpr.bindings());
        }

        // 4. 处理动态起点和终点 (矢量解包)
        ExpressionData startExpr = getInput(context, StandardPorts.START_POS.getId(), ExpressionData.class);
        extractVec3(startExpr, "start", expressions, bindings);

        ExpressionData endExpr = getInput(context, StandardPorts.END_POS.getId(), ExpressionData.class);
        extractVec3(endExpr, "end", expressions, bindings);

        // 5. 广播
        context.broadcastDynamicVisual("laser_beam", -1, baseStart, -1, baseEnd, color, baseSize, duration, expressions, bindings);

        return next(StandardPorts.FLOW_OUT.getId());
    }

    /**
     * 专属协议解析器：把 "vec3(X, Y, Z)" 切割成三个轴的偏移公式
     */
    private void extractVec3(ExpressionData expr, String axisPrefix, Map<String, String> expressions, Map<String, String> bindings) {
        if (expr != null && expr.formula() != null) {
            String f = expr.formula().trim();
            if (f.startsWith("vec3(") && f.endsWith(")")) {
                String[] parts = f.substring(5, f.length() - 1).split(",");
                if (parts.length >= 3) {
                    // 只把有实际内容的公式发给客户端，过滤掉纯粹的 "0" 减少计算开销
                    if (!"0".equals(parts[0].trim())) expressions.put(axisPrefix + "X", parts[0].trim());
                    if (!"0".equals(parts[1].trim())) expressions.put(axisPrefix + "Y", parts[1].trim());
                    if (!"0".equals(parts[2].trim())) expressions.put(axisPrefix + "Z", parts[2].trim());

                    bindings.putAll(expr.bindings());
                }
            }
        }
    }
}