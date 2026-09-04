package com.mine.geometry_node.core.node.nodes.actions.schematic;

import com.mine.geometry_node.core.engine.graph.data.GraphDataContext;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.network.packet.s2c.PacketSchematicProjection;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceId;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceIdCodec;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceIds;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceTypeRegistry;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.definition.node.NodeComment;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import com.mine.geometry_node.core.schematic.SchematicData;
import com.mine.geometry_node.core.schematic.LegacySchematicBlockStateMapper;
import com.mine.geometry_node.core.schematic.SchematicBlockEntityUtils;
import com.mine.geometry_node.core.engine.system.schematic.SchematicPlacementDebugSync;
import com.mine.geometry_node.core.engine.system.schematic.SchematicPlacementManager;
import com.mine.geometry_node.core.engine.system.schematic.SchematicProjectionService;
import com.mine.geometry_node.core.schematic.SchematicPaths;
import com.mine.geometry_node.core.schematic.SchematicReader;
import com.mine.geometry_node.core.engine.system.schematic.SchematicPlacementManager.BlockSnapshot;
import com.mine.geometry_node.core.engine.system.schematic.SchematicPlacementManager.ChangedBlock;
import com.mine.geometry_node.core.engine.system.schematic.SchematicPlacementManager.PlacedEntity;
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

public class CreateSchematicProjection extends BaseNode {
    public static final String TYPE_ID = "create_schematic_projection";

    private static final float DEFAULT_ALPHA = 0.38f;
    private static final float DEFAULT_VIEW_RANGE = 128.0f;
    private static final int DEFAULT_DURATION_TICKS = 200;
    private static final int DIRECT_SET_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

    @Override
    public NodeDef getDefaultDefinition() {
        return buildDef(true);
    }

    @Override
    public NodeDef getDefinition(NodeData instanceData) {
        return buildDef(resolveDebugMode(instanceData));
    }

