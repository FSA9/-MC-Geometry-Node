package com.mine.geometry_node.core.engine.system.schematic;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.core.engine.system.schematic.SchematicPlacementManager.BlockSnapshot;
import com.mine.geometry_node.core.engine.system.schematic.SchematicPlacementManager.ChangedBlock;
import com.mine.geometry_node.core.engine.system.schematic.SchematicPlacementManager.PlacedEntity;
import com.mine.geometry_node.core.engine.system.schematic.SchematicPlacementManager.SchematicPlacementRecord;
import com.mine.geometry_node.core.engine.system.schematic.SchematicPlacementManager.TransformInfo;
import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 持久化 schematic 放置记录。
 * 数据保存在存档的 {@code data/geometry_node/schematic_placements.dat} 文件中。
 */
public final class SchematicPlacementStorage extends SavedData {
    private static final String TAG_VERSION = "Version";
    private static final String TAG_RECORDS = "Records";
    private static final String TAG_STATE_PALETTE = "StatePalette";
    private static final String TAG_BLOCK_POSITIONS = "BlockPositions";
    private static final String TAG_BLOCK_STATES = "BlockStates";
    private static final String TAG_BEFORE_BLOCK_ENTITIES = "BeforeBlockEntities";
    private static final String TAG_AFTER_BLOCK_ENTITIES = "AfterBlockEntities";
    private static final int VERSION = 2;

    private static final Codec<SchematicPlacementStorage> CODEC = CompoundTag.CODEC.xmap(
            SchematicPlacementStorage::load,
            storage -> storage.saveToTag(new CompoundTag())
    );

