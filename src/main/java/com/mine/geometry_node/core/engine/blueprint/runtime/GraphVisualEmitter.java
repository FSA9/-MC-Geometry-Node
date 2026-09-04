package com.mine.geometry_node.core.engine.blueprint.runtime;

import com.mine.geometry_node.core.engine.service.GraphEngineServices;
import com.mine.geometry_node.core.engine.graph.expression.ExpressionData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.List;

final class GraphVisualEmitter {
    private static final int RADIUS = 128;

    private GraphVisualEmitter() {
    }

    static void broadcastDynamicVisual(ServerLevel level, String effectType, int color, int durationTicks,
                                       Map<String, ExpressionData> expressions,
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

        broadcastDynamicVisual(level, effectType, color, durationTicks, expressions, extraData,
                center, RADIUS, List.of());
    }

    static void broadcastDynamicVisual(ServerLevel level, String effectType, int color, int durationTicks,
                                       Map<String, ExpressionData> expressions,
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
                extraData,
                center != null ? center : Vec3.ZERO,
                radius,
                assets != null ? List.copyOf(assets) : List.of()
        ));
    }
}