    private NodeDef buildDef(boolean debugMode) {
        NodeComment.Builder comment = NodeComment.builder(TYPE_ID)
                .text("summary")
                .output(StandardPorts.FLOW_OUT, "flow_out")
                .output(StandardPorts.PATH, "path_out")
                .output(StandardPorts.RESULT_RESOURCE_ID, "resource_id")
                .input(StandardPorts.FLOW_IN, "flow_in")
                .input(StandardPorts.PATH, "path")
                .input(StandardPorts.KEY, "key")
                .input(StandardPorts.XYZ, "xyz")
                .input(StandardPorts.DEBUG, "debug");

        if (debugMode) {
            comment.input(StandardPorts.ONLY_SELF_VISIBLE, "only_self_visible")
                    .input(StandardPorts.ALPHA, "alpha")
                    .input(StandardPorts.TICK, "tick")
                    .input(StandardPorts.RADIUS, "radius");
        } else {
            comment.input(StandardPorts.UNIQUE_IF_EXISTS, "unique_if_exists")
                    .input(StandardPorts.REPLACE_AIR, "replace_air")
                    .input(StandardPorts.REPLACE_BLOCKS, "replace_blocks")
                    .input(StandardPorts.ROTATION, "rotation")
                    .input(StandardPorts.MIRROR, "mirror");
        }

        NodeDef.Builder builder = NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.create_schematic_projection"))
                .comment(comment.build());

        builder.addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null));
        builder.addRow(new PortRow(null, StandardPorts.RESULT_RESOURCE_ID.toOutput(),
                UIHint.DEFAULT, null, null));
        builder.addPassthroughInput(StandardPorts.PATH.toInput(""), UIHint.PATH);
        builder.addPassthroughInput(StandardPorts.KEY.toInput(""), UIHint.INPUT);
        builder.addPassthroughInput(StandardPorts.XYZ.toInput(Vec3.ZERO), UIHint.VECTOR);
        builder.addPassthroughInput(StandardPorts.DEBUG.toInput(debugMode), UIHint.CHECKBOX);

        if (debugMode) {
            addDebugRows(builder);
        } else {
            addPlacementRows(builder);
        }

        return builder.build();
    }

    private static void addDebugRows(NodeDef.Builder builder) {
        builder.addPassthroughInput(StandardPorts.ONLY_SELF_VISIBLE.toInput(true), UIHint.CHECKBOX);
        builder.addPassthroughInput(StandardPorts.ALPHA.toInput(DEFAULT_ALPHA), UIHint.INPUT, null, Map.of(PortMetaKeys.NUMERIC_MIN, 0.05f, PortMetaKeys.NUMERIC_MAX, 1.0f));
        builder.addPassthroughInput(StandardPorts.TICK.toInput(DEFAULT_DURATION_TICKS), UIHint.INPUT, null, Map.of(PortMetaKeys.NUMERIC_MIN, 1));
        builder.addPassthroughInput(StandardPorts.RADIUS.toInput(DEFAULT_VIEW_RANGE), UIHint.INPUT, null, Map.of(PortMetaKeys.NUMERIC_MIN, 1.0f));
    }

    private static void addPlacementRows(NodeDef.Builder builder) {
        builder.addPassthroughInput(StandardPorts.UNIQUE_IF_EXISTS.toInput(true), UIHint.CHECKBOX);
        builder.addPassthroughInput(StandardPorts.REPLACE_AIR.toInput(false), UIHint.CHECKBOX);
        builder.addPassthroughInput(StandardPorts.REPLACE_BLOCKS.toInput(true), UIHint.CHECKBOX);
        builder.addPassthroughInput(StandardPorts.ROTATION.toInput(Vec3.ZERO), UIHint.VECTOR);
        builder.addPassthroughInput(StandardPorts.MIRROR.toInput(new Vec3(1.0D, 1.0D, 1.0D)), UIHint.VECTOR);
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        context.setNodeResult(StandardPorts.RESULT_RESOURCE_ID.getId(), null);
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
        String configuredKey = getInput(context, StandardPorts.KEY.getId(), String.class);
        configuredKey = configuredKey == null ? "" : configuredKey.trim();

        try {
            Path path = SchematicPaths.resolveServerPath(level.getServer(), pathValue);
            String actualKey;
            if (debugMode) {
                GraphResourceId resourceId = GraphResourceIds.forKey(context, stableNodeId(context),
                        GraphResourceTypeRegistry.SCHEMATIC_PROJECTION, configuredKey);
                actualKey = GraphResourceIdCodec.encode(resourceId);
                SchematicData data = SchematicReader.read(path, false);
                createDebugProjection(context, level, data, originValue, resourceId);
            } else {
                String placementKey = configuredKey.isEmpty() ? defaultPlacementKey(context) : configuredKey;
                boolean replaceAir = boolOrDefault(getInput(context, StandardPorts.REPLACE_AIR.getId(), Boolean.class), false);
                boolean uniqueIfExists = boolOrDefault(getInput(context, StandardPorts.UNIQUE_IF_EXISTS.getId(), Boolean.class), true);
                actualKey = SchematicPlacementManager.resolveKey(level, placementKey, uniqueIfExists);
                SchematicData data = SchematicReader.read(path, replaceAir);
                actualKey = placeSchematic(context, level, data, originValue, actualKey, replaceAir);
            }
            context.setNodeResult(StandardPorts.RESULT_RESOURCE_ID.getId(), actualKey);
        } catch (Exception e) {
            GeometryNode.LOGGER.warn("[GeometryNode] Failed to place schematic projection from '{}': {}", pathValue, e.getMessage());
        }

        return next(StandardPorts.FLOW_OUT.getId());
    }

    @Override
    public Object compute(GraphDataContext context, String portName) {
        if (StandardPorts.RESULT_RESOURCE_ID.getId().equals(portName)) {
            return context.getNodeResult(StandardPorts.RESULT_RESOURCE_ID.getId());
        }
        return null;
    }

    private void createDebugProjection(ExecutionContext context,
                                       ServerLevel level,
                                       SchematicData data,
                                       Vec3 originValue,
                                       GraphResourceId resourceId) {
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
                GraphResourceIdCodec.encode(resourceId),
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

        List<ServerPlayer> targets = projectionTargets(context, level, origin.getCenter(), viewRange, onlySelfVisible);
        if (!SchematicProjectionService.INSTANCE.upsert(level.getServer(), resourceId, packet, targets,
                level.getGameTime())) {
            GeometryNode.LOGGER.warn("[GeometryNode] Schematic projection '{}' was created but no target player was available.",
                    GraphResourceIdCodec.encode(resourceId));
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
            SchematicPlacementDebugSync.syncRecord(level, record.key(), record);
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

    private static List<ServerPlayer> projectionTargets(ExecutionContext context,
                                                        ServerLevel level,
                                                        Vec3 origin,
                                                        float viewRange,
                                                        boolean onlySelfVisible) {
        if (onlySelfVisible) {
            ServerPlayer player = resolveSelfVisiblePlayer(context);
            if (player != null && player.level() == level) {
                return List.of(player);
            }
            return List.of();
        }
        double radiusSqr = (double) viewRange * viewRange;
        List<ServerPlayer> targets = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            if (player.position().distanceToSqr(origin) <= radiusSqr) {
                targets.add(player);
            }
        }
        return targets;
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

    private static String defaultPlacementKey(ExecutionContext context) {
        return "schematic_projection/" + stableNodeId(context);
    }

    private static String stableNodeId(ExecutionContext context) {
        String stableId = context.getCurrentNodeStableId();
        return stableId != null && !stableId.isBlank()
                ? stableId : Integer.toString(context.getCurrentNodeId());
    }

    private static String formatPos(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
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
