package com.mine.geometry_node.core.node.nodes.actions.visual;

import com.mine.geometry_node.core.execution.ExecutionContext;
import com.mine.geometry_node.core.execution.ExecutionResult;
import com.mine.geometry_node.core.node.nodes.*;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;

public class DrawRayBeam extends BaseNode {

    public static final String TYPE_ID = "draw_ray_beam";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.draw_ray_beam"))
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))

                // 核心输入源
                .addRow(new PortRow(StandardPorts.SOURCE_ENTITY.toInput(), null, UIHint.DEFAULT, null, null))

                // 偏移量与射线属性
                .addRow(new PortRow(StandardPorts.START_POS.toInput(), null, UIHint.VECTOR, null, null)) // 位置偏移
                .addRow(new PortRow(StandardPorts.PITCH.toInput(), null, UIHint.INPUT, null, null))  // 俯仰角偏移
                .addRow(new PortRow(StandardPorts.YAW.toInput(), null, UIHint.INPUT, null, null))    // 偏航角偏移

                .addRow(new PortRow(StandardPorts.DIST.toInput(), null, UIHint.INPUT, null, null))  // 射线长度
                .addRow(new PortRow(StandardPorts.COLOR.toInput(), null, UIHint.INPUT, null, null))
                .addRow(new PortRow(StandardPorts.RADIUS.toInput(), null, UIHint.INPUT, null, null)) // 半径/粗细
                .addRow(new PortRow(StandardPorts.TICK.toInput(), null, UIHint.INPUT, null, null))     // 存活时间
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        Entity sourceEntity = getInput(context, StandardPorts.SOURCE_ENTITY.getId(), Entity.class);
        int sourceId = sourceEntity != null ? sourceEntity.getId() : -1;

        Vec3 posOffset = getInput(context, StandardPorts.START_POS.getId(), Vec3.class);
        Float pitchOffset = getInput(context, StandardPorts.PITCH.getId(), Float.class);
        Float yawOffset = getInput(context, StandardPorts.YAW.getId(), Float.class);
        Float length = getInput(context, StandardPorts.DIST.getId(), Float.class);
        Integer color = getInput(context, StandardPorts.COLOR.getId(), Integer.class);
        Float radius = getInput(context, StandardPorts.RADIUS.getId(), Float.class);
        Integer duration = getInput(context, StandardPorts.TICK.getId(), Integer.class);

        if (posOffset == null) posOffset = Vec3.ZERO;
        if (pitchOffset == null) pitchOffset = 0.0f;
        if (yawOffset == null) yawOffset = 0.0f;
        if (length == null) length = 20.0f;
        if (color == null) color = 0xFFFFFFFF;
        if (radius == null) radius = 0.1f;
        if (duration == null) duration = 20;

        // 打包静态数据到 ExtraData (由于是纯视觉跟随，这里不使用动态 AST，直接利用客户端插值)
        CompoundTag extraData = new CompoundTag();
        extraData.putInt("sourceId", sourceId);

        extraData.putDouble("offX", posOffset.x);
        extraData.putDouble("offY", posOffset.y);
        extraData.putDouble("offZ", posOffset.z);

        extraData.putFloat("offPitch", pitchOffset);
        extraData.putFloat("offYaw", yawOffset);

        extraData.putFloat("length", length);
        extraData.putFloat("radius", radius);

        // 发送网络包给客户端
        context.broadcastDynamicVisual("ray_beam", color, duration, new HashMap<>(), new HashMap<>(), extraData);

        return next(StandardPorts.FLOW_OUT.getId());
    }
}