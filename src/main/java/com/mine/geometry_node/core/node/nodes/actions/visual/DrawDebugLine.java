package com.mine.geometry_node.core.node.nodes.actions.visual;

import com.mine.geometry_node.core.execution.ExecutionContext;
import com.mine.geometry_node.core.execution.ExecutionResult;
import com.mine.geometry_node.core.node.nodes.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

public class DrawDebugLine extends BaseNode {

    public static final String TYPE_ID = "draw_debug_line";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.draw_debug_line"))
                // 执行流：输入与输出
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                // 核心数据端口：使用 PortDef.create 创建具有默认值的自定义语义端口
                .addRow(new PortRow(PortDef.create("start_pos", "geometry_node.port.start_pos", PortType.XYZ, Vec3.ZERO), null, UIHint.INPUT, null, null))
                .addRow(new PortRow(PortDef.create("end_pos", "geometry_node.port.end_pos", PortType.XYZ, Vec3.ZERO), null, UIHint.INPUT, null, null))
                // 渲染参数端口：颜色 (ARGB)、粗细、持续时间(Tick)
                .addRow(new PortRow(PortDef.create("color", "geometry_node.port.color", PortType.INTEGER, 0xFFFF0000), null, UIHint.INPUT, null, null)) // 默认纯红色
                .addRow(new PortRow(PortDef.create("size", "geometry_node.port.size", PortType.FLOAT, 0.05f), null, UIHint.INPUT, null, null)) // 默认粗细
                .addRow(new PortRow(PortDef.create("duration", "geometry_node.port.duration", PortType.INTEGER, 100), null, UIHint.INPUT, null, null)) // 默认 5 秒 (100 ticks)
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        // 1. 从连线或静态输入框中获取数据 (利用了你现有的 TypeConverter)
        Vec3 startPos = getInput(context, "start_pos", Vec3.class);
        Vec3 endPos = getInput(context, "end_pos", Vec3.class);
        Integer color = getInput(context, "color", Integer.class);
        Float size = getInput(context, "size", Float.class);
        Integer duration = getInput(context, "duration", Integer.class);

        // 2. 容错处理 (防止空指针异常导致虚拟机宕机)
        if (startPos == null) startPos = Vec3.ZERO;
        if (endPos == null) endPos = Vec3.ZERO;
        if (color == null) color = 0xFFFFFFFF; // 默认白色
        if (size == null) size = 0.05f;
        if (duration == null) duration = 20;   // 默认1秒

        // 3. [核心] 调用我们在 ExecutionContext 中新增的网络广播方法
        // 特效标识设为 "debug_line"，实体ID传 -1 表示不绑定实体，直接使用死坐标
        context.broadcastVisual("debug_line", -1, startPos, -1, endPos, color, size, duration);

        // 4. 流转到下一个节点
        return next(StandardPorts.FLOW_OUT.getId());
    }
}