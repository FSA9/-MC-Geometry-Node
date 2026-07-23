package com.mine.geometry_node.core.schematic;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityProcessor;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class SchematicPlacementManager {
    private SchematicPlacementManager() {
    }

    public static SchematicPlacementRecord put(ServerLevel level, SchematicPlacementRecord record) {
        if (level == null || record == null || record.key().isBlank()) {
            return record;
        }
        return SchematicPlacementStorage.get(level).put(record);
    }

    public static String resolveKey(ServerLevel level, String requestedKey, boolean uniqueIfExists) {
        String baseKey = requestedKey == null ? "" : requestedKey.trim();
        if (level == null || baseKey.isEmpty() || !uniqueIfExists) {
            return baseKey;
        }
        return SchematicPlacementStorage.get(level).resolveKey(level.dimension(), baseKey, true);
    }

    public static Optional<SchematicPlacementRecord> get(ServerLevel level, String key) {
        if (level == null || key == null || key.isBlank()) {
            return Optional.empty();
        }
        return SchematicPlacementStorage.get(level).get(level.dimension(), key.trim());
    }

    public static Optional<SchematicPlacementRecord> findContaining(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) {
            return Optional.empty();
        }
        return SchematicPlacementStorage.get(level).findContaining(level.dimension(), pos);
    }

    public static boolean containsBlock(ServerLevel level, SchematicPlacementRecord record, BlockPos pos) {
        if (level == null || record == null || pos == null) {
            return false;
        }
        return SchematicPlacementStorage.get(level).containsBlock(record.dimension(), record.key(), pos);
    }

    public static boolean containsBlock(SchematicPlacementRecord record, BlockPos pos) {
        if (record == null || pos == null || !withinBounds(record, pos)) {
            return false;
        }
        for (ChangedBlock block : record.changedBlocks()) {
            if (block.pos().equals(pos)) {
                return true;
            }
        }
        return false;
    }

    public static Optional<SchematicPlacementRecord> remove(ServerLevel level, String key) {
        if (level == null || key == null || key.isBlank()) {
            return Optional.empty();
        }
        return SchematicPlacementStorage.get(level).remove(level.dimension(), key.trim());
    }

    public static int clearLevel(ServerLevel level) {
        if (level == null) {
            return 0;
        }
        return SchematicPlacementStorage.get(level).clearDimension(level.dimension());
    }

    public static OperationResult revert(ServerLevel level, String key, int flags) {
        return revert(level, key, flags, true);
    }

    public static OperationResult revert(ServerLevel level, String key, int flags, boolean affectEntities) {
        Optional<SchematicPlacementRecord> optional = get(level, key);
        if (optional.isEmpty()) {
            return OperationResult.missing();
        }

        SchematicPlacementRecord record = optional.get();
        int restoredBlocks = 0;
        int matchedBlocks = 0;
        Map<String, Object> blockStats = new LinkedHashMap<>();
        for (ChangedBlock block : record.changedBlocks()) {
            ApplyResult applyResult = applySnapshot(level, block.before(), flags);
            if (applyResult.matched()) {
                matchedBlocks++;
            }
            if (applyResult.changed()) {
                restoredBlocks++;
                incrementBlockStat(blockStats, block.before().state());
            }
        }

        int removedEntities = 0;
        if (affectEntities) {
            for (PlacedEntity entity : record.spawnedEntities()) {
                Entity existing = level.getEntity(entity.uuid());
                if (existing != null) {
                    existing.discard();
                    removedEntities++;
                }
            }
        }
        if (matchedBlocks == record.changedBlocks().size()) {
            remove(level, key);
        }
        return new OperationResult(true, restoredBlocks, removedEntities, 0, blockStats);
    }

    public static OperationResult repair(ServerLevel level, String key, int flags) {
        return repair(level, key, flags, true, true);
    }

    public static OperationResult repair(ServerLevel level, String key, int flags, boolean repairAir, boolean affectEntities) {
        Optional<SchematicPlacementRecord> optional = get(level, key);
        if (optional.isEmpty()) {
            return OperationResult.missing();
        }

        SchematicPlacementRecord record = optional.get();
        int repairedBlocks = 0;
        Map<String, Object> blockStats = new LinkedHashMap<>();
        for (ChangedBlock block : record.changedBlocks()) {
            if (!repairAir && block.after().state().isAir()) {
                continue;
            }
            ApplyResult applyResult = applySnapshot(level, block.after(), flags);
            if (applyResult.changed()) {
                repairedBlocks++;
                incrementBlockStat(blockStats, block.after().state());
            }
        }

        int respawnedEntities = 0;
        List<PlacedEntity> repairedEntities = new ArrayList<>(record.spawnedEntities().size());
        boolean entityIdsChanged = false;
        if (affectEntities) {
            for (PlacedEntity placedEntity : record.spawnedEntities()) {
                Entity existing = level.getEntity(placedEntity.uuid());
                if (existing != null) {
                    repairedEntities.add(placedEntity);
                    continue;
                }
                boolean respawned = false;
                try {
                    Entity entity = EntityType.loadEntityRecursive(placedEntity.spawnTag(), level,
                            EntitySpawnReason.COMMAND, EntityProcessor.NOP);
                    if (entity != null && level.tryAddFreshEntityWithPassengers(entity)) {
                        repairedEntities.add(new PlacedEntity(entity.getUUID(), placedEntity.spawnTag()));
                        entityIdsChanged = true;
                        respawned = true;
                        respawnedEntities++;
                    }
                } catch (Exception ignored) {
                }
                if (!respawned) {
                    repairedEntities.add(placedEntity);
                }
            }
        }
        if (affectEntities && entityIdsChanged) {
            put(level, record.withSpawnedEntities(repairedEntities));
        }
        return new OperationResult(true, repairedBlocks, 0, respawnedEntities, blockStats);
    }

    public static BlockSnapshot captureBlock(ServerLevel level, BlockPos pos) {
        BlockPos immutablePos = pos.immutable();
        BlockState state = level.getBlockState(immutablePos);
        CompoundTag blockEntityTag = null;
        BlockEntity blockEntity = state.hasBlockEntity() ? level.getBlockEntity(immutablePos) : null;
        if (blockEntity != null && blockEntity.isValidBlockState(state)) {
            try {
                blockEntityTag = blockEntity.saveWithFullMetadata(level.registryAccess());
            } catch (Exception ignored) {
                blockEntityTag = null;
            }
        }
        return new BlockSnapshot(immutablePos, state, blockEntityTag);
    }

    private static ApplyResult applySnapshot(ServerLevel level, BlockSnapshot snapshot, int flags) {
        if (level == null || snapshot == null || snapshot.pos() == null || snapshot.state() == null) {
            return ApplyResult.failed();
        }

        BlockPos pos = snapshot.pos();
        if (!level.isLoaded(pos)) {
            return ApplyResult.failed();
        }

        BlockSnapshot previous = captureBlock(level, pos);
        level.setBlock(pos, snapshot.state(), flags);
        if (!level.getBlockState(pos).equals(snapshot.state())) {
            return ApplyResult.failed();
        }

        boolean blockEntityMatched = applyBlockEntitySnapshot(level, pos, snapshot);
        BlockSnapshot current = captureBlock(level, pos);
        boolean changed = !snapshotsEquivalent(previous, current);
        if (changed) {
            level.sendBlockUpdated(pos, previous.state(), current.state(), flags);
        }
        return new ApplyResult(current.state().equals(snapshot.state()) && blockEntityMatched, changed);
    }

    private static boolean applyBlockEntitySnapshot(ServerLevel level, BlockPos pos, BlockSnapshot snapshot) {
        BlockState state = snapshot.state();
        if (!state.hasBlockEntity()) {
            level.removeBlockEntity(pos);
            return level.getBlockEntity(pos) == null;
        }
        return SchematicBlockEntityUtils.setBlockEntity(level, pos, state, snapshot.blockEntityTag());
    }

    private static boolean snapshotsEquivalent(BlockSnapshot left, BlockSnapshot right) {
        if (left == null || right == null) {
            return left == right;
        }
        return left.state().equals(right.state()) && tagsEquivalent(left.blockEntityTag(), right.blockEntityTag());
    }

    private static boolean tagsEquivalent(CompoundTag left, CompoundTag right) {
        if (left == null || right == null) {
            return left == right;
        }
        return left.equals(right);
    }

    private static void incrementBlockStat(Map<String, Object> stats, BlockState state) {
        String key = blockId(state);
        Object raw = stats.get(key);
        int count = raw instanceof Number number ? number.intValue() : 0;
        stats.put(key, count + 1);
    }

    private static String blockId(BlockState state) {
        if (state == null || state.isAir()) {
            return "minecraft:air";
        }
        Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return id == null ? "unknown" : id.toString();
    }

    private static boolean withinBounds(SchematicPlacementRecord record, BlockPos pos) {
        BlockPos min = record.boundsMin();
        BlockPos max = record.boundsMax();
        return pos.getX() >= min.getX() && pos.getX() <= max.getX()
                && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
    }

    public record SchematicPlacementRecord(
            String key,
            String graphId,
            ResourceKey<Level> dimension,
            BlockPos origin,
            BlockPos boundsMin,
            BlockPos boundsMax,
            int width,
            int height,
            int length,
            TransformInfo transform,
            List<ChangedBlock> changedBlocks,
            List<PlacedEntity> spawnedEntities,
            long createdGameTime
    ) {
        public SchematicPlacementRecord {
            key = key == null ? "" : key.trim();
            graphId = graphId == null ? "" : graphId;
            origin = origin == null ? BlockPos.ZERO : origin.immutable();
            boundsMin = boundsMin == null ? origin : boundsMin.immutable();
            boundsMax = boundsMax == null ? origin : boundsMax.immutable();
            transform = transform == null ? TransformInfo.NONE : transform;
            changedBlocks = changedBlocks == null ? List.of() : List.copyOf(changedBlocks);
            spawnedEntities = spawnedEntities == null ? List.of() : List.copyOf(spawnedEntities);
        }

        public Vec3 boundsCenter() {
            return new Vec3(
                    (boundsMin.getX() + boundsMax.getX() + 1.0D) * 0.5D,
                    (boundsMin.getY() + boundsMax.getY() + 1.0D) * 0.5D,
                    (boundsMin.getZ() + boundsMax.getZ() + 1.0D) * 0.5D
            );
        }

        public Vec3 boundsSize() {
            return new Vec3(
                    Math.max(0, boundsMax.getX() - boundsMin.getX() + 1),
                    Math.max(0, boundsMax.getY() - boundsMin.getY() + 1),
                    Math.max(0, boundsMax.getZ() - boundsMin.getZ() + 1)
            );
        }

        private SchematicPlacementRecord withSpawnedEntities(List<PlacedEntity> entities) {
            return new SchematicPlacementRecord(
                    key,
                    graphId,
                    dimension,
                    origin,
                    boundsMin,
                    boundsMax,
                    width,
                    height,
                    length,
                    transform,
                    changedBlocks,
                    entities,
                    createdGameTime
            );
        }
    }

    public record TransformInfo(boolean mirrorX, boolean mirrorZ, int rotationSteps) {
        public static final TransformInfo NONE = new TransformInfo(false, false, 0);

        public TransformInfo {
            rotationSteps = Math.floorMod(rotationSteps, 4);
        }
    }

    public record ChangedBlock(BlockSnapshot before, BlockSnapshot after) {
        public ChangedBlock {
            if (before == null) {
                throw new IllegalArgumentException("before snapshot is required");
            }
            if (after == null) {
                throw new IllegalArgumentException("after snapshot is required");
            }
        }

        public BlockPos pos() {
            return before.pos();
        }
    }

    public record BlockSnapshot(BlockPos pos, BlockState state, CompoundTag blockEntityTag) {
        public BlockSnapshot {
            pos = pos == null ? BlockPos.ZERO : pos.immutable();
            state = state == null ? Blocks.AIR.defaultBlockState() : state;
            blockEntityTag = blockEntityTag == null ? null : blockEntityTag.copy();
        }
    }

    public record PlacedEntity(UUID uuid, CompoundTag spawnTag) {
        public PlacedEntity {
            spawnTag = spawnTag == null ? new CompoundTag() : spawnTag.copy();
        }
    }

    public record OperationResult(boolean found,
                                  int blocks,
                                  int removedEntities,
                                  int respawnedEntities,
                                  Map<String, Object> blockStats) {
        public OperationResult {
            blockStats = blockStats == null ? Map.of() : new LinkedHashMap<>(blockStats);
        }

        private static OperationResult missing() {
            return new OperationResult(false, 0, 0, 0, Map.of());
        }
    }

    private record ApplyResult(boolean matched, boolean changed) {
        private static ApplyResult failed() {
            return new ApplyResult(false, false);
        }
    }

}
