package com.mine.geometry_node.core.node.nodes.actions.schematic;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.network.NetworkHandler;
import com.mine.geometry_node.core.network.packet.s2c.PacketSchematicProjection;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.schematic.SchematicPlacementManager;
import com.mine.geometry_node.core.schematic.SchematicPlacementManager.SchematicPlacementRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.List;

final class _SchematicActionUtils {
    private _SchematicActionUtils() {
    }

    static boolean boolOrDefault(Boolean value, boolean fallback) {
        return value != null ? value : fallback;
    }

    static PlacementLookup resolvePlacement(ServerLevel level, String requestedKey, BlockPos eventPos) {
        String safeKey = requestedKey == null ? "" : requestedKey.trim();
        SchematicPlacementRecord keyRecord = !safeKey.isEmpty()
                ? SchematicPlacementManager.get(level, safeKey).orElse(null)
                : null;
        SchematicPlacementRecord positionRecord = eventPos != null
                ? SchematicPlacementManager.findContaining(level, eventPos).orElse(null)
                : null;

        if (positionRecord != null && (keyRecord == null
                || !SchematicPlacementManager.containsBlock(level, keyRecord, eventPos)
                || positionRecord.createdGameTime() > keyRecord.createdGameTime())) {
            return new PlacementLookup(positionRecord, safeKey, true);
        }
        if (keyRecord != null) {
            return new PlacementLookup(keyRecord, safeKey, false);
        }
        return new PlacementLookup(null, safeKey, false);
    }

    static BlockPos eventBlockPos(ExecutionContext context) {
        if (context == null) {
            return null;
        }
        Object raw = context.getEventData(StandardPorts.XYZ.getId());
        if (raw instanceof BlockPos pos) {
            return pos.immutable();
        }
        if (raw instanceof Vec3 vec) {
            return BlockPos.containing(vec);
        }
        if (raw instanceof List<?> list && list.size() >= 3
                && list.get(0) instanceof Number x
                && list.get(1) instanceof Number y
                && list.get(2) instanceof Number z) {
            return BlockPos.containing(x.doubleValue(), y.doubleValue(), z.doubleValue());
        }
        return null;
    }

    static boolean sendProjectionRemoval(ExecutionContext context,
                                         ServerLevel level,
                                         String key) {
        PacketSchematicProjection packet = new PacketSchematicProjection(
                key,
                context.getGraphId(),
                level.dimension().identifier().toString(),
                0.0D,
                0.0D,
                0.0D,
                0,
                0,
                0,
                1,
                0.0f,
                false,
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );

        List<ServerPlayer> targets = level.getServer().getPlayerList().getPlayers();
        if (targets.isEmpty()) {
            return false;
        }
        NetworkHandler.sendToPlayers(targets, packet);
        return true;
    }

    record PlacementLookup(SchematicPlacementRecord record, String requestedKey, boolean resolvedByPosition) {
    }

}
