package com.mine.geometry_node.core.engine.graph.storage;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.core.engine.graph.GraphDocumentType;
import com.mine.geometry_node.core.engine.graph.GraphType;
import com.mine.geometry_node.core.engine.graph.compile.GraphCompilationService;
import com.mine.geometry_node.core.engine.graph.compile.artifact.CompiledGraph;
import com.mine.geometry_node.core.engine.system.asset.repository.ServerAssetPaths;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Package-private disk scanner and compiler used by {@link ServerGraphRepository}. */
final class GraphRepositoryLoader {
    private GraphRepositoryLoader() {
    }

    static Map<String, GraphAssetDescriptor> loadAll(MinecraftServer server) {
        Map<String, GraphAssetDescriptor> loadedGraphs = new LinkedHashMap<>();
        try {
            Path root = ServerAssetPaths.root(server);
            if (!Files.isDirectory(root)) return loadedGraphs;
            try (var walk = Files.walk(root)) {
                walk.filter(path -> Files.isRegularFile(path) && !Files.isSymbolicLink(path))
                        .filter(path -> GraphPathMapper.isGraphJsonPath(path.toString()))
                        .forEach(file -> {
                            GraphAssetDescriptor descriptor = loadDescriptor(root, file);
                            if (descriptor != null) loadedGraphs.put(descriptor.graphId(), descriptor);
                        });
            }
            GeometryNode.LOGGER.info("[ServerGraphRepository] Loaded {} graphs", loadedGraphs.size());
            return loadedGraphs;
        } catch (Exception error) {
            throw new IllegalStateException("Failed to scan the graph repository", error);
        }
    }

    static IncrementalLoad loadChanged(MinecraftServer server,
                                       Map<String, GraphAssetDescriptor> currentGraphs,
                                       Set<String> affectedPaths) {
        Map<String, GraphAssetDescriptor> graphs = new LinkedHashMap<>(currentGraphs);
        Set<String> affectedGraphIds = new HashSet<>();
        Path root = ServerAssetPaths.root(server);
        for (String affectedPath : affectedPaths) {
            String normalized = ServerAssetPaths.normalizeRelativePath(affectedPath, false);
            if (!GraphPathMapper.isGraphJsonPath(normalized)) continue;
            String graphId = GraphPathMapper.normalizeId(normalized);
            affectedGraphIds.add(graphId);
            Path file = ServerAssetPaths.resolveUnderRoot(root, normalized, false);
            if (!Files.isRegularFile(file) || Files.isSymbolicLink(file)) {
                graphs.remove(graphId);
                continue;
            }
            GraphAssetDescriptor descriptor = loadDescriptor(root, file);
            if (descriptor == null) graphs.remove(graphId);
            else graphs.put(graphId, descriptor);
        }
        return new IncrementalLoad(graphs, Set.copyOf(affectedGraphIds));
    }

    @Nullable
    private static GraphAssetDescriptor loadDescriptor(Path root, Path file) {
        try {
            String graphId = GraphPathMapper.pathToId(root, file);
            try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                JsonElement parsed = JsonParser.parseReader(reader);
                if (!parsed.isJsonObject()) return null;
                JsonObject document = parsed.getAsJsonObject();
                GraphType type = GraphDocumentType.require(document);
                CompiledGraph artifact = GraphCompilationService.INSTANCE.compile(graphId, document);
                return describe(graphId, type, artifact, document);
            }
        } catch (Exception error) {
            GeometryNode.LOGGER.error("[ServerGraphRepository] Failed to load graph: {}", file, error);
            return null;
        }
    }

    /** Uses only the document already read by the loader; published descriptors need no file access. */
    static GraphAssetDescriptor describe(String graphId, GraphType type, CompiledGraph artifact, JsonObject document) {
        List<String> tags = new ArrayList<>();
        JsonElement values = document.get("tags");
        if (values != null && values.isJsonArray()) {
            // Same string-only input and GRAPH_LEGACY normalization as GraphTagIO.readTags.
            for (JsonElement value : values.getAsJsonArray()) {
                if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) tags.add(value.getAsString());
            }
        }
        String name = readName(document, "name");
        if (name.isBlank()) name = readName(document, "graph_name");
        return new GraphAssetDescriptor(graphId, type, artifact, GraphAssetFingerprint.of(document), name, tags);
    }

    private static String readName(JsonObject document, String key) {
        JsonElement value = document.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                ? value.getAsString() : "";
    }

    record IncrementalLoad(Map<String, GraphAssetDescriptor> graphs,
                           Set<String> affectedGraphIds) {
    }
}
