package com.mine.geometry_node.core.node.nodes.actions.schematic;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceReferences;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.engine.system.schematic.placement.SchematicPlacementManager;
import com.mine.geometry_node.core.engine.system.schematic.placement.SchematicPlacementManager.SchematicPlacementRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.List;

final class _SchematicActionUtils {
    private _SchematicActionUtils() {
    }

    static boolean boolOrDefault(Boolean value, boolean fallback) {
        return value != null ? value : fallback;
    }

    static PlacementTarget placementTarget(ExecutionContext context, String input) {
        ServerLevel level = context.getLevel();
        if (input == null || input.isBlank()) return new PlacementTarget(level, "", false);
        try {
            var address = GraphResourceReferences.resolvePlacement(context, context.getCurrentNodeStableId(),
                    input, level.dimension(), false);
            return new PlacementTarget(level.getServer().getLevel(address.dimension()), address.key(),
                    GraphResourceReferences.isEncoded(input));
        } catch (IllegalArgumentException invalid) {
            return new PlacementTarget(null, "", true);
        }
    }

    record PlacementTarget(ServerLevel level, String key, boolean exact) {}

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

    record PlacementLookup(SchematicPlacementRecord record, String requestedKey, boolean resolvedByPosition) {
    }

}
