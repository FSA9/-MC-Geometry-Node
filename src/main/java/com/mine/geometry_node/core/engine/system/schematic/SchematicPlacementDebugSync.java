package com.mine.geometry_node.core.engine.system.schematic;

import com.mine.geometry_node.core.engine.graph.debug.DebugRenderShape;
import com.mine.geometry_node.core.engine.graph.debug.DebugRenderChannel;
import com.mine.geometry_node.core.engine.graph.debug.DebugRendererSessionManager;
import com.mine.geometry_node.core.engine.system.schematic.SchematicPlacementManager.SchematicPlacementRecord;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class SchematicPlacementDebugSync {
    private static boolean registered;

    private SchematicPlacementDebugSync() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        DebugRendererSessionManager.registerSchematicChannelHydrator(SchematicPlacementDebugSync::syncPlayer);
    }

    public static int syncPlayer(ServerPlayer player) {
        return player != null ? syncLevel(player.level()) : 0;
    }

    public static int syncLevel(ServerLevel level) {
        if (level == null) {
            return 0;
        }

        int count = 0;
        long currentTick = level.getGameTime();
        for (SchematicPlacementRecord record : SchematicPlacementStorage.get(level).records()) {
            if (!level.dimension().equals(record.dimension())) {
                continue;
            }
            syncRecord(level, record.key(), record, currentTick);
            count++;
        }
        return count;
    }

    public static void syncRecord(ServerLevel level, String key, SchematicPlacementRecord record) {
        syncRecord(level, key, record, level != null ? level.getGameTime() : 0L);
    }

    public static void syncRecord(ServerLevel level, String key, SchematicPlacementRecord record, long currentTick) {
        String sourceKey = boundsSourceKey(level, key);
        if (level == null || sourceKey == null) {
            return;
        }
        if (record == null) {
            DebugRendererSessionManager.removeSourceShapes(level, sourceKey);
            return;
        }

        DebugRendererSessionManager.replacePersistentSourceShapes(
                level,
                sourceKey,
                List.of(toDebugShape(sourceKey, record)),
                currentTick
        );
    }

    private static DebugRenderShape toDebugShape(String sourceKey, SchematicPlacementRecord record) {
        return new DebugRenderShape(
                sourceKey + ":bounds",
                record.graphId(),
                "box",
                record.boundsCenter(),
                record.boundsSize(),
                Vec3.ZERO,
                DebugRenderChannel.SCHEMATIC.color()
        );
    }

    private static String boundsSourceKey(ServerLevel level, String key) {
        if (level == null || key == null || key.isBlank()) {
            return null;
        }
        return DebugRendererSessionManager.schematicPlacementSourceKey(level, key);
    }
}
