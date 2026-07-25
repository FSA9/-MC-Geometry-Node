package com.mine.geometry_node.core.node.nodes.actions.schematic;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import com.mine.geometry_node.core.schematic.SchematicPlacementDebugSync;
import com.mine.geometry_node.core.schematic.SchematicPlacementManager;
import com.mine.geometry_node.core.schematic.SchematicPlacementManager.OperationResult;
import com.mine.geometry_node.core.schematic.SchematicPlacementManager.SchematicPlacementRecord;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;

import java.util.Map;

public class RevertSchematicPlacement extends BaseNode {
    public static final String TYPE_ID = "revert_schematic_placement";

    private static final int DIRECT_SET_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

    @Override
    public NodeDef getDefaultDefinition() {
        String comment = """
                移除指定 key 的结构放置，将世界恢复到放置之前的状态。
                会强制恢复本次放置记录中的所有 before 快照：原本是空气就清空，原本是草方块就恢复草方块。
                即使结构方块已经被玩家手动破坏，也会按放置前快照恢复。
                affect_entities 开启时会删除该次放置生成的实体。
                结构包围盒由放置记录自动维护；玩家可通过 /geometry_node debug schem on/off 控制是否可见。
                block_stats 输出本次移除后恢复成的方块 ID 与数量。""";

        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.revert_schematic_placement"))
                .comment(comment)
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.BOOL.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.COUNT.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.BLOCK_STATS.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.KEY.toInput(""), null, UIHint.INPUT, null, null))
                .addRow(new PortRow(StandardPorts.AFFECT_ENTITIES.toInput(true), null, UIHint.CHECKBOX, null, null))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        OperationResult result = new OperationResult(false, 0, 0, 0, Map.of());
        if (context.getLevel() instanceof ServerLevel level) {
            String key = getInput(context, StandardPorts.KEY.getId(), String.class);
            _SchematicActionUtils.PlacementLookup lookup = _SchematicActionUtils.resolvePlacement(
                    level, key, _SchematicActionUtils.eventBlockPos(context));
            SchematicPlacementRecord previousRecord = lookup.record();
            if (previousRecord == null) {
                logMissing(level, lookup.requestedKey());
            } else {
                String safeKey = previousRecord.key();
                if (lookup.resolvedByPosition() && !lookup.requestedKey().equals(safeKey)) {
                    GeometryNode.LOGGER.info("[GeometryNode] Revert schematic placement key '{}' resolved to '{}' by event position.",
                            lookup.requestedKey(), safeKey);
                }
                boolean affectEntities = _SchematicActionUtils.boolOrDefault(
                        getInput(context, StandardPorts.AFFECT_ENTITIES.getId(), Boolean.class), true);
                result = SchematicPlacementManager.revert(level, safeKey, DIRECT_SET_FLAGS, affectEntities);
                SchematicPlacementRecord currentRecord = SchematicPlacementManager.get(level, safeKey).orElse(null);
                SchematicPlacementDebugSync.syncRecord(level, safeKey, currentRecord);
                if (result.found()) {
                    _SchematicActionUtils.sendProjectionRemoval(context, level, safeKey);
                }
                logResult(level, safeKey, previousRecord, result);
            }
        }

        context.setTempData(tempKey(context, StandardPorts.BOOL.getId()), affected(result));
        context.setTempData(tempKey(context, StandardPorts.COUNT.getId()), result.blocks());
        context.setTempData(tempKey(context, StandardPorts.BLOCK_STATS.getId()), result.blockStats());
        return next(StandardPorts.FLOW_OUT.getId());
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        if (StandardPorts.BOOL.getId().equals(portName)
                || StandardPorts.COUNT.getId().equals(portName)
                || StandardPorts.BLOCK_STATS.getId().equals(portName)) {
            return context.getTempData(tempKey(context, portName));
        }
        return null;
    }

    private static String tempKey(ExecutionContext context, String port) {
        return TYPE_ID + ":" + context.getCurrentNodeId() + ":" + port;
    }

    private static void logMissing(ServerLevel level, String key) {
        if (key == null || key.isEmpty()) {
            GeometryNode.LOGGER.warn("[GeometryNode] Revert schematic placement skipped: empty key and no placement at event position.");
        } else {
            GeometryNode.LOGGER.warn("[GeometryNode] Revert schematic placement skipped: no placement record for key '{}' in dimension '{}'.",
                    key, level.dimension().identifier());
        }
    }

    private static boolean affected(OperationResult result) {
        return result.found() && (result.blocks() > 0 || result.removedEntities() > 0 || result.respawnedEntities() > 0);
    }

    private static void logResult(ServerLevel level, String key, SchematicPlacementRecord record, OperationResult result) {
        if (!result.found()) {
            GeometryNode.LOGGER.warn("[GeometryNode] Revert schematic placement skipped: no placement record for key '{}' in dimension '{}'.",
                    key, level.dimension().identifier());
            return;
        }
        if (result.blocks() == 0 && result.removedEntities() == 0) {
            String origin = record != null ? formatPos(record.origin()) : "unknown";
            GeometryNode.LOGGER.warn("[GeometryNode] Revert schematic placement '{}' at {} found a record but changed no blocks/entities.",
                    key, origin);
            return;
        }
        if (record != null) {
            GeometryNode.LOGGER.info("[GeometryNode] Reverted schematic placement '{}' at {} in dimension '{}': {} blocks, {} entities.",
                    key, formatPos(record.origin()), level.dimension().identifier(), result.blocks(), result.removedEntities());
        }
    }

    private static String formatPos(net.minecraft.core.BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }
}
