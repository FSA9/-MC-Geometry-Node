package com.mine.geometry_node.core.node.nodes.actions.visual; // 建议移动到 action 包下

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.node.nodes.*;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
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
        String comment = """
                从 start_pos 沿 vector 方向执行一次射线检测。
                distance 控制最大距离，radius 会扩大实体命中范围。
                输出是否命中、命中位置，以及最近命中的实体。
                同一次执行中多个输出会复用缓存结果。""";

        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.raycast"))
                .comment(comment)
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.IS_HIT.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.XYZ.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.ENTITY.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.START_POS.toInput(), null, UIHint.VECTOR, null, null))
                .addRow(new PortRow(StandardPorts.VECTOR.toInput(), null, UIHint.VECTOR, null, null))
                .addRow(new PortRow(StandardPorts.DIST.toInput(), null, UIHint.INPUT, null, null))
                .addRow(new PortRow(StandardPorts.RADIUS.toInput(), null, UIHint.INPUT, null, null))
                .build();
    }

    private record RaycastResult(boolean isHit, Vec3 hitPos, Entity hitEntity) {}

    private RaycastResult doTrace(ExecutionContext context) {
        String cacheKey = "raycast_cache_" + context.getCurrentNodeId();
        RaycastResult cached = (RaycastResult) context.getTempData(cacheKey);
        if (cached != null) return cached;

        Vec3 start = getInput(context, StandardPorts.START_POS.getId(), Vec3.class);
        Vec3 dir = getInput(context, StandardPorts.VECTOR.getId(), Vec3.class);
        Float dist = getInput(context, StandardPorts.DIST.getId(), Float.class);
        Float radius = getInput(context, StandardPorts.RADIUS.getId(), Float.class);

        if (start == null || dir == null || dir.lengthSqr() < 0.0001) {
            return cacheAndReturn(context, cacheKey, new RaycastResult(false, start != null ? start : Vec3.ZERO, null));
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

        return cacheAndReturn(context, cacheKey, finalResult);
    }

    private RaycastResult cacheAndReturn(ExecutionContext context, String key, RaycastResult result) {
        context.setTempData(key, result);
        return result;
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        doTrace(context);
        return next(StandardPorts.FLOW_OUT.getId());
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        RaycastResult result = doTrace(context);
        if (StandardPorts.IS_HIT.getId().equals(portName)) return result.isHit();
        if (StandardPorts.XYZ.getId().equals(portName)) return result.hitPos();
        if (StandardPorts.ENTITY.getId().equals(portName)) return result.hitEntity();
        return null;
    }
}
