package com.mine.geometry_node.core.node.nodes.data.entity;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.engine.blueprint.spatial.RotatedBoxEntityQuery;
import com.mine.geometry_node.core.node.nodes.*;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;

public class GetEntitiesbyRotationBox extends BaseNode {

    public static final String TYPE_ID = "get_entities_by_rotation_box";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.get_entities_by_rotation_box"))
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.LIST.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.CENTER.toInput(), null, UIHint.VECTOR, null, null))
                .addRow(new PortRow(StandardPorts.SIZE_3.toInput(new Vec3(1, 1, 1)), null, UIHint.VECTOR, null, null))
                .addRow(new PortRow(StandardPorts.ROTATION.toInput(Vec3.ZERO), null, UIHint.VECTOR, null, null))
                .addRow(new PortRow(StandardPorts.DEBUG.toInput(false), null, UIHint.CHECKBOX, null, null))
                .addRow(new PortRow(StandardPorts.TICK.toInput(20), null, UIHint.INPUT, null, null))
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

        List<Entity> hitEntities = RotatedBoxEntityQuery.find(context.getLevel(), center, size, rot, e -> !e.isSpectator());

        Boolean isDebug = getInput(context, StandardPorts.DEBUG.getId(), Boolean.class);

        if (isDebug != null && isDebug) {
            Integer duration = getInput(context, StandardPorts.TICK.getId(), Integer.class);
            if (duration == null || duration <= 0) duration = 20;

            CompoundTag extraData = new CompoundTag();
            extraData.putDouble("startX", center.x);
            extraData.putDouble("startY", center.y);
            extraData.putDouble("startZ", center.z);
            extraData.putDouble("sizeX", size.x); extraData.putDouble("sizeY", size.y); extraData.putDouble("sizeZ", size.z);
            extraData.putDouble("rotX", rot.x); extraData.putDouble("rotY", rot.y); extraData.putDouble("rotZ", rot.z);

            context.broadcastDynamicVisual("debug_box", 0xFFFF0000, duration, new HashMap<>(), new HashMap<>(), extraData);
        }

        return cacheAndReturn(context, cacheKey, hitEntities);
    }

    private List<Entity> cacheAndReturn(ExecutionContext context, String key, List<Entity> result) {
        context.setTempData(key, result);
        return result;
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        String cacheKey = "obb_cache_" + context.getCurrentNodeId();
        context.removeTempData(cacheKey);

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
