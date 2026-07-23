package com.mine.geometry_node.core.node.nodes.actions.schematic;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.network.NetworkHandler;
import com.mine.geometry_node.core.network.packet.s2c.PacketSchematicProjection;
import com.mine.geometry_node.core.node.NodeData;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import com.mine.geometry_node.core.schematic.SchematicData;
import com.mine.geometry_node.core.schematic.LegacySchematicBlockStateMapper;
import com.mine.geometry_node.core.schematic.SchematicBlockEntityUtils;
import com.mine.geometry_node.core.schematic.SchematicPlacementManager;
import com.mine.geometry_node.core.schematic.SchematicPaths;
import com.mine.geometry_node.core.schematic.SchematicReader;
import com.mine.geometry_node.core.schematic.SchematicPlacementManager.BlockSnapshot;
import com.mine.geometry_node.core.schematic.SchematicPlacementManager.ChangedBlock;
import com.mine.geometry_node.core.schematic.SchematicPlacementManager.PlacedEntity;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityProcessor;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public class CreateSchematicProjection extends BaseNode {
    public static final String TYPE_ID = "create_schematic_projection";

    private static final int DEFAULT_MAX_BLOCKS = 8192;
    private static final int HARD_MAX_BLOCKS = 65536;
    private static final float DEFAULT_ALPHA = 0.38f;
    private static final float DEFAULT_VIEW_RANGE = 128.0f;
    private static final int DEFAULT_DURATION_TICKS = 200;
    private static final int DIRECT_SET_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
    private static final AtomicLong SEQUENCE = new AtomicLong();

    @Override
    public NodeDef getDefaultDefinition() {
        return buildDef(true);
    }

    @Override
    public NodeDef getDefinition(NodeData instanceData) {
        return buildDef(resolveDebugMode(instanceData));
    }

    private NodeDef buildDef(boolean debugMode) {
        String comment = """
                在指定坐标放置 .schem/.schematic。
                debug 开启时只向客户端发送投影视觉，不修改世界；关闭时会实际写入世界方块。
                path 是存档 geometry_nodes 目录下的相对结构文件路径，例如 demo/castle.schem。
                禁止绝对路径、路径前缀、. 和 ..；结构文件只能从服务器资产目录读取。
                xyz 是结构最小角坐标，执行时会吸附到方块网格。
                debug 模式支持普通方块、液体、可渲染方块实体和实体投影；key 相同会替换已有投影。
                放置模式下 replace_air 控制是否用结构空气清空目标位置，replace_blocks 控制是否覆盖已有非空气方块。
                放置模式会用 key 记录放置前后快照，供还原结构放置和修复结构放置使用。
                unique_if_exists 开启时，如果 key 已存在，会自动追加 _1、_2 等后缀，并从 key 输出实际名称。
                放置记录会自动维护结构包围盒；玩家可通过 /geometry_node debug schem on/off 控制是否可见。
                rotation 使用 Y 轴角度，按 90 度步进取整；mirror 的 X/Z 为负数时分别沿 X/Z 镜像。""";

        NodeDef.Builder builder = NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.create_schematic_projection"))
                .comment(comment);

        builder.addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null));
        builder.addRow(new PortRow(StandardPorts.PATH.toInput(""), StandardPorts.PATH.toOutput(), UIHint.PATH, null, null));
        builder.addRow(new PortRow(StandardPorts.KEY.toInput(""), StandardPorts.KEY.toOutput(), UIHint.INPUT, null, null));
        builder.addRow(new PortRow(StandardPorts.XYZ.toInput(Vec3.ZERO), null, UIHint.VECTOR, null, null));
        builder.addRow(new PortRow(StandardPorts.DEBUG.toInput(debugMode), null, UIHint.CHECKBOX, null, null));

        if (debugMode) {
            addDebugRows(builder);
        } else {
            addPlacementRows(builder);
        }

        return builder.build();
    }

    private static void addDebugRows(NodeDef.Builder builder) {
        builder.addRow(new PortRow(StandardPorts.ONLY_SELF_VISIBLE.toInput(true), null, UIHint.CHECKBOX, null, null));
        builder.addRow(new PortRow(StandardPorts.ALPHA.toInput(DEFAULT_ALPHA), null, UIHint.INPUT, null,
                Map.of(PortMetaKeys.NUMERIC_MIN, 0.05f, PortMetaKeys.NUMERIC_MAX, 1.0f)));
        builder.addRow(new PortRow(StandardPorts.TICK.toInput(DEFAULT_DURATION_TICKS), null, UIHint.INPUT, null,
                Map.of(PortMetaKeys.NUMERIC_MIN, 1)));
        builder.addRow(new PortRow(StandardPorts.RADIUS.toInput(DEFAULT_VIEW_RANGE), null, UIHint.INPUT, null,
                Map.of(PortMetaKeys.NUMERIC_MIN, 1.0f)));
        builder.addRow(new PortRow(StandardPorts.MAX_BLOCKS.toInput(DEFAULT_MAX_BLOCKS), null, UIHint.INPUT, null,
                Map.of(PortMetaKeys.NUMERIC_MIN, 1, PortMetaKeys.NUMERIC_MAX, HARD_MAX_BLOCKS)));
    }

    private static void addPlacementRows(NodeDef.Builder builder) {
        builder.addRow(new PortRow(StandardPorts.UNIQUE_IF_EXISTS.toInput(true), null, UIHint.CHECKBOX, null, null));
        builder.addRow(new PortRow(StandardPorts.REPLACE_AIR.toInput(false), null, UIHint.CHECKBOX, null, null));
        builder.addRow(new PortRow(StandardPorts.REPLACE_BLOCKS.toInput(true), null, UIHint.CHECKBOX, null, null));
        builder.addRow(new PortRow(StandardPorts.ROTATION.toInput(Vec3.ZERO), null, UIHint.VECTOR, null, null));
        builder.addRow(new PortRow(StandardPorts.MIRROR.toInput(new Vec3(1.0D, 1.0D, 1.0D)), null, UIHint.VECTOR, null, null));
        builder.addRow(new PortRow(StandardPorts.MAX_BLOCKS.toInput(DEFAULT_MAX_BLOCKS), null, UIHint.INPUT, null,
                Map.of(PortMetaKeys.NUMERIC_MIN, 1, PortMetaKeys.NUMERIC_MAX, HARD_MAX_BLOCKS)));
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        ServerLevel level = context.getLevel();
        if (level == null) {
            return next(StandardPorts.FLOW_OUT.getId());
        }

        String pathValue = getInput(context, StandardPorts.PATH.getId(), String.class);
        Vec3 originValue = getInput(context, StandardPorts.XYZ.getId(), Vec3.class);
        if (pathValue == null || pathValue.trim().isEmpty() || originValue == null) {
            return next(StandardPorts.FLOW_OUT.getId());
        }

        boolean debugMode = boolOrDefault(getInput(context, StandardPorts.DEBUG.getId(), Boolean.class), true);
        int maxBlocks = clampInt(getInput(context, StandardPorts.MAX_BLOCKS.getId(), Integer.class), DEFAULT_MAX_BLOCKS, 1, HARD_MAX_BLOCKS);
        String key = resolveKey(context, level);

        try {
            Path path = SchematicPaths.resolveServerPath(level.getServer(), pathValue);
            String actualKey = key;
            if (debugMode) {
                SchematicData data = SchematicReader.read(path, maxBlocks, false);
                createDebugProjection(context, level, data, originValue, key);
            } else {
                boolean replaceAir = boolOrDefault(getInput(context, StandardPorts.REPLACE_AIR.getId(), Boolean.class), false);
                boolean uniqueIfExists = boolOrDefault(getInput(context, StandardPorts.UNIQUE_IF_EXISTS.getId(), Boolean.class), true);
                actualKey = SchematicPlacementManager.resolveKey(level, key, uniqueIfExists);
                SchematicData data = SchematicReader.read(path, maxBlocks, replaceAir);
                actualKey = placeSchematic(context, level, data, originValue, actualKey, replaceAir);
            }
            context.setTempData(tempKey(context), actualKey);
        } catch (Exception e) {
            GeometryNode.LOGGER.warn("[GeometryNode] Failed to place schematic projection from '{}': {}", pathValue, e.getMessage());
        }

        return next(StandardPorts.FLOW_OUT.getId());
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        if (StandardPorts.KEY.getId().equals(portName)) {
            return context.getTempData(tempKey(context));
        }
        if (StandardPorts.PATH.getId().equals(portName)) {
            return getInput(context, StandardPorts.PATH.getId(), String.class);
        }
        return null;
    }

    private void createDebugProjection(ExecutionContext context,
                                       ServerLevel level,
                                       SchematicData data,
                                       Vec3 originValue,
                                       String key) {
        if (data.isEmpty()) {
            return;
        }

        int durationTicks = durationTicks(getInput(context, StandardPorts.TICK.getId(), Integer.class));
        float alpha = clampFloat(getInput(context, StandardPorts.ALPHA.getId(), Float.class), DEFAULT_ALPHA, 0.05f, 1.0f);
        float viewRange = clampFloat(getInput(context, StandardPorts.RADIUS.getId(), Float.class), DEFAULT_VIEW_RANGE, 1.0f, 4096.0f);
        boolean onlySelfVisible = boolOrDefault(getInput(context, StandardPorts.ONLY_SELF_VISIBLE.getId(), Boolean.class), true);

        BlockPos origin = BlockPos.containing(originValue);
        PacketPayloadData payloadData = toPacketPayload(data.blocks());
        PacketSchematicProjection packet = new PacketSchematicProjection(
                key,
                context.getGraphId(),
                level.dimension().identifier().toString(),
                origin.getX(),
                origin.getY(),
                origin.getZ(),
                data.width(),
                data.height(),
                data.length(),
                durationTicks,
                alpha,
                data.truncated(),
                payloadData.states(),
                payloadData.blocks(),
                toPacketBlockEntities(data.blockEntities()),
                toPacketEntities(data.entities())
        );

        if (!sendProjection(context, level, origin.getCenter(), viewRange, onlySelfVisible, packet)) {
            GeometryNode.LOGGER.warn("[GeometryNode] Schematic projection '{}' was created but no target player was available.", key);
        }
    }

    private String placeSchematic(ExecutionContext context,
                                  ServerLevel level,
                                  SchematicData data,
                                  Vec3 originValue,
                                  String key,
                                  boolean replaceAir) {
        if (data.isEmpty()) {
            GeometryNode.LOGGER.warn("[GeometryNode] Schematic placement '{}' read no blocks/entities; revert/repair will have nothing to target.", key);
            return key;
        }

        boolean replaceBlocks = boolOrDefault(getInput(context, StandardPorts.REPLACE_BLOCKS.getId(), Boolean.class), true);
        Transform transform = Transform.from(
                data.width(),
                data.length(),
                getInput(context, StandardPorts.ROTATION.getId(), Vec3.class),
                getInput(context, StandardPorts.MIRROR.getId(), Vec3.class)
        );
        BlockPos origin = BlockPos.containing(originValue);
        LongOpenHashSet loadedChunks = new LongOpenHashSet();
        LongOpenHashSet unloadedChunks = new LongOpenHashSet();
        LongOpenHashSet placedBlocks = new LongOpenHashSet();
        Map<Long, BlockSnapshot> beforeSnapshots = new HashMap<>();
        Map<String, BlockState> stateCache = new HashMap<>();

        for (SchematicData.Block block : data.blocks()) {
            BlockState state = parseBlockState(block.state(), stateCache);
            if (state == null) {
                continue;
            }

            BlockPos worldPos = transform.blockPos(origin, block.x(), block.y(), block.z());
            if (!isLoaded(level, worldPos, loadedChunks, unloadedChunks)) {
                continue;
            }

            if (state.isAir()) {
                if (!replaceAir) {
                    continue;
                }
            } else {
                state = transform.state(state);
                if (!replaceBlocks && !level.isEmptyBlock(worldPos)) {
                    continue;
                }
            }

            BlockSnapshot before = SchematicPlacementManager.captureBlock(level, worldPos);
            boolean sameState = before.state().equals(state);
            if (level.setBlock(worldPos, state, DIRECT_SET_FLAGS) || sameState) {
                long posKey = worldPos.asLong();
                placedBlocks.add(posKey);
                beforeSnapshots.put(posKey, before);
            }
        }

        int failedBlockEntities = placeBlockEntities(level, data.blockEntities(), origin, transform, placedBlocks);
        if (failedBlockEntities > 0) {
            GeometryNode.LOGGER.warn("[GeometryNode] Schematic placement '{}' failed to apply {} block entities.",
                    key, failedBlockEntities);
        }
        List<PlacedEntity> spawnedEntities = placeEntities(level, data.entities(), origin, transform);
        SchematicPlacementManager.SchematicPlacementRecord record = recordPlacement(
                context, level, data, origin, transform, key, placedBlocks, beforeSnapshots, spawnedEntities);
        if (record != null) {
            _SchematicActionUtils.syncDebugBounds(level, record.key(), record, level.getGameTime());
            GeometryNode.LOGGER.info("[GeometryNode] Schematic placement '{}' recorded {} blocks and {} entities at {} in dimension '{}'.",
                    record.key(), record.changedBlocks().size(), record.spawnedEntities().size(),
                    formatPos(record.origin()), level.dimension().identifier());
            return record.key();
        }
        GeometryNode.LOGGER.warn("[GeometryNode] Schematic placement '{}' created no placement record; revert/repair will have nothing to target.", key);
        return key;
    }

    private static int placeBlockEntities(ServerLevel level,
                                           List<SchematicData.BlockEntity> blockEntities,
                                           BlockPos origin,
                                           Transform transform,
                                           LongOpenHashSet placedBlocks) {
        int failed = 0;
        for (SchematicData.BlockEntity blockEntityData : blockEntities) {
            BlockPos worldPos = transform.blockPos(origin, blockEntityData.x(), blockEntityData.y(), blockEntityData.z());
            if (!placedBlocks.contains(worldPos.asLong())) {
                continue;
            }

            BlockState state = level.getBlockState(worldPos);
            if (!state.hasBlockEntity()) {
                continue;
            }

            if (SchematicBlockEntityUtils.setBlockEntity(level, worldPos, state, blockEntityData.tag())) {
                level.sendBlockUpdated(worldPos, state, state, DIRECT_SET_FLAGS);
            } else {
                failed++;
            }
        }
        return failed;
    }

    private static List<PlacedEntity> placeEntities(ServerLevel level,
                                                    List<SchematicData.Entity> entities,
                                                    BlockPos origin,
                                                    Transform transform) {
        List<PlacedEntity> spawnedEntities = new ArrayList<>();
        for (SchematicData.Entity entityData : entities) {
            Vec3 worldPos = transform.entityPos(origin, entityData.x(), entityData.y(), entityData.z());
            try {
                CompoundTag tag = absoluteEntityTag(entityData.tag(), worldPos);
                Entity entity = EntityType.loadEntityRecursive(tag, level, EntitySpawnReason.COMMAND, EntityProcessor.NOP);
                if (entity != null) {
                    entity.snapTo(worldPos.x, worldPos.y, worldPos.z,
                            transform.yaw(entity.getYRot()),
                            entity.getXRot());
                    if (level.tryAddFreshEntityWithPassengers(entity)) {
                        spawnedEntities.add(new PlacedEntity(entity.getUUID(), tag));
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return spawnedEntities;
    }

    private static SchematicPlacementManager.SchematicPlacementRecord recordPlacement(ExecutionContext context,
                                                                                     ServerLevel level,
                                                                                     SchematicData data,
                                                                                     BlockPos origin,
                                                                                     Transform transform,
                                                                                     String key,
                                                                                     LongOpenHashSet placedBlocks,
                                                                                     Map<Long, BlockSnapshot> beforeSnapshots,
                                                                                     List<PlacedEntity> spawnedEntities) {
        if (placedBlocks.isEmpty() && spawnedEntities.isEmpty()) {
            return null;
        }

        List<ChangedBlock> changedBlocks = new ArrayList<>(placedBlocks.size());
        for (long posLong : placedBlocks) {
            BlockSnapshot before = beforeSnapshots.get(posLong);
            if (before == null) {
                continue;
            }
            changedBlocks.add(new ChangedBlock(before, SchematicPlacementManager.captureBlock(level, BlockPos.of(posLong))));
        }

        SchematicPlacementManager.SchematicPlacementRecord record = new SchematicPlacementManager.SchematicPlacementRecord(
                key,
                context.getGraphId(),
                level.dimension(),
                origin,
                transform.boundsMin(origin, data.height()),
                transform.boundsMax(origin, data.height()),
                data.width(),
                data.height(),
                data.length(),
                transform.info(),
                changedBlocks,
                spawnedEntities,
                level.getGameTime()
        );
        return SchematicPlacementManager.put(level, record);
    }

    private static PacketPayloadData toPacketPayload(List<SchematicData.Block> blocks) {
        LinkedHashMap<String, Integer> palette = new LinkedHashMap<>();
        List<PacketSchematicProjection.Block> packetBlocks = new ArrayList<>(blocks.size());
        for (SchematicData.Block block : blocks) {
            if (block.color() == 0) {
                continue;
            }
            int stateIndex = palette.computeIfAbsent(block.state(), ignored -> palette.size());
            packetBlocks.add(new PacketSchematicProjection.Block(block.x(), block.y(), block.z(), stateIndex, block.color()));
        }
        return new PacketPayloadData(new ArrayList<>(palette.keySet()), packetBlocks);
    }

    private static List<PacketSchematicProjection.BlockEntity> toPacketBlockEntities(List<SchematicData.BlockEntity> blockEntities) {
        List<PacketSchematicProjection.BlockEntity> packetBlockEntities = new ArrayList<>(blockEntities.size());
        for (SchematicData.BlockEntity blockEntity : blockEntities) {
            packetBlockEntities.add(new PacketSchematicProjection.BlockEntity(blockEntity.x(), blockEntity.y(), blockEntity.z(), blockEntity.tag()));
        }
        return packetBlockEntities;
    }

    private static List<PacketSchematicProjection.Entity> toPacketEntities(List<SchematicData.Entity> entities) {
        List<PacketSchematicProjection.Entity> packetEntities = new ArrayList<>(entities.size());
        for (SchematicData.Entity entity : entities) {
            packetEntities.add(new PacketSchematicProjection.Entity(entity.x(), entity.y(), entity.z(), entity.tag()));
        }
        return packetEntities;
    }

    private static boolean sendProjection(ExecutionContext context,
                                          ServerLevel level,
                                          Vec3 origin,
                                          float viewRange,
                                          boolean onlySelfVisible,
                                          PacketSchematicProjection packet) {
        if (onlySelfVisible) {
            ServerPlayer player = resolveSelfVisiblePlayer(context);
            if (player != null && player.level() == level) {
                NetworkHandler.sendToPlayer(player, packet);
                return true;
            }
            return false;
        }
        return sendToNearbyPlayers(level, origin, viewRange, packet);
    }

    private static boolean sendToNearbyPlayers(ServerLevel level, Vec3 origin, float viewRange, PacketSchematicProjection packet) {
        double radiusSqr = (double) viewRange * viewRange;
        List<ServerPlayer> targets = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            if (player.position().distanceToSqr(origin) <= radiusSqr) {
                targets.add(player);
            }
        }
        if (!targets.isEmpty()) {
            NetworkHandler.sendToPlayers(targets, packet);
            return true;
        }
        return false;
    }

    private static ServerPlayer resolveSelfVisiblePlayer(ExecutionContext context) {
        if (context.getEntity() instanceof ServerPlayer player) {
            return player;
        }
        Object playerData = context.getEventData(StandardPorts.PLAYER.getId());
        if (playerData instanceof ServerPlayer player) {
            return player;
        }
        Object triggerEntity = context.getEventData(StandardPorts.TRIGGER_ENTITY.getId());
        if (triggerEntity instanceof ServerPlayer player) {
            return player;
        }
        Object entity = context.getEventData(StandardPorts.ENTITY.getId());
        if (entity instanceof ServerPlayer player) {
            return player;
        }
        return null;
    }

    private String resolveKey(ExecutionContext context, ServerLevel level) {
        String configured = getInput(context, StandardPorts.KEY.getId(), String.class);
        String baseKey = configured != null ? configured.trim() : "";
        if (baseKey.isEmpty()) {
            return uniqueKey(context, level, "schematic_projection");
        }
        return baseKey;
    }

    private static String uniqueKey(ExecutionContext context, ServerLevel level, String prefix) {
        String stableId = context.getCurrentNodeStableId();
        String nodePart = stableId != null && !stableId.isBlank() ? stableId : Integer.toString(context.getCurrentNodeId());
        return prefix + ":" + nodePart + ":" + level.getGameTime() + ":" + SEQUENCE.incrementAndGet();
    }

    private static String formatPos(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static String tempKey(ExecutionContext context) {
        return TYPE_ID + ":key:" + context.getCurrentNodeId();
    }

    private static boolean resolveDebugMode(NodeData data) {
        if (data == null || data.inputs == null) {
            return true;
        }
        Object raw = data.inputs.get(StandardPorts.DEBUG.getId());
        if (raw instanceof Boolean bool) {
            return bool;
        }
        if (raw instanceof String string) {
            return Boolean.parseBoolean(string);
        }
        return true;
    }

    private static boolean isLoaded(ServerLevel level,
                                    BlockPos pos,
                                    LongOpenHashSet loadedChunks,
                                    LongOpenHashSet unloadedChunks) {
        long chunkKey = ChunkPos.pack(pos.getX() >> 4, pos.getZ() >> 4);
        if (unloadedChunks.contains(chunkKey)) {
            return false;
        }
        if (loadedChunks.contains(chunkKey)) {
            return true;
        }
        if (!level.isLoaded(pos)) {
            unloadedChunks.add(chunkKey);
            return false;
        }
        loadedChunks.add(chunkKey);
        return true;
    }

    private static BlockState parseBlockState(String raw, Map<String, BlockState> stateCache) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        if (stateCache != null && stateCache.containsKey(raw)) {
            return stateCache.get(raw);
        }

        BlockState parsed = parseBlockStateUncached(raw);
        if (stateCache != null && parsed != null) {
            stateCache.put(raw, parsed);
        }
        return parsed;
    }

    private static BlockState parseBlockStateUncached(String raw) {
        if (raw.startsWith("legacy:")) {
            return LegacySchematicBlockStateMapper.fromRaw(raw);
        }
        try {
            return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, raw, false).blockState();
        } catch (Exception ignored) {
        }

        try {
            String id = raw;
            int bracket = id.indexOf('[');
            if (bracket >= 0) {
                id = id.substring(0, bracket);
            }
            Identifier identifier = Identifier.tryParse(id);
            if (identifier == null) {
                return null;
            }
            Block block = BuiltInRegistries.BLOCK.getValue(identifier);
            return block != null ? block.defaultBlockState() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static CompoundTag absoluteEntityTag(CompoundTag source, Vec3 worldPos) {
        CompoundTag tag = source == null ? new CompoundTag() : source.copy();
        ListTag pos = new ListTag();
        pos.add(DoubleTag.valueOf(worldPos.x));
        pos.add(DoubleTag.valueOf(worldPos.y));
        pos.add(DoubleTag.valueOf(worldPos.z));
        tag.put("Pos", pos);
        tag.putDouble("x", worldPos.x);
        tag.putDouble("y", worldPos.y);
        tag.putDouble("z", worldPos.z);
        tag.remove("UUID");
        tag.remove("UUIDMost");
        tag.remove("UUIDLeast");
        return tag;
    }

    private static int clampInt(Integer value, int fallback, int min, int max) {
        int raw = value != null ? value : fallback;
        return Math.max(min, Math.min(max, raw));
    }

    private static float clampFloat(Float value, float fallback, float min, float max) {
        float raw = value != null ? value : fallback;
        if (!Float.isFinite(raw)) raw = fallback;
        return Math.max(min, Math.min(max, raw));
    }

    private static boolean boolOrDefault(Boolean value, boolean fallback) {
        return value != null ? value : fallback;
    }

    private static int durationTicks(Integer value) {
        if (value == null || value <= 0) {
            return DEFAULT_DURATION_TICKS;
        }
        return Math.max(1, value);
    }

    private record PacketPayloadData(
            List<String> states,
            List<PacketSchematicProjection.Block> blocks
    ) {
    }

    private record Transform(int width, int length, boolean mirrorX, boolean mirrorZ, int rotationSteps) {
        private static Transform from(int width, int length, Vec3 rotation, Vec3 mirror) {
            double rotationY = rotation != null ? rotation.y : 0.0D;
            int steps = Math.floorMod((int) Math.round(rotationY / 90.0D), 4);
            boolean mirrorX = mirror != null && mirror.x < 0.0D;
            boolean mirrorZ = mirror != null && mirror.z < 0.0D;
            return new Transform(Math.max(1, width), Math.max(1, length), mirrorX, mirrorZ, steps);
        }

        private BlockPos blockPos(BlockPos origin, int x, int y, int z) {
            LocalXZ transformed = localBlockXZ(x, z);
            return origin.offset(transformed.x(), y, transformed.z());
        }

        private Vec3 entityPos(BlockPos origin, double x, double y, double z) {
            LocalDoubleXZ transformed = localEntityXZ(x, z);
            return new Vec3(origin.getX() + transformed.x(), origin.getY() + y, origin.getZ() + transformed.z());
        }

        private BlockState state(BlockState state) {
            BlockState transformed = state;
            if (mirrorX) {
                transformed = transformed.mirror(Mirror.FRONT_BACK);
            }
            if (mirrorZ) {
                transformed = transformed.mirror(Mirror.LEFT_RIGHT);
            }
            return transformed.rotate(rotation());
        }

        private float yaw(float yaw) {
            float transformed = yaw;
            if (mirrorX) {
                transformed = 180.0F - transformed;
            }
            if (mirrorZ) {
                transformed = -transformed;
            }
            return transformed + rotationSteps * 90.0F;
        }

        private Rotation rotation() {
            return switch (rotationSteps) {
                case 1 -> Rotation.CLOCKWISE_90;
                case 2 -> Rotation.CLOCKWISE_180;
                case 3 -> Rotation.COUNTERCLOCKWISE_90;
                default -> Rotation.NONE;
            };
        }

        private SchematicPlacementManager.TransformInfo info() {
            return new SchematicPlacementManager.TransformInfo(mirrorX, mirrorZ, rotationSteps);
        }

        private BlockPos boundsMin(BlockPos origin, int height) {
            BoundsXZ bounds = boundsXZ();
            return new BlockPos(
                    origin.getX() + bounds.minX(),
                    origin.getY(),
                    origin.getZ() + bounds.minZ()
            );
        }

        private BlockPos boundsMax(BlockPos origin, int height) {
            BoundsXZ bounds = boundsXZ();
            return new BlockPos(
                    origin.getX() + bounds.maxX(),
                    origin.getY() + Math.max(1, height) - 1,
                    origin.getZ() + bounds.maxZ()
            );
        }

        private LocalXZ localBlockXZ(int x, int z) {
            int tx = mirrorX ? width - 1 - x : x;
            int tz = mirrorZ ? length - 1 - z : z;
            return switch (rotationSteps) {
                case 1 -> new LocalXZ(length - 1 - tz, tx);
                case 2 -> new LocalXZ(width - 1 - tx, length - 1 - tz);
                case 3 -> new LocalXZ(tz, width - 1 - tx);
                default -> new LocalXZ(tx, tz);
            };
        }

        private LocalDoubleXZ localEntityXZ(double x, double z) {
            double tx = mirrorX ? width - x : x;
            double tz = mirrorZ ? length - z : z;
            return switch (rotationSteps) {
                case 1 -> new LocalDoubleXZ(length - tz, tx);
                case 2 -> new LocalDoubleXZ(width - tx, length - tz);
                case 3 -> new LocalDoubleXZ(tz, width - tx);
                default -> new LocalDoubleXZ(tx, tz);
            };
        }

        private BoundsXZ boundsXZ() {
            LocalXZ a = localBlockXZ(0, 0);
            LocalXZ b = localBlockXZ(width - 1, 0);
            LocalXZ c = localBlockXZ(0, length - 1);
            LocalXZ d = localBlockXZ(width - 1, length - 1);
            int minX = Math.min(Math.min(a.x(), b.x()), Math.min(c.x(), d.x()));
            int maxX = Math.max(Math.max(a.x(), b.x()), Math.max(c.x(), d.x()));
            int minZ = Math.min(Math.min(a.z(), b.z()), Math.min(c.z(), d.z()));
            int maxZ = Math.max(Math.max(a.z(), b.z()), Math.max(c.z(), d.z()));
            return new BoundsXZ(minX, minZ, maxX, maxZ);
        }
    }

    private record LocalXZ(int x, int z) {
    }

    private record LocalDoubleXZ(double x, double z) {
    }

    private record BoundsXZ(int minX, int minZ, int maxX, int maxZ) {
    }
}
