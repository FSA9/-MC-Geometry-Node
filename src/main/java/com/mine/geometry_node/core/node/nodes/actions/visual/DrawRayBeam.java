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
import com.mine.geometry_node.core.node.definition.port.PortType;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.nodes.*;
import com.mine.geometry_node.core.node.util.AnchorNodeSupport;
import com.mine.geometry_node.core.node.util.VisualTransformInputs;
import com.mine.geometry_node.core.node.value.color.ColorValue;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

public class DrawRayBeam extends BaseNode {

    public static final String TYPE_ID = "draw_ray_beam";
    public static final PortDef DISTANCE_PORT = StandardPorts.DIST.toInput(20.0F).liveExpression();
    public static final PortDef RADIUS_PORT = StandardPorts.RADIUS.toInput(0.1F).liveExpression();

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.draw_ray_beam"))
                .comment(NodeComment.builder(TYPE_ID)
                        .text("summary")
                        .output(StandardPorts.FLOW_OUT, "flow_out")
                        .input(StandardPorts.FLOW_IN, "flow_in")
                        .input(StandardPorts.SPATIAL_ANCHOR, "geometry_node.anchor.help")
                        .input(AnchorNodeSupport.WORLD_OFFSET.id(), "geometry_node.transform.world_offset.help")
                        .input(AnchorNodeSupport.WORLD_ROTATION.id(), "geometry_node.transform.world_rotation.help")
                        .input(AnchorNodeSupport.LOCAL_OFFSET.id(), "geometry_node.transform.local_offset.help")
                        .input(AnchorNodeSupport.LOCAL_ROTATION.id(), "geometry_node.transform.local_rotation.help")
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
                .addRow(AnchorNodeSupport.row(StandardPorts.SPATIAL_ANCHOR))
                .addPassthroughInput(AnchorNodeSupport.WORLD_OFFSET, UIHint.VECTOR)
                .addPassthroughInput(AnchorNodeSupport.WORLD_ROTATION, UIHint.VECTOR)
                .addRow(PortRow.passthrough(AnchorNodeSupport.LOCAL_OFFSET, UIHint.VECTOR,null,null))
                .addRow(PortRow.passthrough(AnchorNodeSupport.LOCAL_ROTATION, UIHint.VECTOR,null,null))
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
        SpatialAnchor anchor = AnchorNodeSupport.capture(context, StandardPorts.SPATIAL_ANCHOR);
        if (anchor == null || !VisualSpatialAnchors.canBroadcast(anchor, context.getLevel()))
            return next(StandardPorts.FLOW_OUT.getId());
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
        VisualSpatialAnchors.write(extraData, "anchor", anchor, context.getLevel());
        extraData.putFloat("length", length != null ? length : 20f);
        extraData.putFloat("radius", radius != null ? radius : 0.1f);

        // 【新增】：封装物理规则
        extraData.putBoolean("penSolid", penetrateSolid != null ? penetrateSolid : false);
        extraData.putBoolean("penTrans", penetrateTrans != null ? penetrateTrans : true);
        extraData.putBoolean("penEnt", penetrateEntities != null ? penetrateEntities : false);
        extraData.putInt("maxEnt", limit != null ? limit : 1);

        Map<String, ExpressionData> expressions = new LinkedHashMap<>();
        putInputExpression(context, StandardPorts.SPATIAL_ANCHOR.getId(), "anchor", expressions);
        VisualTransformInputs.write(context, extraData, expressions);
        putInputExpression(context, StandardPorts.DIST.getId(), "distance", expressions);
        putInputExpression(context, StandardPorts.RADIUS.getId(), "radius", expressions);

        context.broadcastDynamicVisual("ray_beam", color != null ? color.toArgb() : ColorValue.WHITE.toArgb(),
                duration != null ? duration : 2, expressions, extraData);
        return next(StandardPorts.FLOW_OUT.getId());
    }
}
