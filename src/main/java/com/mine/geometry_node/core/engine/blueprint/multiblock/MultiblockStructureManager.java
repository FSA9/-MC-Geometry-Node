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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class MultiblockStructureManager extends SimplePreparableReloadListener<Map<Identifier, JsonElement>> {
    public static final String DYNAMIC_REGISTRY_ID = "geometry_node:multiblock_structure";
    public static final String ANY_STRUCTURE_ID = "*";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String FOLDER_NAME = "multiblocks";
    private static MultiblockStructureManager INSTANCE;

    private volatile StructureIndex structureIndex = StructureIndex.compile(Map.of());

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
        return structureIndex.optionIds();
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

        replaceStructures(loaded);
        GeometryNode.LOGGER.info("[MultiblockStructureManager] Loaded {} multiblock structure(s)", loaded.size());
    }

    /** Publishes definitions, anchor indexes, and picker options as one reload snapshot. */
    void replaceStructures(Map<String, MultiblockStructure> structures) {
        structureIndex = StructureIndex.compile(structures);
    }

    public List<Match> findMatches(ServerLevel level, BlockPos changedPos, BlockState changedState, Set<String> interestedStructureIds) {
        if (level == null || changedPos == null || changedState == null || interestedStructureIds == null || interestedStructureIds.isEmpty()) {
            return List.of();
        }

        Identifier changedBlockId = BuiltInRegistries.BLOCK.getKey(changedState.getBlock());
        if (changedBlockId == null) {
            return List.of();
        }

        return findMatches(changedPos, changedBlockId, interestedStructureIds,
                worldPos -> BuiltInRegistries.BLOCK.getKey(level.getBlockState(worldPos).getBlock()));
    }

    /** World-independent matching entry point; the lookup is only called for relevant anchors. */
    List<Match> findMatches(BlockPos changedPos, Identifier changedBlockId,
                            Set<String> interestedStructureIds,
                            Function<BlockPos, Identifier> blockAt) {
        StructureIndex index = structureIndex;
        if (!index.anchorsByBlock().containsKey(changedBlockId)) return List.of();
        List<Match> matches = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        if (interestedStructureIds.contains(ANY_STRUCTURE_ID)) {
            appendMatches(index.anchorsByBlock().getOrDefault(changedBlockId, List.of()),
                    changedPos, blockAt, matches, seen);
        } else {
            // Preserve caller iteration order, as the former candidate list did.
            for (String id : interestedStructureIds) {
                Map<Identifier, List<Anchor>> anchors = index.anchorsByStructure().get(id);
                if (anchors != null) {
                    appendMatches(anchors.getOrDefault(changedBlockId, List.of()),
                            changedPos, blockAt, matches, seen);
                }
            }
        }
        return matches.isEmpty() ? List.of() : List.copyOf(matches);
    }

    private static void appendMatches(List<Anchor> anchors, BlockPos changedPos,
                                      Function<BlockPos, Identifier> blockAt,
                                      List<Match> matches, Set<String> seen) {
        for (Anchor anchor : anchors) {
            MultiblockStructure structure = anchor.structure();
            BlockPos origin = changedPos.offset(-anchor.offset().getX(),
                    -anchor.offset().getY(), -anchor.offset().getZ());
            if (!matchesAt(blockAt, origin, structure)) continue;

            String key = structure.id() + "@" + origin.getX() + "," + origin.getY() + "," + origin.getZ();
            if (seen.add(key)) matches.add(new Match(structure.id(), origin, changedPos));
        }
    }

    private static boolean matchesAt(Function<BlockPos, Identifier> blockAt,
                                      BlockPos origin, MultiblockStructure structure) {
        for (BlockEntry entry : structure.blocks()) {
            BlockPos worldPos = origin.offset(entry.offset().getX(), entry.offset().getY(), entry.offset().getZ());
            if (!entry.blockId().equals(blockAt.apply(worldPos))) return false;
        }
        return true;
    }

    private record Anchor(MultiblockStructure structure, BlockPos offset) {}

    private record StructureIndex(Map<String, Map<Identifier, List<Anchor>>> anchorsByStructure,
                                  Map<Identifier, List<Anchor>> anchorsByBlock,
                                  List<String> optionIds) {
        private static StructureIndex compile(Map<String, MultiblockStructure> structures) {
            // Match the former immutable map's wildcard structure iteration order.
            Map<String, MultiblockStructure> definitions = Map.copyOf(structures);
            Map<String, Map<Identifier, List<Anchor>>> byStructure = new HashMap<>();
            Map<Identifier, List<Anchor>> byBlock = new HashMap<>();
            for (Map.Entry<String, MultiblockStructure> definition : definitions.entrySet()) {
                Map<Identifier, List<Anchor>> local = new LinkedHashMap<>();
                for (BlockEntry entry : definition.getValue().blocks()) {
                    Anchor anchor = new Anchor(definition.getValue(), entry.offset());
                    local.computeIfAbsent(entry.blockId(), ignored -> new ArrayList<>()).add(anchor);
                    byBlock.computeIfAbsent(entry.blockId(), ignored -> new ArrayList<>()).add(anchor);
                }
                byStructure.put(definition.getKey(), immutableLists(local));
            }
            List<String> options = new ArrayList<>(definitions.keySet());
            Collections.sort(options);
            options.add(0, ANY_STRUCTURE_ID);
            return new StructureIndex(Map.copyOf(byStructure), immutableLists(byBlock), List.copyOf(options));
        }

        private static <K> Map<K, List<Anchor>> immutableLists(Map<K, List<Anchor>> source) {
            Map<K, List<Anchor>> result = new HashMap<>();
            source.forEach((key, anchors) -> result.put(key, List.copyOf(anchors)));
            return Map.copyOf(result);
        }
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
