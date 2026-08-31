package com.mine.geometry_node.core.node.nodes.actions.visual;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class MultiRaycast extends BaseNode {

    public static final String TYPE_ID = "multi_raycast";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.multi_raycast"))
                .comment(NodeComment.builder(TYPE_ID)
                        .text("summary")
                        .output(StandardPorts.FLOW_OUT, "flow_out")
                        .output(StandardPorts.IS_HIT, "is_hit")
                        .output(StandardPorts.XYZ, "xyz")
                        .output(StandardPorts.LIST, "list")
                        .input(StandardPorts.FLOW_IN, "flow_in")
                        .input(StandardPorts.START_POS, "start_pos")
                        .input(StandardPorts.PITCH, "pitch")
                        .input(StandardPorts.YAW, "yaw")
                        .input(StandardPorts.DIST, "distance")
                        .input(StandardPorts.RADIUS, "radius")
                        .input(StandardPorts.PENETRATE_SOLID, "penetrate_solid")
                        .input(StandardPorts.PENETRATE_TRANS, "penetrate_trans")
                        .input(StandardPorts.PENETRATE_ENTITIES, "penetrate_entities")
                        .input(StandardPorts.LIMIT, "limit")
                        .build())
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))

                // 输出
                .addRow(new PortRow(null, StandardPorts.IS_HIT.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.XYZ.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.LIST.toOutput(), UIHint.DEFAULT, null, null))

                // 输入：起点、角度、距离、半径
                .addRow(new PortRow(StandardPorts.START_POS.toInput(), null, UIHint.VECTOR, null, null))
                .addRow(new PortRow(StandardPorts.PITCH.toInput(0.0f), null, UIHint.INPUT, null, null))
                .addRow(new PortRow(StandardPorts.YAW.toInput(0.0f), null, UIHint.INPUT, null, null))
                .addRow(new PortRow(StandardPorts.DIST.toInput(), null, UIHint.INPUT, null, null))
                .addRow(new PortRow(StandardPorts.RADIUS.toInput(0.0f), null, UIHint.INPUT, null, null))

                // 输入：穿透设置 (复用 LIMIT 作为穿透上限)
                .addRow(new PortRow(StandardPorts.PENETRATE_SOLID.toInput(false), null, UIHint.CHECKBOX, null, null))
                .addRow(new PortRow(StandardPorts.PENETRATE_TRANS.toInput(true), null, UIHint.CHECKBOX, null, null))
                .addRow(new PortRow(StandardPorts.PENETRATE_ENTITIES.toInput(false), null, UIHint.CHECKBOX, null, null))
                .addRow(new PortRow(StandardPorts.LIMIT.toInput(1), null, UIHint.INPUT, null, null))
                .build();
    }

    private record MultiRaycastResult(boolean isHit, Vec3 endPos, List<Entity> hitEntities) {}

    private MultiRaycastResult doTrace(ExecutionContext context) {
        String cacheKey = "multi_raycast_cache_" + context.getCurrentNodeId();
        MultiRaycastResult cached = (MultiRaycastResult) context.getTempData(cacheKey);
        if (cached != null) return cached;

        Vec3 start = getInput(context, StandardPorts.START_POS.getId(), Vec3.class);
        Float pitch = getInput(context, StandardPorts.PITCH.getId(), Float.class);
        Float yaw = getInput(context, StandardPorts.YAW.getId(), Float.class);
        Float dist = getInput(context, StandardPorts.DIST.getId(), Float.class);
        Float radius = getInput(context, StandardPorts.RADIUS.getId(), Float.class);

        Boolean penetrateSolid = getInput(context, StandardPorts.PENETRATE_SOLID.getId(), Boolean.class);
        Boolean penetrateTrans = getInput(context, StandardPorts.PENETRATE_TRANS.getId(), Boolean.class);
        Boolean penetrateEntities = getInput(context, StandardPorts.PENETRATE_ENTITIES.getId(), Boolean.class);
        Integer maxEntities = getInput(context, StandardPorts.LIMIT.getId(), Integer.class);

        if (start == null) start = Vec3.ZERO;
        if (pitch == null) pitch = 0f;
        if (yaw == null) yaw = 0f;
        if (dist == null) dist = 20.0f;
        if (radius == null) radius = 0.0f;
        if (penetrateSolid == null) penetrateSolid = false;
        if (penetrateTrans == null) penetrateTrans = true;
        if (penetrateEntities == null) penetrateEntities = false;
        if (maxEntities == null) maxEntities = 1;

        // 1. 将欧拉角转换为方向向量
        Vec3 dir = Vec3.directionFromRotation(pitch, yaw);
        Vec3 maxEnd = start.add(dir.scale(dist));
        Vec3 actualEnd = maxEnd;

        // 2. 方块检测 (步进式穿透检测)
        if (!penetrateSolid) {
            Vec3 currentStart = start;
            while (true) {
                ClipContext clipCtx = new ClipContext(currentStart, maxEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, context.getEntity());
                BlockHitResult blockHit = context.getLevel().clip(clipCtx);

                if (blockHit.getType() != HitResult.Type.MISS) {
                    BlockState hitState = context.getLevel().getBlockState(blockHit.getBlockPos());
                    boolean isOpaque = hitState.canOcclude();

                    if (!isOpaque && penetrateTrans) {
                        currentStart = blockHit.getLocation().add(dir.scale(0.01));
                        if (currentStart.distanceToSqr(start) >= dist * dist) break;
                        continue;
                    } else {
                        actualEnd = blockHit.getLocation();
                        break;
                    }
                }
                break;
            }
        }

        // 3. 实体检测
        List<Entity> hitEntitiesList = new ArrayList<>();
        double actualDistSqr = start.distanceToSqr(actualEnd);

        AABB broadBox = new AABB(start, actualEnd).inflate(radius + 1.0);
        List<Entity> entities = context.getLevel().getEntities(context.getEntity(), broadBox, e -> !e.isSpectator() && e.isPickable());

        for (Entity e : entities) {
            AABB aabb = e.getBoundingBox().inflate(e.getPickRadius() + radius);
            Optional<Vec3> hitOpt = aabb.clip(start, actualEnd);

            if (hitOpt.isPresent()) {
                double dSqr = start.distanceToSqr(hitOpt.get());
                if (dSqr <= actualDistSqr) {
                    hitEntitiesList.add(e);
                }
            }
        }

        // 4. 排序与截断
        Vec3 finalStart = start;
        hitEntitiesList.sort(Comparator.comparingDouble(e -> e.distanceToSqr(finalStart)));

        if (!hitEntitiesList.isEmpty()) {
            if (!penetrateEntities) {
                Entity firstHit = hitEntitiesList.get(0);
                AABB firstAABB = firstHit.getBoundingBox().inflate(firstHit.getPickRadius() + radius);
                Optional<Vec3> hitOpt = firstAABB.clip(start, actualEnd);
                actualEnd = hitOpt.orElse(actualEnd);

                hitEntitiesList = List.of(firstHit);
            } else if (maxEntities > 0 && hitEntitiesList.size() > maxEntities) {
                hitEntitiesList = hitEntitiesList.subList(0, maxEntities);
            }
        }

        boolean isHit = !hitEntitiesList.isEmpty() || actualEnd.distanceToSqr(start) < maxEnd.distanceToSqr(start);
        MultiRaycastResult result = new MultiRaycastResult(isHit, actualEnd, hitEntitiesList);

        context.setTempData(cacheKey, result);
        return result;
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        doTrace(context);
        return next(StandardPorts.FLOW_OUT.getId());
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        MultiRaycastResult result = doTrace(context);

        if (StandardPorts.IS_HIT.getId().equals(portName)) return result.isHit();
        if (StandardPorts.XYZ.getId().equals(portName)) return result.endPos();
        if (StandardPorts.LIST.getId().equals(portName)) return result.hitEntities();

        return null;
    }
}
