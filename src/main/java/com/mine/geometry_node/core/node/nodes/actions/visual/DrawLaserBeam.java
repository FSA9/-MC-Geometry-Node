package com.mine.geometry_node.core.node.nodes.actions.visual;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.NodeComment;
import com.mine.geometry_node.core.node.value.ColorValue;
import com.mine.geometry_node.core.node.value.ExpressionData;
import com.mine.geometry_node.core.node.nodes.*;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DrawLaserBeam extends BaseNode {

    public static final String TYPE_ID = "draw_laser_beam";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.draw_laser_beam"))
                .comment(NodeComment.builder(TYPE_ID)
                        .text("summary")
                        .output(StandardPorts.FLOW_OUT, "flow_out")
                        .input(StandardPorts.FLOW_IN, "flow_in")
                        .input(StandardPorts.SOURCE_ENTITY, "source_entity")
                        .input(StandardPorts.TARGET_ENTITY, "target_entity")
                        .input(StandardPorts.START_POS, "start_pos")
                        .input(StandardPorts.END_POS, "end_pos")
                        .input(StandardPorts.COLOR, "color")
                        .input(StandardPorts.SIZE_1, "size_1")
                        .input(StandardPorts.TICK, "tick")
                        .build())
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                // 【修复3】：必须在节点定义里暴露出这两个实体输入端口，否则玩家无法连线
                // 注意：如果你的 StandardPorts 里没有 SOURCE_ENTITY，请去那里注册一下，或者直接用自定义 PortDef
                .addRow(new PortRow(StandardPorts.SOURCE_ENTITY.toInput(), null, UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.TARGET_ENTITY.toInput(), null, UIHint.DEFAULT, null, null))

                .addRow(new PortRow(StandardPorts.START_POS.toInput(), null, UIHint.VECTOR, null, null))
                .addRow(new PortRow(StandardPorts.END_POS.toInput(), null, UIHint.VECTOR, null, null))
                .addRow(new PortRow(StandardPorts.COLOR.toInput(), null, UIHint.INPUT, null, null))
                .addRow(new PortRow(StandardPorts.SIZE_1.toInput(), null, UIHint.INPUT, null, null))
                .addRow(new PortRow(StandardPorts.TICK.toInput(), null, UIHint.INPUT, null,
                        Map.of(PortMetaKeys.NUMERIC_MIN, 0)))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        // 读取实体端口
        Entity sourceEntity = getInput(context, StandardPorts.SOURCE_ENTITY.getId(), Entity.class);
        Entity targetEntity = getInput(context, StandardPorts.TARGET_ENTITY.getId(), Entity.class);

        int sourceId = sourceEntity != null ? sourceEntity.getId() : -1;
        int targetId = targetEntity != null ? targetEntity.getId() : -1;

        // 1. 获取基础物理数据 (20FPS的死坐标和死数值)
        Vec3 baseStart = getInput(context, StandardPorts.START_POS.getId(), Vec3.class);
        Vec3 baseEnd = getInput(context, StandardPorts.END_POS.getId(), Vec3.class);
        ColorValue color = getInput(context, StandardPorts.COLOR.getId(), ColorValue.class);
        Float baseSize = getInput(context, StandardPorts.SIZE_1.getId(), Float.class);
        Integer duration = getInput(context, StandardPorts.TICK.getId(), Integer.class);

        if (baseStart == null) baseStart = Vec3.ZERO;
        if (baseEnd == null) baseEnd = Vec3.ZERO;
        int argb = color != null ? color.toArgb() : ColorValue.WHITE.toArgb();
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

        // 5. 组装动态数据夹 (NBT)
        net.minecraft.nbt.CompoundTag extraData = new net.minecraft.nbt.CompoundTag();
        extraData.putInt("sourceId", sourceId);
        extraData.putInt("targetId", targetId);

        extraData.putDouble("startX", baseStart.x);
        extraData.putDouble("startY", baseStart.y);
        extraData.putDouble("startZ", baseStart.z);

        extraData.putDouble("endX", baseEnd.x);
        extraData.putDouble("endY", baseEnd.y);
        extraData.putDouble("endZ", baseEnd.z);

        extraData.putFloat("size", baseSize);

        // 6. 广播
        context.broadcastDynamicVisual("laser_beam", argb, duration, expressions, bindings, extraData);

        return next(StandardPorts.FLOW_OUT.getId());
    }

    /**
     * 专属协议解析器：把 "vec3(X, Y, Z)" 切割成三个轴的偏移公式
     */
    protected void extractVec3(ExpressionData expr, String prefix, Map<String, String> expressions, Map<String, String> bindings) {
        if (expr == null || expr.formula() == null || expr.formula().isEmpty()) return;

        String f = expr.formula().trim();
        // 【修复1】：必须先验证并剥离 vec3(...) 外壳，拿到 inner！
        if (f.startsWith("vec3(") && f.endsWith(")")) {
            String inner = f.substring(5, f.length() - 1);

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
                expressions.put(prefix + "X", parts.get(0));
                expressions.put(prefix + "Y", parts.get(1));
                expressions.put(prefix + "Z", parts.get(2));
            }
        }

        // 【修复2】：不要忘了把表达式绑定的变量（比如环境里的实体数据）传递下去
        if (expr.bindings() != null) {
            bindings.putAll(expr.bindings());
        }
    }
}
