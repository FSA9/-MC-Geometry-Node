package com.mine.geometry_node.core.node.nodes.actions.visual;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.nodes.*;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import com.mine.geometry_node.core.node.value.ColorValue;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.util.Map;

public class DrawDebugLine extends BaseNode {

    public static final String TYPE_ID = "draw_debug_line";

    @Override
    public NodeDef getDefaultDefinition() {
        String comment = """
                绘制一条临时调试直线。
                start_pos/end_pos 使用世界坐标。
                color、size_1、tick 控制颜色、粗细和持续时间。
                只产生视觉效果，不做碰撞、伤害或方块修改。""";

        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.draw_debug_line"))
                .comment(comment)
                // 执行流：输入与输出
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                // 核心数据端口：使用 PortDef.create 创建具有默认值的自定义语义端口
                .addRow(new PortRow(StandardPorts.START_POS.toInput(), null, UIHint.VECTOR, null, null))
                .addRow(new PortRow(StandardPorts.END_POS.toInput(), null, UIHint.VECTOR, null, null))
                // 渲染参数端口：颜色、粗细、持续时间(Tick)
                .addRow(new PortRow(StandardPorts.COLOR.toInput(), null, UIHint.INPUT, null, null))
                .addRow(new PortRow(StandardPorts.SIZE_1.toInput(), null, UIHint.INPUT, null, null))
                .addRow(new PortRow(StandardPorts.TICK.toInput(), null, UIHint.INPUT, null,
                        Map.of(PortMetaKeys.NUMERIC_MIN, 0)))
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

        context.broadcastVisual("debug_line", -1, startPos, -1, endPos, argb, size, duration);

        return next(StandardPorts.FLOW_OUT.getId());
    }
}
