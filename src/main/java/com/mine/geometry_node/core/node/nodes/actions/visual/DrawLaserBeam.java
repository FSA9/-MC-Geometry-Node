package com.mine.geometry_node.core.node.nodes.actions.visual;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.engine.graph.expression.ExpressionData;
import com.mine.geometry_node.core.engine.spatial.SpatialAnchor;
import com.mine.geometry_node.core.engine.spatial.VisualSpatialAnchors;
import com.mine.geometry_node.core.node.definition.node.NodeComment;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.PortDef;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.nodes.*;
import com.mine.geometry_node.core.node.util.AnchorNodeSupport;
import com.mine.geometry_node.core.node.util.VisualTransformInputs;
import com.mine.geometry_node.core.node.definition.port.PortType;
import com.mine.geometry_node.core.node.value.color.ColorValue;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

public class DrawLaserBeam extends BaseNode {

    public static final String TYPE_ID = "draw_laser_beam";
    public static final PortDef START_PORT = PortDef.create("start_world_offset","geometry_node.port.start_world_offset",PortType.XYZ,Vec3.ZERO).liveExpression();
    public static final PortDef END_PORT = PortDef.create("end_world_offset","geometry_node.port.end_world_offset",PortType.XYZ,Vec3.ZERO).liveExpression();
    public static final PortDef START_LOCAL_PORT = PortDef.create("start_local_offset", "geometry_node.port.start_local_offset", PortType.XYZ, Vec3.ZERO).liveExpression();
    public static final PortDef END_LOCAL_PORT = PortDef.create("end_local_offset", "geometry_node.port.end_local_offset", PortType.XYZ, Vec3.ZERO).liveExpression();
    public static final PortDef SIZE_PORT = StandardPorts.SIZE_1.toInput(0.5f).liveExpression();

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.draw_laser_beam"))
                .comment(NodeComment.builder(TYPE_ID)
                        .text("summary")
                        .output(StandardPorts.FLOW_OUT, "flow_out")
                        .input(StandardPorts.FLOW_IN, "flow_in")
                        .input(StandardPorts.START_ANCHOR, "geometry_node.anchor.help")
                        .input(StandardPorts.END_ANCHOR, "geometry_node.anchor.help")
                        .input(START_PORT.id(), "geometry_node.transform.world_offset.help")
                        .input(START_LOCAL_PORT.id(), "geometry_node.transform.local_offset.help")
                        .input(END_PORT.id(), "geometry_node.transform.world_offset.help")
                        .input(END_LOCAL_PORT.id(), "geometry_node.transform.local_offset.help")
                        .input(StandardPorts.COLOR, "color")
                        .input(StandardPorts.SIZE_1, "size_1")
                        .input(StandardPorts.TICK, "tick")
                        .build())
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(AnchorNodeSupport.row(StandardPorts.START_ANCHOR))
                .addRow(AnchorNodeSupport.row(StandardPorts.END_ANCHOR))

                .addPassthroughInput(START_PORT, UIHint.VECTOR)
                .addPassthroughInput(END_PORT, UIHint.VECTOR)
                .addRow(PortRow.passthrough(START_LOCAL_PORT, UIHint.VECTOR,null,null))
                .addRow(PortRow.passthrough(END_LOCAL_PORT, UIHint.VECTOR,null,null))
                .addPassthroughInput(StandardPorts.COLOR.toInput(), UIHint.INPUT)
                .addPassthroughInput(SIZE_PORT, UIHint.INPUT)
                .addPassthroughInput(StandardPorts.TICK.toInput(), UIHint.INPUT, null, Map.of(PortMetaKeys.NUMERIC_MIN, 0))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        SpatialAnchor startAnchor = AnchorNodeSupport.capture(context, StandardPorts.START_ANCHOR);
        SpatialAnchor endAnchor = AnchorNodeSupport.capture(context, StandardPorts.END_ANCHOR);
        if (startAnchor == null || endAnchor == null
                || !VisualSpatialAnchors.canBroadcast(startAnchor, context.getLevel())
                || !VisualSpatialAnchors.canBroadcast(endAnchor, context.getLevel())) {
            return next(StandardPorts.FLOW_OUT.getId());
        }
        // 1. 获取基础物理数据 (20FPS的死坐标和死数值)
        Vec3 baseStart = getInput(context, START_PORT.id(), Vec3.class);
        Vec3 baseEnd = getInput(context, END_PORT.id(), Vec3.class);
        ColorValue color = getInput(context, StandardPorts.COLOR.getId(), ColorValue.class);
        Float baseSize = getInput(context, StandardPorts.SIZE_1.getId(), Float.class);
        Integer duration = getInput(context, StandardPorts.TICK.getId(), Integer.class);

        if (baseStart == null) baseStart = Vec3.ZERO;
        if (baseEnd == null) baseEnd = Vec3.ZERO;
        int argb = color != null ? color.toArgb() : ColorValue.WHITE.toArgb();
        if (baseSize == null) baseSize = 0.5f;
        if (duration == null) duration = 20;

        // 2. 准备协议字典
        Map<String, ExpressionData> expressions = new LinkedHashMap<>();

        // 3. 处理动态粗细 (标量)
        putInputExpression(context, StandardPorts.SIZE_1.getId(), "size", expressions);

        // 4. 处理动态起点和终点 (矢量解包)
        putInputExpression(context, START_PORT.id(), "start", expressions);
        putInputExpression(context, END_PORT.id(), "end", expressions);

        // 5. 组装动态数据夹 (NBT)
        net.minecraft.nbt.CompoundTag extraData = new net.minecraft.nbt.CompoundTag();
        VisualSpatialAnchors.write(extraData, "startAnchor", startAnchor, context.getLevel());
        VisualSpatialAnchors.write(extraData, "endAnchor", endAnchor, context.getLevel());
        putInputExpression(context, StandardPorts.START_ANCHOR.getId(), "startAnchor", expressions);
        putInputExpression(context, StandardPorts.END_ANCHOR.getId(), "endAnchor", expressions);

        extraData.putDouble("startX", baseStart.x);
        extraData.putDouble("startY", baseStart.y);
        extraData.putDouble("startZ", baseStart.z);

        extraData.putDouble("endX", baseEnd.x);
        extraData.putDouble("endY", baseEnd.y);
        extraData.putDouble("endZ", baseEnd.z);

        VisualTransformInputs.write(context, extraData, expressions, START_LOCAL_PORT, "startLocal");
        VisualTransformInputs.write(context, extraData, expressions, END_LOCAL_PORT, "endLocal");
        extraData.putFloat("size", baseSize);

        // 6. 广播
        context.broadcastDynamicVisual("laser_beam", argb, duration, expressions, extraData);

        return next(StandardPorts.FLOW_OUT.getId());
    }
}
