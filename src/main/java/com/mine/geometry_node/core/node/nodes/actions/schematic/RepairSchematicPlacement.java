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
import com.mine.geometry_node.core.schematic.SchematicPlacementManager;
import com.mine.geometry_node.core.schematic.SchematicPlacementManager.OperationResult;
import com.mine.geometry_node.core.schematic.SchematicPlacementManager.SchematicPlacementRecord;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;

import java.util.Map;

public class RepairSchematicPlacement extends BaseNode {
    public static final String TYPE_ID = "repair_schematic_placement";

    private static final int DIRECT_SET_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

    @Override
    public NodeDef getDefaultDefinition() {
        String comment = """
                将指定 key 的结构放置修复成刚放置完成后的状态。
                repair_air 开启时会恢复本次放置记录中的空气位置；关闭时跳过这些空气目标。
                affect_entities 开启时会补回该次放置生成但已经缺失的实体。
                结构包围盒由放置记录自动维护；玩家可通过 /geometry_node debug schem on/off 控制是否可见。
                block_stats 输出本次修复成的方块 ID 与数量。""";

        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.repair_schematic_placement"))
                .comment(comment)
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.BOOL.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.COUNT.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.BLOCK_STATS.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.KEY.toInput(""), null, UIHint.INPUT, null, null))
                .addRow(new PortRow(StandardPorts.REPAIR_AIR.toInput(true), null, UIHint.CHECKBOX, null, null))
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
            SchematicPlacementRecord currentRecord = lookup.record();
            if (currentRecord == null) {
                logMissing(level, lookup.requestedKey());
            } else {
                String safeKey = currentRecord.key();
                if (lookup.resolvedByPosition() && !lookup.requestedKey().equals(safeKey)) {
                    GeometryNode.LOGGER.info("[GeometryNode] Repair schematic placement key '{}' resolved to '{}' by event position.",
                            lookup.requestedKey(), safeKey);
                }
                boolean repairAir = _SchematicActionUtils.boolOrDefault(
                        getInput(context, StandardPorts.REPAIR_AIR.getId(), Boolean.class), true);
                boolean affectEntities = _SchematicActionUtils.boolOrDefault(
                        getInput(context, StandardPorts.AFFECT_ENTITIES.getId(), Boolean.class), true);
                result = SchematicPlacementManager.repair(level, safeKey, DIRECT_SET_FLAGS, repairAir, affectEntities);
                SchematicPlacementRecord syncedRecord = SchematicPlacementManager.get(level, safeKey).orElse(currentRecord);
                _SchematicActionUtils.syncDebugBounds(level, safeKey, syncedRecord, level.getGameTime());
                logResult(level, safeKey, currentRecord, result);
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
            GeometryNode.LOGGER.warn("[GeometryNode] Repair schematic placement skipped: empty key and no placement at event position.");
        } else {
            GeometryNode.LOGGER.warn("[GeometryNode] Repair schematic placement skipped: no placement record for key '{}' in dimension '{}'.",
                    key, level.dimension().identifier());
        }
    }

    private static boolean affected(OperationResult result) {
        return result.found() && (result.blocks() > 0 || result.removedEntities() > 0 || result.respawnedEntities() > 0);
    }

    private static void logResult(ServerLevel level, String key, SchematicPlacementRecord record, OperationResult result) {
        if (!result.found()) {
            GeometryNode.LOGGER.warn("[GeometryNode] Repair schematic placement skipped: no placement record for key '{}' in dimension '{}'.",
                    key, level.dimension().identifier());
            return;
        }
        if (result.blocks() == 0 && result.respawnedEntities() == 0) {
            String origin = record != null ? formatPos(record.origin()) : "unknown";
            GeometryNode.LOGGER.warn("[GeometryNode] Repair schematic placement '{}' at {} found a record but changed no blocks/entities.",
                    key, origin);
            return;
        }
        if (record != null) {
            GeometryNode.LOGGER.info("[GeometryNode] Repaired schematic placement '{}' at {} in dimension '{}': {} blocks, {} entities.",
                    key, formatPos(record.origin()), level.dimension().identifier(), result.blocks(), result.respawnedEntities());
        }
    }

    private static String formatPos(net.minecraft.core.BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }
}