    public static final SavedDataType<SchematicPlacementStorage> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(GeometryNode.MODID, "schematic_placements"),
            SchematicPlacementStorage::new,
            CODEC
    );

    private final Map<RecordKey, SchematicPlacementRecord> records = new HashMap<>();
    private final Map<RecordKey, LongOpenHashSet> positionsByRecord = new HashMap<>();
    private final Map<RecordKey, LongOpenHashSet> chunksByRecord = new HashMap<>();
    private final Map<DimensionChunkKey, Set<RecordKey>> recordsByChunk = new HashMap<>();

    public static SchematicPlacementStorage get(ServerLevel level) {
        return level.getServer().getDataStorage().computeIfAbsent(TYPE);
    }

    public Collection<SchematicPlacementRecord> records() {
        return Collections.unmodifiableCollection(records.values());
    }

    public Optional<SchematicPlacementRecord> get(ResourceKey<Level> dimension, String key) {
        if (dimension == null || key == null || key.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(records.get(new RecordKey(dimension, key)));
    }

    public SchematicPlacementRecord put(SchematicPlacementRecord record) {
        if (record == null || record.dimension() == null || record.key().isBlank()) {
            return record;
        }
        RecordKey key = new RecordKey(record.dimension(), record.key());
        SchematicPlacementRecord previous = records.put(key, record);
        if (previous != null) {
            removeIndexes(key);
        }
        indexRecord(key, record);
        setDirty();
        return record;
    }

    public Optional<SchematicPlacementRecord> remove(ResourceKey<Level> dimension, String key) {
        if (dimension == null || key == null || key.isBlank()) {
            return Optional.empty();
        }
        RecordKey recordKey = new RecordKey(dimension, key);
        SchematicPlacementRecord removed = records.remove(recordKey);
        if (removed != null) {
            removeIndexes(recordKey);
            setDirty();
        }
        return Optional.ofNullable(removed);
    }

    public int clearDimension(ResourceKey<Level> dimension) {
        if (dimension == null || records.isEmpty()) {
            return 0;
        }
        ArrayList<RecordKey> toRemove = new ArrayList<>();
        for (RecordKey key : records.keySet()) {
            if (dimension.equals(key.dimension())) {
                toRemove.add(key);
            }
        }
        for (RecordKey key : toRemove) {
            records.remove(key);
            removeIndexes(key);
        }
        int removed = toRemove.size();
        if (removed > 0) {
            setDirty();
        }
        return removed;
    }

    public Optional<SchematicPlacementRecord> findContaining(ResourceKey<Level> dimension, BlockPos pos) {
        if (dimension == null || pos == null) {
            return Optional.empty();
        }

        Set<RecordKey> candidates = recordsByChunk.get(chunkKey(dimension, pos));
        if (candidates == null || candidates.isEmpty()) {
            return Optional.empty();
        }

        long posLong = pos.asLong();
        SchematicPlacementRecord best = null;
        for (RecordKey key : candidates) {
            LongOpenHashSet positions = positionsByRecord.get(key);
            if (positions == null || !positions.contains(posLong)) {
                continue;
            }
            SchematicPlacementRecord record = records.get(key);
            if (record == null) {
                continue;
            }
            if (best == null || record.createdGameTime() >= best.createdGameTime()) {
                best = record;
            }
        }
        return Optional.ofNullable(best);
    }

    public boolean containsBlock(ResourceKey<Level> dimension, String key, BlockPos pos) {
        if (dimension == null || key == null || key.isBlank() || pos == null) {
            return false;
        }
        LongOpenHashSet positions = positionsByRecord.get(new RecordKey(dimension, key));
        return positions != null && positions.contains(pos.asLong());
    }

    public String resolveKey(ResourceKey<Level> dimension, String requestedKey, boolean uniqueIfExists) {
        String baseKey = requestedKey == null ? "" : requestedKey.trim();
        if (dimension == null || baseKey.isEmpty() || !uniqueIfExists) {
            return baseKey;
        }
        if (!records.containsKey(new RecordKey(dimension, baseKey))) {
            return baseKey;
        }
        for (int index = 1; index < Integer.MAX_VALUE; index++) {
            String candidate = baseKey + "_" + index;
            if (!records.containsKey(new RecordKey(dimension, candidate))) {
                return candidate;
            }
        }
        return baseKey + "_" + UUID.randomUUID();
    }

    private static SchematicPlacementStorage load(CompoundTag tag) {
        SchematicPlacementStorage storage = new SchematicPlacementStorage();
        ListTag list = tag.getListOrEmpty(TAG_RECORDS);
        for (int i = 0; i < list.size(); i++) {
            readRecord(list.getCompoundOrEmpty(i)).ifPresent(storage::putLoaded);
        }
        return storage;
    }

    private void putLoaded(SchematicPlacementRecord record) {
        if (record == null || record.dimension() == null || record.key().isBlank()) {
            return;
        }
        RecordKey key = new RecordKey(record.dimension(), record.key());
        SchematicPlacementRecord previous = records.put(key, record);
        if (previous != null) {
            removeIndexes(key);
        }
        indexRecord(key, record);
    }

    private CompoundTag saveToTag(CompoundTag tag) {
        tag.putInt(TAG_VERSION, VERSION);
        ListTag list = new ListTag();
        for (SchematicPlacementRecord record : records.values()) {
            list.add(writeRecord(record));
        }
        tag.put(TAG_RECORDS, list);
        return tag;
    }

    private static CompoundTag writeRecord(SchematicPlacementRecord record) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Key", record.key());
        tag.putString("GraphId", record.graphId());
        tag.putString("Dimension", record.dimension().identifier().toString());
        tag.put("Origin", writePos(record.origin()));
        tag.put("BoundsMin", writePos(record.boundsMin()));
        tag.put("BoundsMax", writePos(record.boundsMax()));
        tag.putInt("Width", record.width());
        tag.putInt("Height", record.height());
        tag.putInt("Length", record.length());
        tag.put("Transform", writeTransform(record.transform()));
        writeChangedBlocks(tag, record.changedBlocks());
        tag.put("SpawnedEntities", writeSpawnedEntities(record.spawnedEntities()));
        tag.putLong("CreatedGameTime", record.createdGameTime());
        return tag;
    }

    private static Optional<SchematicPlacementRecord> readRecord(CompoundTag tag) {
        String key = tag.getStringOr("Key", "").trim();
        Identifier dimensionId = Identifier.tryParse(tag.getStringOr("Dimension", ""));
        if (key.isEmpty() || dimensionId == null) {
            return Optional.empty();
        }

        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimensionId);
        List<ChangedBlock> changedBlocks = readChangedBlocks(tag);
        List<PlacedEntity> spawnedEntities = readSpawnedEntities(tag.getListOrEmpty("SpawnedEntities"));
        return Optional.of(new SchematicPlacementRecord(
                key,
                tag.getStringOr("GraphId", ""),
                dimension,
                readPos(tag.getCompoundOrEmpty("Origin")),
                readPos(tag.getCompoundOrEmpty("BoundsMin")),
                readPos(tag.getCompoundOrEmpty("BoundsMax")),
                Math.max(0, tag.getIntOr("Width", 0)),
                Math.max(0, tag.getIntOr("Height", 0)),
                Math.max(0, tag.getIntOr("Length", 0)),
                readTransform(tag.getCompoundOrEmpty("Transform")),
                changedBlocks,
                spawnedEntities,
                tag.getLongOr("CreatedGameTime", 0L)
        ));
    }

    private static void writeChangedBlocks(CompoundTag recordTag, List<ChangedBlock> changedBlocks) {
        StatePaletteWriter palette = new StatePaletteWriter();
        int count = changedBlocks.size();
        long[] positions = new long[count];
        int[] stateIndices = new int[count * 2];
        ListTag beforeBlockEntities = new ListTag();
        ListTag afterBlockEntities = new ListTag();

        for (int i = 0; i < count; i++) {
            ChangedBlock changedBlock = changedBlocks.get(i);
            BlockPos pos = changedBlock.pos();
            positions[i] = pos.asLong();

            int stateOffset = i * 2;
            stateIndices[stateOffset] = palette.indexOf(changedBlock.before().state());
            stateIndices[stateOffset + 1] = palette.indexOf(changedBlock.after().state());

            if (changedBlock.before().blockEntityTag() != null) {
                beforeBlockEntities.add(writeIndexedTag(i, changedBlock.before().blockEntityTag()));
            }
            if (changedBlock.after().blockEntityTag() != null) {
                afterBlockEntities.add(writeIndexedTag(i, changedBlock.after().blockEntityTag()));
            }
        }

        recordTag.put(TAG_STATE_PALETTE, palette.toTag());
        recordTag.putLongArray(TAG_BLOCK_POSITIONS, positions);
        recordTag.putIntArray(TAG_BLOCK_STATES, stateIndices);
        if (!beforeBlockEntities.isEmpty()) {
            recordTag.put(TAG_BEFORE_BLOCK_ENTITIES, beforeBlockEntities);
        }
        if (!afterBlockEntities.isEmpty()) {
            recordTag.put(TAG_AFTER_BLOCK_ENTITIES, afterBlockEntities);
        }
    }

    private static CompoundTag writeIndexedTag(int index, CompoundTag value) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Index", index);
        tag.put("Tag", value.copy());
        return tag;
    }

    private static List<ChangedBlock> readChangedBlocks(CompoundTag recordTag) {
        if (recordTag.contains(TAG_STATE_PALETTE)) {
            return readChangedBlocksV2(recordTag);
        }
        return readChangedBlocksV1(recordTag.getListOrEmpty("ChangedBlocks"));
    }

    private static List<ChangedBlock> readChangedBlocksV2(CompoundTag recordTag) {
        List<BlockState> palette = readStatePalette(recordTag.getListOrEmpty(TAG_STATE_PALETTE));
        long[] positions = readBlockPositions(recordTag);
        int[] stateIndices = recordTag.getIntArray(TAG_BLOCK_STATES).orElse(new int[0]);
        Map<Integer, CompoundTag> beforeBlockEntities = readIndexedTags(recordTag.getListOrEmpty(TAG_BEFORE_BLOCK_ENTITIES));
        Map<Integer, CompoundTag> afterBlockEntities = readIndexedTags(recordTag.getListOrEmpty(TAG_AFTER_BLOCK_ENTITIES));

        int count = Math.min(positions.length, stateIndices.length / 2);
        ArrayList<ChangedBlock> changedBlocks = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            BlockState beforeState = paletteState(palette, stateIndices[i * 2]);
            BlockState afterState = paletteState(palette, stateIndices[i * 2 + 1]);
            if (beforeState == null || afterState == null) {
                continue;
            }

            BlockPos pos = BlockPos.of(positions[i]);
            changedBlocks.add(new ChangedBlock(
                    new BlockSnapshot(pos, beforeState, beforeBlockEntities.get(i)),
                    new BlockSnapshot(pos, afterState, afterBlockEntities.get(i))
            ));
        }
        return changedBlocks;
    }

    private static long[] readBlockPositions(CompoundTag recordTag) {
        Optional<long[]> packedPositions = recordTag.getLongArray(TAG_BLOCK_POSITIONS);
        if (packedPositions.isPresent()) {
            return packedPositions.get();
        }

        int[] legacyPositions = recordTag.getIntArray(TAG_BLOCK_POSITIONS).orElse(new int[0]);
        int count = legacyPositions.length / 3;
        long[] positions = new long[count];
        for (int i = 0; i < count; i++) {
            positions[i] = new BlockPos(
                    legacyPositions[i * 3],
                    legacyPositions[i * 3 + 1],
                    legacyPositions[i * 3 + 2]
            ).asLong();
        }
        return positions;
    }

    private static List<ChangedBlock> readChangedBlocksV1(ListTag list) {
        ArrayList<ChangedBlock> changedBlocks = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompoundOrEmpty(i);
            Optional<BlockSnapshot> before = readSnapshot(tag.getCompoundOrEmpty("Before"));
            Optional<BlockSnapshot> after = readSnapshot(tag.getCompoundOrEmpty("After"));
            if (before.isPresent() && after.isPresent()) {
                changedBlocks.add(new ChangedBlock(before.get(), after.get()));
            }
        }
        return changedBlocks;
    }

    private static List<BlockState> readStatePalette(ListTag list) {
        ArrayList<BlockState> states = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            states.add(NbtUtils.readBlockState(BuiltInRegistries.BLOCK, list.getCompoundOrEmpty(i)));
        }
        return states;
    }

    private static BlockState paletteState(List<BlockState> palette, int index) {
        return index >= 0 && index < palette.size() ? palette.get(index) : null;
    }

    private static Map<Integer, CompoundTag> readIndexedTags(ListTag list) {
        HashMap<Integer, CompoundTag> tags = new HashMap<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompoundOrEmpty(i);
            int index = entry.getIntOr("Index", -1);
            if (index >= 0 && entry.contains("Tag")) {
                tags.put(index, entry.getCompoundOrEmpty("Tag").copy());
            }
        }
        return tags;
    }

    private static Optional<BlockSnapshot> readSnapshot(CompoundTag tag) {
        if (!tag.contains("State")) {
            return Optional.empty();
        }
        BlockState state = NbtUtils.readBlockState(BuiltInRegistries.BLOCK, tag.getCompoundOrEmpty("State"));
        CompoundTag blockEntityTag = tag.contains("BlockEntity") ? tag.getCompoundOrEmpty("BlockEntity").copy() : null;
        return Optional.of(new BlockSnapshot(readPos(tag.getCompoundOrEmpty("Pos")), state, blockEntityTag));
    }

    private static ListTag writeSpawnedEntities(List<PlacedEntity> spawnedEntities) {
        ListTag list = new ListTag();
        for (PlacedEntity placedEntity : spawnedEntities) {
            CompoundTag tag = new CompoundTag();
            if (placedEntity.uuid() != null) {
                tag.putString("Uuid", placedEntity.uuid().toString());
            }
            tag.put("Tag", placedEntity.spawnTag().copy());
            list.add(tag);
        }
        return list;
    }

    private static List<PlacedEntity> readSpawnedEntities(ListTag list) {
        ArrayList<PlacedEntity> spawnedEntities = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompoundOrEmpty(i);
            try {
                UUID uuid = UUID.fromString(tag.getStringOr("Uuid", ""));
                spawnedEntities.add(new PlacedEntity(uuid, tag.getCompoundOrEmpty("Tag").copy()));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return spawnedEntities;
    }

    private static CompoundTag writeTransform(TransformInfo transform) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("MirrorX", transform.mirrorX());
        tag.putBoolean("MirrorZ", transform.mirrorZ());
        tag.putInt("RotationSteps", transform.rotationSteps());
        return tag;
    }

    private static TransformInfo readTransform(CompoundTag tag) {
        return new TransformInfo(
                tag.getBooleanOr("MirrorX", false),
                tag.getBooleanOr("MirrorZ", false),
                tag.getIntOr("RotationSteps", 0)
        );
    }

    private static CompoundTag writePos(BlockPos pos) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("X", pos.getX());
        tag.putInt("Y", pos.getY());
        tag.putInt("Z", pos.getZ());
        return tag;
    }

    private static BlockPos readPos(CompoundTag tag) {
        return new BlockPos(
                tag.getIntOr("X", 0),
                tag.getIntOr("Y", 0),
                tag.getIntOr("Z", 0)
        );
    }

    private void indexRecord(RecordKey key, SchematicPlacementRecord record) {
        LongOpenHashSet positions = new LongOpenHashSet(Math.max(16, record.changedBlocks().size() * 2));
        LongOpenHashSet chunks = new LongOpenHashSet();
        for (ChangedBlock changedBlock : record.changedBlocks()) {
            BlockPos pos = changedBlock.pos();
            positions.add(pos.asLong());
            chunks.add(chunkPosLong(pos));
        }
        positionsByRecord.put(key, positions);
        chunksByRecord.put(key, chunks);
        for (long chunkPos : chunks) {
            recordsByChunk.computeIfAbsent(chunkKey(key.dimension(), chunkPos), ignored -> new HashSet<>()).add(key);
        }
    }

    private void removeIndexes(RecordKey key) {
        LongOpenHashSet positions = positionsByRecord.remove(key);
        LongOpenHashSet chunks = chunksByRecord.remove(key);
        if (chunks == null || chunks.isEmpty()) {
            chunks = new LongOpenHashSet();
            if (positions != null) {
                for (long posLong : positions) {
                    chunks.add(chunkPosLong(BlockPos.of(posLong)));
                }
            }
        }
        if (chunks.isEmpty()) {
            return;
        }
        for (long chunkPos : chunks) {
            DimensionChunkKey chunkKey = chunkKey(key.dimension(), chunkPos);
            Set<RecordKey> keys = recordsByChunk.get(chunkKey);
            if (keys == null) {
                continue;
            }
            keys.remove(key);
            if (keys.isEmpty()) {
                recordsByChunk.remove(chunkKey);
            }
        }
    }

    private static DimensionChunkKey chunkKey(ResourceKey<Level> dimension, BlockPos pos) {
        return chunkKey(dimension, chunkPosLong(pos));
    }

    private static DimensionChunkKey chunkKey(ResourceKey<Level> dimension, long chunkPos) {
        return new DimensionChunkKey(dimension, chunkPos);
    }

    private static long chunkPosLong(BlockPos pos) {
        return ((long) pos.getX() >> 4 & 0xffffffffL) | (((long) pos.getZ() >> 4 & 0xffffffffL) << 32);
    }

    private static final class StatePaletteWriter {
        private final Map<BlockState, Integer> indices = new HashMap<>();
        private final List<BlockState> states = new ArrayList<>();

        private int indexOf(BlockState state) {
            Integer existing = indices.get(state);
            if (existing != null) {
                return existing;
            }
            int index = states.size();
            states.add(state);
            indices.put(state, index);
            return index;
        }

        private ListTag toTag() {
            ListTag list = new ListTag();
            for (BlockState state : states) {
                list.add(NbtUtils.writeBlockState(state));
            }
            return list;
        }
    }

    private record RecordKey(ResourceKey<Level> dimension, String key) {
        private RecordKey {
            key = key == null ? "" : key.trim();
        }
    }

    private record DimensionChunkKey(ResourceKey<Level> dimension, long chunkPos) {
    }
}
