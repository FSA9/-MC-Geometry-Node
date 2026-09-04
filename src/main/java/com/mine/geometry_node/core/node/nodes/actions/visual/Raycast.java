package com.mine.geometry_node.core.node.nodes.actions.visual; // 建议移动到 action 包下

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.engine.graph.data.GraphDataContext;
import com.mine.geometry_node.core.node.definition.node.NodeComment;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.nodes.*;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

public class Raycast extends BaseNode {

    public static final String TYPE_ID = "raycast";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.raycast"))
                .comment(NodeComment.builder(TYPE_ID)
                        .text("summary")
                        .output(StandardPorts.FLOW_OUT, "flow_out")
                        .output(StandardPorts.IS_HIT, "is_hit")
                        .output(StandardPorts.XYZ, "xyz")
                        .output(StandardPorts.ENTITY, "entity")
                        .input(StandardPorts.FLOW_IN, "flow_in")
                        .input(StandardPorts.START_POS, "start_pos")
                        .input(StandardPorts.VECTOR, "vector")
                        .input(StandardPorts.DIST, "distance")
                        .input(StandardPorts.RADIUS, "radius")
                        .build())
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.IS_HIT.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.XYZ.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.ENTITY.toOutput(), UIHint.DEFAULT, null, null))
                .addPassthroughInput(StandardPorts.START_POS.toInput(), UIHint.VECTOR)
                .addPassthroughInput(StandardPorts.VECTOR.toInput(), UIHint.VECTOR)
                .addPassthroughInput(StandardPorts.DIST.toInput(), UIHint.INPUT)
                .addPassthroughInput(StandardPorts.RADIUS.toInput(), UIHint.INPUT)
                .build();
    }

    private record RaycastResult(boolean isHit, Vec3 hitPos, Entity hitEntity) {}

    private RaycastResult trace(ExecutionContext context) {
        Vec3 start = getInput(context, StandardPorts.START_POS.getId(), Vec3.class);
        Vec3 dir = getInput(context, StandardPorts.VECTOR.getId(), Vec3.class);
        Float dist = getInput(context, StandardPorts.DIST.getId(), Float.class);
        Float radius = getInput(context, StandardPorts.RADIUS.getId(), Float.class);

        if (start == null || dir == null || dir.lengthSqr() < 0.0001) {
            return new RaycastResult(false, start != null ? start : Vec3.ZERO, null);
        }
        if (dist == null) dist = 20.0f;
        if (radius == null) radius = 0.0f; // 默认无限细

        Vec3 end = start.add(dir.normalize().scale(dist));

        ClipContext clipCtx = new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, context.getEntity());
        BlockHitResult blockHit = context.getLevel().clip(clipCtx);
        boolean isBlockHit = blockHit.getType() != HitResult.Type.MISS;
        double blockDistSqr = isBlockHit ? start.distanceToSqr(blockHit.getLocation()) : Double.MAX_VALUE;

        Entity closestEntity = null;
        Vec3 closestHitPos = null;
        double minEntityDistSqr = Double.MAX_VALUE;

        // 加上 radius 扩大宽相包围盒
        AABB broadBox = new AABB(start, end).inflate(radius + 1.0);
        List<Entity> entities = context.getLevel().getEntities(context.getEntity(), broadBox, e -> !e.isSpectator() && e.isPickable());

        for (Entity e : entities) {
            // 【核心修改】：将半径加到实体的判定箱上
            AABB aabb = e.getBoundingBox().inflate(e.getPickRadius() + radius);
            Optional<Vec3> hitOpt = aabb.clip(start, end);

            if (hitOpt.isPresent()) {
                double d = start.distanceToSqr(hitOpt.get());
                if (d < minEntityDistSqr) {
                    minEntityDistSqr = d;
                    closestEntity = e;
                    closestHitPos = hitOpt.get();
                }
            }
        }

        RaycastResult finalResult;
        if (closestEntity != null && minEntityDistSqr <= blockDistSqr) {
            finalResult = new RaycastResult(true, closestHitPos, closestEntity);
        } else if (isBlockHit) {
            finalResult = new RaycastResult(true, blockHit.getLocation(), null);
        } else {
            finalResult = new RaycastResult(false, end, null);
        }

        return finalResult;
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        RaycastResult result = trace(context);
        context.setNodeResult(StandardPorts.IS_HIT.getId(), result.isHit());
        context.setNodeResult(StandardPorts.XYZ.getId(), result.hitPos());
        context.setNodeResult(StandardPorts.ENTITY.getId(), result.hitEntity());
        return next(StandardPorts.FLOW_OUT.getId());
    }

    @Override
    public Object compute(GraphDataContext context, String portName) {
        if (StandardPorts.IS_HIT.getId().equals(portName)
                || StandardPorts.XYZ.getId().equals(portName)
                || StandardPorts.ENTITY.getId().equals(portName)) {
            return context.getNodeResult(portName);
        }
        return null;
    }
}
