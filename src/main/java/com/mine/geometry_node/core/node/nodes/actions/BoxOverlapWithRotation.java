package com.mine.geometry_node.core.node.nodes.actions;

import com.mine.geometry_node.core.execution.ExecutionContext;
import com.mine.geometry_node.core.execution.ExecutionResult;
import com.mine.geometry_node.core.node.nodes.*;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public class BoxOverlapWithRotation extends BaseNode {

    public static final String TYPE_ID = "box_overlap_rotated";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.box_overlap_rotated"))
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.LIST.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.CENTER.toInput(), null, UIHint.INPUT, null, null))
                .addRow(new PortRow(StandardPorts.SIZE_3.toInput(), null, UIHint.INPUT, null, null))
                .addRow(new PortRow(StandardPorts.ROTATION.toInput(), null, UIHint.INPUT, null, null))
                .build();
    }

    private List<Entity> doOverlap(ExecutionContext context) {
        String cacheKey = "obb_cache_" + context.getCurrentNodeId();
        @SuppressWarnings("unchecked")
        List<Entity> cached = (List<Entity>) context.getTempData(cacheKey);
        if (cached != null) return cached;

        Vec3 center = getInput(context, StandardPorts.CENTER.getId(), Vec3.class);
        Vec3 size = getInput(context, StandardPorts.SIZE_3.getId(), Vec3.class);
        Vec3 rot = getInput(context, StandardPorts.ROTATION.getId(), Vec3.class);

        if (center == null || size == null) {
            return cacheAndReturn(context, cacheKey, List.of());
        }
        if (rot == null) rot = Vec3.ZERO;

        // 1. 宽相 (Broad-phase): 用最大的外接球半径框出一个绝对 AABB，捞出附近的候选实体
        double radius = size.length() / 2.0;
        AABB broadBox = AABB.ofSize(center, radius * 2, radius * 2, radius * 2);
        List<Entity> candidateEntities = context.getLevel().getEntities((Entity) null, broadBox, e -> true);

        Quaternionf q = new Quaternionf().rotationYXZ(
                (float) Math.toRadians(rot.y),
                (float) Math.toRadians(rot.x),
                (float) Math.toRadians(rot.z)
        );
        Quaternionf invQ = new Quaternionf(q).invert();

        List<Entity> hitEntities = new ArrayList<>();
        Vec3 halfSize = size.scale(0.5);

        // 3. 窄相 (Narrow-phase): 将实体的世界坐标转换到 Box 的局部坐标系中
        for (Entity e : candidateEntities) {
            Vec3 eCenter = e.getBoundingBox().getCenter();

            Vector3f localPos = new Vector3f(
                    (float) (eCenter.x - center.x),
                    (float) (eCenter.y - center.y),
                    (float) (eCenter.z - center.z)
            );

            localPos.rotate(invQ);

            //实体碰撞体积顾长
            float ex = (float) (halfSize.x + e.getBbWidth() / 2.0f);
            float ey = (float) (halfSize.y + e.getBbHeight() / 2.0f);
            float ez = (float) (halfSize.z + e.getBbWidth() / 2.0f);

            // AABB 点内检测
            if (Math.abs(localPos.x()) <= ex &&
                    Math.abs(localPos.y()) <= ey &&
                    Math.abs(localPos.z()) <= ez) {
                hitEntities.add(e);
            }
        }

        return cacheAndReturn(context, cacheKey, hitEntities);
    }

    private List<Entity> cacheAndReturn(ExecutionContext context, String key, List<Entity> result) {
        context.setTempData(key, result);
        return result;
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        doOverlap(context);
        return next(StandardPorts.FLOW_OUT.getId());
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        if (StandardPorts.LIST.getId().equals(portName)) {
            return doOverlap(context);
        }
        return null;
    }
}