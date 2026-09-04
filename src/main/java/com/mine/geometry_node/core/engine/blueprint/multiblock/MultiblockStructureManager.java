package com.mine.geometry_node.core.engine.blueprint.multiblock;

import com.mine.geometry_node.GeometryNode;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MultiblockStructureManager extends SimplePreparableReloadListener<Map<Identifier, JsonElement>> {
    public static final String DYNAMIC_REGISTRY_ID = "geometry_node:multiblock_structure";
    public static final String ANY_STRUCTURE_ID = "*";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String FOLDER_NAME = "multiblocks";
    private static MultiblockStructureManager INSTANCE;

    private Map<String, MultiblockStructure> structures = Collections.emptyMap();
    private List<String> optionIds = List.of(ANY_STRUCTURE_ID);

    public MultiblockStructureManager() {
        INSTANCE = this;
    }

    public static MultiblockStructureManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new MultiblockStructureManager();
        }
        return INSTANCE;
    }

    public List<String> getAllIds() {
        return optionIds;
    }

    @Override
    protected Map<Identifier, JsonElement> prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        FileToIdConverter lister = FileToIdConverter.json(FOLDER_NAME);
        Map<Identifier, JsonElement> objects = new HashMap<>();
        lister.listMatchingResources(resourceManager).forEach((fileId, resource) -> {
            Identifier id = lister.fileToId(fileId);
            try (BufferedReader reader = resource.openAsReader()) {
                objects.put(id, JsonParser.parseReader(reader));
            } catch (Exception e) {
                GeometryNode.LOGGER.error("[MultiblockStructureManager] Failed to load multiblock {}", fileId, e);
            }
        });
        return objects;
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> object, ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<String, MultiblockStructure> loaded = new HashMap<>();

        object.forEach((location, json) -> {
            try {
                MultiblockStructure structure = parseStructure(location.toString(), json);
                if (!structure.blocks().isEmpty()) {
                    loaded.put(structure.id(), structure);
                }
            } catch (Exception e) {
                GeometryNode.LOGGER.error("[MultiblockStructureManager] Failed to parse multiblock {}", location, e);
            }
        });

        List<String> options = new ArrayList<>(loaded.keySet());
        Collections.sort(options);
        options.add(0, ANY_STRUCTURE_ID);

        this.structures = Map.copyOf(loaded);
        this.optionIds = List.copyOf(options);
        GeometryNode.LOGGER.info("[MultiblockStructureManager] Loaded {} multiblock structure(s)", structures.size());
    }

    public List<Match> findMatches(ServerLevel level, BlockPos changedPos, BlockState changedState, Set<String> interestedStructureIds) {
        if (level == null || changedPos == null || changedState == null || interestedStructureIds == null || interestedStructureIds.isEmpty()) {
            return List.of();
        }

        Identifier changedBlockId = BuiltInRegistries.BLOCK.getKey(changedState.getBlock());
        if (changedBlockId == null) {
            return List.of();
        }

        List<MultiblockStructure> candidates = resolveCandidates(interestedStructureIds);
        if (candidates.isEmpty()) {
            return List.of();
        }

        List<Match> matches = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (MultiblockStructure structure : candidates) {
            for (BlockEntry anchorEntry : structure.blocks()) {
                if (!anchorEntry.blockId().equals(changedBlockId)) {
                    continue;
                }

                BlockPos origin = changedPos.offset(
                        -anchorEntry.offset().getX(),
                        -anchorEntry.offset().getY(),
                        -anchorEntry.offset().getZ()
                );
                if (!matchesAt(level, origin, structure)) {
                    continue;
                }

                String key = structure.id() + "@" + origin.getX() + "," + origin.getY() + "," + origin.getZ();
                if (seen.add(key)) {
                    matches.add(new Match(structure.id(), origin, changedPos));
                }
            }
        }
        return matches.isEmpty() ? List.of() : List.copyOf(matches);
    }

    private List<MultiblockStructure> resolveCandidates(Set<String> interestedStructureIds) {
        if (interestedStructureIds.contains(ANY_STRUCTURE_ID)) {
            return List.copyOf(structures.values());
        }

        List<MultiblockStructure> candidates = new ArrayList<>();
        for (String id : interestedStructureIds) {
            MultiblockStructure structure = structures.get(id);
            if (structure != null) {
                candidates.add(structure);
            }
        }
        return candidates;
    }

    private boolean matchesAt(ServerLevel level, BlockPos origin, MultiblockStructure structure) {
        for (BlockEntry entry : structure.blocks()) {
            BlockPos worldPos = origin.offset(entry.offset().getX(), entry.offset().getY(), entry.offset().getZ());
            BlockState state = level.getBlockState(worldPos);
            Identifier blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            if (!entry.blockId().equals(blockId)) {
                return false;
            }
        }
        return true;
    }

    private static MultiblockStructure parseStructure(String id, JsonElement json) {
        JsonObject root = asObject(json);
        if (root == null) {
            throw new IllegalArgumentException("Multiblock root must be an object");
        }

        JsonArray blocksArray = asArray(root.get("blocks"));
        if (blocksArray == null) {
            throw new IllegalArgumentException("Multiblock must contain a blocks array");
        }

        List<BlockEntry> blocks = new ArrayList<>();
        for (JsonElement element : blocksArray) {
            JsonObject blockObj = asObject(element);
            if (blockObj == null) {
                continue;
            }

            BlockPos offset = readOffset(blockObj);
            Identifier blockId = readBlockId(blockObj);
            if (offset != null && blockId != null) {
                blocks.add(new BlockEntry(offset, blockId));
            }
        }
        return new MultiblockStructure(id, List.copyOf(blocks));
    }

    @Nullable
    private static BlockPos readOffset(JsonObject blockObj) {
        JsonArray pos = asArray(blockObj.get("pos"));
        if (pos != null && pos.size() >= 3) {
            return new BlockPos(readInt(pos.get(0), 0), readInt(pos.get(1), 0), readInt(pos.get(2), 0));
        }

        if (blockObj.has("x") || blockObj.has("y") || blockObj.has("z")) {
            return new BlockPos(
                    readInt(blockObj.get("x"), 0),
                    readInt(blockObj.get("y"), 0),
                    readInt(blockObj.get("z"), 0)
            );
        }

        return null;
    }

    @Nullable
    private static Identifier readBlockId(JsonObject blockObj) {
        String raw = readString(blockObj, "block", null);
        if (raw == null || raw.isBlank()) {
            return null;
        }

        Identifier id = Identifier.tryParse(raw);
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
            throw new IllegalArgumentException("Unknown block id: " + raw);
        }
        return id;
    }

    private static int readInt(JsonElement element, int defaultValue) {
        if (element == null || !element.isJsonPrimitive()) {
            return defaultValue;
        }
        try {
            return element.getAsInt();
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private static String readString(JsonObject obj, String key, @Nullable String defaultValue) {
        if (obj == null || !obj.has(key)) {
            return defaultValue;
        }
        JsonElement element = obj.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            return defaultValue;
        }
        try {
            return element.getAsString();
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    @Nullable
    private static JsonObject asObject(JsonElement element) {
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    @Nullable
    private static JsonArray asArray(JsonElement element) {
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
    }

    public record MultiblockStructure(String id, List<BlockEntry> blocks) {}

    public record BlockEntry(BlockPos offset, Identifier blockId) {}

    public record Match(String structureId, BlockPos origin, BlockPos triggerPos) {}
}
