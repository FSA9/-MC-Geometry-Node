package com.mine.geometry_node.core.node.nodes.actions.visual;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.engine.graph.expression.ExpressionData;
import com.mine.geometry_node.core.node.definition.node.NodeComment;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.nodes.*;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.PortDef;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import com.mine.geometry_node.core.node.value.color.ColorValue;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.Map;

public class DrawDebugLine extends BaseNode {

    public static final String TYPE_ID = "draw_debug_line";
    public static final PortDef START_PORT = StandardPorts.START_POS.toInput(Vec3.ZERO).liveExpression();
    public static final PortDef END_PORT = StandardPorts.END_POS.toInput(Vec3.ZERO).liveExpression();
    public static final PortDef SIZE_PORT = StandardPorts.SIZE_1.toInput(0.01F).liveExpression();

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.draw_debug_line"))
                .comment(NodeComment.builder(TYPE_ID)
                        .text("summary")
                        .output(StandardPorts.FLOW_OUT, "flow_out")
                        .input(StandardPorts.FLOW_IN, "flow_in")
                        .input(StandardPorts.START_POS, "start_pos")
                        .input(StandardPorts.END_POS, "end_pos")
                        .input(StandardPorts.COLOR, "color")
                        .input(StandardPorts.SIZE_1, "size_1")
                        .input(StandardPorts.TICK, "tick")
                        .build())
                // 执行流：输入与输出
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                // 核心数据端口：使用 PortDef.create 创建具有默认值的自定义语义端口
                .addPassthroughInput(START_PORT, UIHint.VECTOR)
                .addPassthroughInput(END_PORT, UIHint.VECTOR)
                // 渲染参数端口：颜色、粗细、持续时间(Tick)
                .addPassthroughInput(StandardPorts.COLOR.toInput(), UIHint.INPUT)
                .addPassthroughInput(SIZE_PORT, UIHint.INPUT)
                .addPassthroughInput(StandardPorts.TICK.toInput(), UIHint.INPUT, null, Map.of(PortMetaKeys.NUMERIC_MIN, 0))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        Vec3 startPos = getInput(context, StandardPorts.START_POS.getId(), Vec3.class);
        Vec3 endPos = getInput(context, StandardPorts.END_POS.getId(), Vec3.class);
        ColorValue color = getInput(context, StandardPorts.COLOR.getId(), ColorValue.class);
        Float size = getInput(context, StandardPorts.SIZE_1.getId(), Float.class);
        Integer duration = getInput(context, StandardPorts.TICK.getId(), Integer.class);
        int argb = color != null ? color.toArgb() : ColorValue.WHITE.toArgb();
        Vec3 baseStart = startPos != null ? startPos : Vec3.ZERO;
        Vec3 baseEnd = endPos != null ? endPos : Vec3.ZERO;
        float baseSize = size != null ? size : 0.01F;
        int durationTicks = duration != null ? duration : 20;

        Map<String, ExpressionData> expressions = new LinkedHashMap<>();
        putInputExpression(context, StandardPorts.START_POS.getId(), "start", expressions);
        putInputExpression(context, StandardPorts.END_POS.getId(), "end", expressions);
        putInputExpression(context, StandardPorts.SIZE_1.getId(), "size", expressions);

        net.minecraft.nbt.CompoundTag extraData = new net.minecraft.nbt.CompoundTag();
        extraData.putInt("sourceId", -1);
        extraData.putInt("targetId", -1);
        extraData.putDouble("startX", baseStart.x);
        extraData.putDouble("startY", baseStart.y);
        extraData.putDouble("startZ", baseStart.z);
        extraData.putDouble("endX", baseEnd.x);
        extraData.putDouble("endY", baseEnd.y);
        extraData.putDouble("endZ", baseEnd.z);
        extraData.putFloat("size", baseSize);

        context.broadcastDynamicVisual("debug_line", argb, durationTicks, expressions, extraData);

        return next(StandardPorts.FLOW_OUT.getId());
    }
}
