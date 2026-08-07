package com.mine.geometry_node.core.engine.blueprint.runtime;

import com.mine.geometry_node.core.engine.service.GraphEngineServices;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.Collections;
import java.util.Map;
import java.util.List;

final class GraphVisualEmitter {
    private static final int RADIUS = 128;

    private GraphVisualEmitter() {
    }

    static void broadcastVisual(ServerLevel level, String effectType,
                                int sourceEntityId, Vec3 startPos,
                                int targetEntityId, Vec3 endPos,
                                int color, float size, int durationTicks) {
        CompoundTag extraData = new CompoundTag();
        extraData.putInt("sourceId", sourceEntityId);
        if (startPos != null) {
            extraData.putDouble("startX", startPos.x);
            extraData.putDouble("startY", startPos.y);
            extraData.putDouble("startZ", startPos.z);
        }
        extraData.putInt("targetId", targetEntityId);
        if (endPos != null) {
            extraData.putDouble("endX", endPos.x);
            extraData.putDouble("endY", endPos.y);
            extraData.putDouble("endZ", endPos.z);
        }
        extraData.putFloat("size", size);

        Vec3 center = startPos != null ? startPos : Vec3.ZERO;
        GraphEngineServices.INSTANCE.visualSink().broadcast(new GraphEngineServices.VisualEffect(
                level,
                effectType,
                color,
                durationTicks,
                Collections.emptyMap(),
                Collections.emptyMap(),
                extraData,
                center,
                RADIUS,
                List.of()
        ));
    }

    static void broadcastDynamicVisual(ServerLevel level, String effectType, int color, int durationTicks,
                                       Map<String, String> expressions,
                                       Map<String, String> bindings,
                                       CompoundTag extraData) {
        Vec3 center = null;

        if (extraData != null && extraData.contains("sourceId")) {
            int sourceId = extraData.getIntOr("sourceId", -1);
            if (sourceId != -1) {
                Entity sourceEntity = level.getEntity(sourceId);
                if (sourceEntity != null) {
                    center = sourceEntity.position();
                }
            }
        }

        if (center == null && extraData != null && extraData.contains("startX")) {
            center = new Vec3(extraData.getDoubleOr("startX", 0.0),
                              extraData.getDoubleOr("startY", 0.0),
                              extraData.getDoubleOr("startZ", 0.0));
        }

        if (center == null) {
            center = Vec3.ZERO;
        }

        broadcastDynamicVisual(level, effectType, color, durationTicks, expressions, bindings, extraData, center, RADIUS, List.of());
    }

    static void broadcastDynamicVisual(ServerLevel level, String effectType, int color, int durationTicks,
                                       Map<String, String> expressions,
                                       Map<String, String> bindings,
                                       CompoundTag extraData,
                                       Vec3 center,
                                       double radius,
                                       List<GraphEngineServices.VisualAsset> assets) {
        GraphEngineServices.INSTANCE.visualSink().broadcast(new GraphEngineServices.VisualEffect(
                level,
                effectType,
                color,
                durationTicks,
                expressions,
                bindings,
                extraData,
                center != null ? center : Vec3.ZERO,
                radius,
                assets != null ? List.copyOf(assets) : List.of()
        ));
    }
}
