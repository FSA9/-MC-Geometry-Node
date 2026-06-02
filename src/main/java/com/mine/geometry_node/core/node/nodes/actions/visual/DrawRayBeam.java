package com.mine.geometry_node.core.node.nodes.actions.visual;

import com.mine.geometry_node.core.engine.blueprint.execution.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.execution.ExecutionResult;
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
                .addRow(new PortRow(StandardPorts.SOURCE_ENTITY.toInput(), null, UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.START_POS.toInput(), null, UIHint.VECTOR, null, null))
                .addRow(new PortRow(StandardPorts.PITCH.toInput(0.0f), null, UIHint.INPUT, null, null))
                .addRow(new PortRow(StandardPorts.YAW.toInput(0.0f), null, UIHint.INPUT, null, null))
                .addRow(new PortRow(StandardPorts.DIST.toInput(20.0f), null, UIHint.INPUT, null, null))
                .addRow(new PortRow(StandardPorts.COLOR.toInput(-1), null, UIHint.INPUT, null, null))
                .addRow(new PortRow(StandardPorts.RADIUS.toInput(0.1f), null, UIHint.INPUT, null, null))
                .addRow(new PortRow(StandardPorts.TICK.toInput(2), null, UIHint.INPUT, null, null))

                // 【新增】：把物理检测规则也传进去
                .addRow(new PortRow(StandardPorts.PENETRATE_SOLID.toInput(false), null, UIHint.CHECKBOX, null, null))
                .addRow(new PortRow(StandardPorts.PENETRATE_TRANS.toInput(true), null, UIHint.CHECKBOX, null, null))
                .addRow(new PortRow(StandardPorts.PENETRATE_ENTITIES.toInput(false), null, UIHint.CHECKBOX, null, null))
                .addRow(new PortRow(StandardPorts.LIMIT.toInput(1), null, UIHint.INPUT, null, null))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        Entity sourceEntity = getInput(context, StandardPorts.SOURCE_ENTITY.getId(), Entity.class);
        if (sourceEntity == null) return next(StandardPorts.FLOW_OUT.getId());

        Vec3 posOffset = getInput(context, StandardPorts.START_POS.getId(), Vec3.class);
        Float pitchOffset = getInput(context, StandardPorts.PITCH.getId(), Float.class);
        Float yawOffset = getInput(context, StandardPorts.YAW.getId(), Float.class);
        Float length = getInput(context, StandardPorts.DIST.getId(), Float.class);
        Integer color = getInput(context, StandardPorts.COLOR.getId(), Integer.class);
        Float radius = getInput(context, StandardPorts.RADIUS.getId(), Float.class);
        Integer duration = getInput(context, StandardPorts.TICK.getId(), Integer.class);

        Boolean penetrateSolid = getInput(context, StandardPorts.PENETRATE_SOLID.getId(), Boolean.class);
        Boolean penetrateTrans = getInput(context, StandardPorts.PENETRATE_TRANS.getId(), Boolean.class);
        Boolean penetrateEntities = getInput(context, StandardPorts.PENETRATE_ENTITIES.getId(), Boolean.class);
        Integer limit = getInput(context, StandardPorts.LIMIT.getId(), Integer.class);

        // NBT 封包
        CompoundTag extraData = new CompoundTag();
        extraData.putInt("sourceId", sourceEntity.getId());
        extraData.putDouble("offX", posOffset != null ? posOffset.x : 0);
        extraData.putDouble("offY", posOffset != null ? posOffset.y : 0);
        extraData.putDouble("offZ", posOffset != null ? posOffset.z : 0);
        extraData.putFloat("offPitch", pitchOffset != null ? pitchOffset : 0);
        extraData.putFloat("offYaw", yawOffset != null ? yawOffset : 0);
        extraData.putFloat("length", length != null ? length : 20f);
        extraData.putFloat("radius", radius != null ? radius : 0.1f);

        // 【新增】：封装物理规则
        extraData.putBoolean("penSolid", penetrateSolid != null ? penetrateSolid : false);
        extraData.putBoolean("penTrans", penetrateTrans != null ? penetrateTrans : true);
        extraData.putBoolean("penEnt", penetrateEntities != null ? penetrateEntities : false);
        extraData.putInt("maxEnt", limit != null ? limit : 1);

        context.broadcastDynamicVisual("ray_beam", color != null ? color : -1, duration != null ? duration : 2, new HashMap<>(), new HashMap<>(), extraData);
        return next(StandardPorts.FLOW_OUT.getId());
    }
}