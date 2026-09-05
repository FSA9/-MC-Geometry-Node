package com.mine.geometry_node.core.node.nodes.actions.visual;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.engine.graph.expression.ExpressionData;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.definition.node.NodeComment;
import com.mine.geometry_node.core.node.nodes.*;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.PortDef;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import com.mine.geometry_node.core.node.value.color.ColorValue;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import java.util.LinkedHashMap;
import java.util.Map;

public class DrawRayBeam extends BaseNode {

    public static final String TYPE_ID = "draw_ray_beam";
    public static final PortDef START_PORT = StandardPorts.START_POS.toInput(Vec3.ZERO).liveExpression();
    public static final PortDef PITCH_PORT = StandardPorts.PITCH.toInput(0.0F).liveExpression();
    public static final PortDef YAW_PORT = StandardPorts.YAW.toInput(0.0F).liveExpression();
    public static final PortDef DISTANCE_PORT = StandardPorts.DIST.toInput(20.0F).liveExpression();
    public static final PortDef RADIUS_PORT = StandardPorts.RADIUS.toInput(0.1F).liveExpression();

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.draw_ray_beam"))
                .comment(NodeComment.builder(TYPE_ID)
                        .text("summary")
                        .output(StandardPorts.FLOW_OUT, "flow_out")
                        .input(StandardPorts.FLOW_IN, "flow_in")
                        .input(StandardPorts.SOURCE_ENTITY, "source_entity")
                        .input(StandardPorts.START_POS, "start_pos")
                        .input(StandardPorts.PITCH, "pitch")
                        .input(StandardPorts.YAW, "yaw")
                        .input(StandardPorts.DIST, "distance")
                        .input(StandardPorts.COLOR, "color")
                        .input(StandardPorts.RADIUS, "radius")
                        .input(StandardPorts.TICK, "tick")
                        .input(StandardPorts.PENETRATE_SOLID, "penetrate_solid")
                        .input(StandardPorts.PENETRATE_TRANS, "penetrate_trans")
                        .input(StandardPorts.PENETRATE_ENTITIES, "penetrate_entities")
                        .input(StandardPorts.LIMIT, "limit")
                        .build())
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addPassthroughInput(StandardPorts.SOURCE_ENTITY.toInput(), UIHint.DEFAULT)
                .addPassthroughInput(START_PORT, UIHint.VECTOR)
                .addPassthroughInput(PITCH_PORT, UIHint.INPUT)
                .addPassthroughInput(YAW_PORT, UIHint.INPUT)
                .addPassthroughInput(DISTANCE_PORT, UIHint.INPUT)
                .addPassthroughInput(StandardPorts.COLOR.toInput(ColorValue.WHITE), UIHint.INPUT)
                .addPassthroughInput(RADIUS_PORT, UIHint.INPUT)
                .addPassthroughInput(StandardPorts.TICK.toInput(2), UIHint.INPUT, null, Map.of(PortMetaKeys.NUMERIC_MIN, 0))

                // 【新增】：把物理检测规则也传进去
                .addPassthroughInput(StandardPorts.PENETRATE_SOLID.toInput(false), UIHint.CHECKBOX)
                .addPassthroughInput(StandardPorts.PENETRATE_TRANS.toInput(true), UIHint.CHECKBOX)
                .addPassthroughInput(StandardPorts.PENETRATE_ENTITIES.toInput(false), UIHint.CHECKBOX)
                .addPassthroughInput(StandardPorts.LIMIT.toInput(1), UIHint.INPUT)
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        Entity sourceEntity = getInputFromList(
                context, StandardPorts.SOURCE_ENTITY.getId(), 0, Entity.class);
        if (sourceEntity == null) return next(StandardPorts.FLOW_OUT.getId());

        Vec3 posOffset = getInput(context, StandardPorts.START_POS.getId(), Vec3.class);
        Float pitchOffset = getInput(context, StandardPorts.PITCH.getId(), Float.class);
        Float yawOffset = getInput(context, StandardPorts.YAW.getId(), Float.class);
        Float length = getInput(context, StandardPorts.DIST.getId(), Float.class);
        ColorValue color = getInput(context, StandardPorts.COLOR.getId(), ColorValue.class);
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

        Map<String, ExpressionData> expressions = new LinkedHashMap<>();
        putInputExpression(context, StandardPorts.START_POS.getId(), "offset", expressions);
        putInputExpression(context, StandardPorts.PITCH.getId(), "pitch", expressions);
        putInputExpression(context, StandardPorts.YAW.getId(), "yaw", expressions);
        putInputExpression(context, StandardPorts.DIST.getId(), "distance", expressions);
        putInputExpression(context, StandardPorts.RADIUS.getId(), "radius", expressions);

        context.broadcastDynamicVisual("ray_beam", color != null ? color.toArgb() : ColorValue.WHITE.toArgb(),
                duration != null ? duration : 2, expressions, extraData);
        return next(StandardPorts.FLOW_OUT.getId());
    }
}
