package com.mine.geometry_node.core.node.nodes.actions;

import com.mine.geometry_node.core.execution.ExecutionContext;
import com.mine.geometry_node.core.execution.ExecutionResult;
import com.mine.geometry_node.core.node.nodes.*;
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
                // 执行流
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                // 输出：是否命中、命中坐标、命中的实体
                .addRow(new PortRow(null, StandardPorts.IS_HIT.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.XYZ.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.ENTITY.toOutput(), UIHint.DEFAULT, null, null))
                // 输入：起点、方向、距离
                .addRow(new PortRow(StandardPorts.START_POS.toInput(), null, UIHint.INPUT, null, null))
                .addRow(new PortRow(StandardPorts.VECTOR.toInput(), null, UIHint.INPUT, null, null))
                .addRow(new PortRow(StandardPorts.DIST.toInput(), null, UIHint.INPUT, null, null))
                .build();
    }

    private record RaycastResult(boolean isHit, Vec3 hitPos, Entity hitEntity) {}

    /**
     * [核心算法] 执行真正的射线检测。带有帧级缓存，防止下游多端口拉取时造成重复计算。
     */
    private RaycastResult doTrace(ExecutionContext context) {
        String cacheKey = "raycast_cache_" + context.getCurrentNodeId();
        RaycastResult cached = (RaycastResult) context.getTempData(cacheKey);
        if (cached != null) return cached;

        Vec3 start = getInput(context, StandardPorts.START_POS.getId(), Vec3.class);
        Vec3 dir = getInput(context, StandardPorts.VECTOR.getId(), Vec3.class);
        Float dist = getInput(context, StandardPorts.DIST.getId(), Float.class);

        if (start == null || dir == null || dir.lengthSqr() < 0.0001) {
            return cacheAndReturn(context, cacheKey, new RaycastResult(false, start != null ? start : Vec3.ZERO, null));
        }
        if (dist == null) dist = 20.0f;

        Vec3 end = start.add(dir.normalize().scale(dist));

        // 1. 方块检测
        ClipContext clipCtx = new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, context.getEntity());
        BlockHitResult blockHit = context.getLevel().clip(clipCtx);
        boolean isBlockHit = blockHit.getType() != HitResult.Type.MISS;
        double blockDistSqr = isBlockHit ? start.distanceToSqr(blockHit.getLocation()) : Double.MAX_VALUE;

        // 2. 实体检测
        Entity closestEntity = null;
        Vec3 closestHitPos = null;
        double minEntityDistSqr = Double.MAX_VALUE;

        // 构建包裹整条射线的宽相包围盒
        AABB broadBox = new AABB(start, end).inflate(1.0);
        List<Entity> entities = context.getLevel().getEntities(context.getEntity(), broadBox, e -> !e.isSpectator() && e.isPickable());

        for (Entity e : entities) {
            // 将实体的判定箱稍微扩大，包含其特有的拾取半径
            AABB aabb = e.getBoundingBox().inflate(e.getPickRadius());
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
        if (closestEntity != null && minEntityDistSqr <= blockDistSqr) { // 实体
            finalResult = new RaycastResult(true, closestHitPos, closestEntity);
        } else if (isBlockHit) { // 方块
            finalResult = new RaycastResult(true, blockHit.getLocation(), null);
        } else { // 射线尽头坐标
            finalResult = new RaycastResult(false, end, null);
        }

        return cacheAndReturn(context, cacheKey, finalResult);
    }

    private RaycastResult cacheAndReturn(ExecutionContext context, String key, RaycastResult result) {
        context.setTempData(key, result);
        return result;
    }

    // --- 兼容执行与数据双模 ---

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        doTrace(context); // 强行触发计算并缓存
        return next(StandardPorts.FLOW_OUT.getId());
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        RaycastResult result = doTrace(context);

        if (StandardPorts.IS_HIT.getId().equals(portName)) {
            return result.isHit();
        } else if (StandardPorts.XYZ.getId().equals(portName)) {
            return result.hitPos();
        } else if (StandardPorts.ENTITY.getId().equals(portName)) {
            return result.hitEntity();
        }

        return null;
    }
}