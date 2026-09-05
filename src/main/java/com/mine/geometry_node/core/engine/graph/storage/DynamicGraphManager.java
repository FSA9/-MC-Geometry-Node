package com.mine.geometry_node.core.engine.graph.storage;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.core.engine.graph.GraphDocumentType;
import com.mine.geometry_node.core.engine.graph.GraphType;
import com.mine.geometry_node.core.engine.graph.compile.GraphCompilationService;
import com.mine.geometry_node.core.engine.graph.compile.artifact.CompiledGraph;
import com.mine.geometry_node.core.engine.system.asset.ServerAssetPaths;
import net.minecraft.server.MinecraftServer;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Loads graph documents off-thread and publishes complete immutable runtime snapshots. */
public final class DynamicGraphManager {
    private static final ExecutorService BUILD_EXECUTOR = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "GeometryNode-GraphRepository-Build");
        thread.setDaemon(true);
        return thread;
    });
    private static final Map<MinecraftServer, CompletableFuture<Void>> PENDING = new WeakHashMap<>();
    private static final Map<MinecraftServer, Set<CompletableFuture<Void>>> ACTIVE_PUBLICATIONS =
            new WeakHashMap<>();
    private static final Map<MinecraftServer, Long> SERVER_GENERATIONS = new WeakHashMap<>();
    private static final Map<MinecraftServer, Boolean> ACTIVE_SERVERS = new WeakHashMap<>();

    private DynamicGraphManager() {
    }

    /** Startup has no active tick loop, so the initial repository snapshot is built synchronously. */
    public static void prepareForServerStart(MinecraftServer server) {
        if (server == null) return;
        synchronized (PENDING) {
            ACTIVE_SERVERS.put(server, Boolean.TRUE);
            SERVER_GENERATIONS.put(server, SERVER_GENERATIONS.getOrDefault(server, 0L) + 1L);
        }
        try {
            publishDynamicSnapshot(server, loadAllDescriptors(server));
        } catch (RuntimeException error) {
            GeometryNode.LOGGER.error("[DynamicGraphManager] Initial graph load failed", error);
        }
    }

    /** Compatibility entry point; runtime full reloads are always scheduled off-thread. */
    public static void loadAllFromDisk(MinecraftServer server) {
        refreshAll(server);
    }

    /** Schedules an ordered background rebuild after an asset mutation. */
    public static CompletableFuture<Void> refresh(
            MinecraftServer server, Set<String> affectedPaths, boolean directoryScope) {
        if (server == null) return CompletableFuture.completedFuture(null);
        Set<String> paths = affectedPaths == null ? Set.of() : Set.copyOf(affectedPaths);
        final long generation;
        final CompletableFuture<Void> next;
        synchronized (PENDING) {
            if (!ACTIVE_SERVERS.containsKey(server)) return CompletableFuture.completedFuture(null);
            generation = SERVER_GENERATIONS.getOrDefault(server, 0L);
            CompletableFuture<Void> previous = PENDING.getOrDefault(
                    server, CompletableFuture.completedFuture(null));
            next = previous.handle((ignored, error) -> null)
                    .thenComposeAsync(ignored -> buildAndPublish(server, paths, directoryScope, generation),
                            BUILD_EXECUTOR);
            PENDING.put(server, next);
        }
        next.whenComplete((ignored, error) -> {
            synchronized (PENDING) {
                PENDING.remove(server, next);
            }
        });
        return next;
    }

    public static CompletableFuture<Void> refreshAll(MinecraftServer server) {
        return refresh(server, Set.of(), true);
    }

    public static void serverStopped(MinecraftServer server) {
        if (server == null) return;
        CompletableFuture<Void> pending;
        Set<CompletableFuture<Void>> publications;
        synchronized (PENDING) {
            ACTIVE_SERVERS.remove(server);
            SERVER_GENERATIONS.put(server, SERVER_GENERATIONS.getOrDefault(server, 0L) + 1L);
            pending = PENDING.remove(server);
            publications = ACTIVE_PUBLICATIONS.remove(server);
        }
        if (pending != null) pending.cancel(false);
        if (publications != null) publications.forEach(future -> future.cancel(false));
    }

    private static CompletableFuture<Void> buildAndPublish(
            MinecraftServer server, Set<String> affectedPaths,
            boolean directoryScope, long generation) {
        if (!isCurrent(server, generation)) return CompletableFuture.completedFuture(null);
        try {
            GraphAssetLifecycleIndex.PreparedUpdate prepared;
            if (directoryScope || affectedPaths.isEmpty()) {
                prepared = GraphAssetLifecycleIndex.INSTANCE.prepareDynamicGraphs(
                        server, loadAllDescriptors(server));
            } else {
                IncrementalLoad load = loadChangedDescriptors(server, affectedPaths);
                prepared = GraphAssetLifecycleIndex.INSTANCE.prepareDynamicGraphsIncrementally(
                        server, load.graphs(), load.affectedGraphIds());
            }
            if (!isCurrent(server, generation)) return CompletableFuture.completedFuture(null);
            CompletableFuture<Void> published;
            synchronized (PENDING) {
                if (SERVER_GENERATIONS.getOrDefault(server, 0L) != generation) {
                    return CompletableFuture.completedFuture(null);
                }
                published = new CompletableFuture<>();
                published.whenComplete((ignored, error) -> removePublication(server, published));
                ACTIVE_PUBLICATIONS.computeIfAbsent(server, ignored -> new HashSet<>()).add(published);
                try {
                    server.execute(() -> {
                        try {
                            if (isCurrent(server, generation)) {
                                GraphAssetLifecycleIndex.INSTANCE.publishPrepared(prepared);
                            }
                            published.complete(null);
                        } catch (Throwable error) {
                            published.completeExceptionally(error);
                        }
                    });
                } catch (Throwable error) {
                    published.completeExceptionally(error);
                }
            }
            return published;
        } catch (Throwable error) {
            return CompletableFuture.failedFuture(error);
        }
    }

    private static IncrementalLoad loadChangedDescriptors(
            MinecraftServer server, Set<String> affectedPaths) {
        Map<String, GraphAssetDescriptor> graphs = new LinkedHashMap<>(
                GraphAssetLifecycleIndex.INSTANCE.dynamicGraphsSnapshot(server));
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

    private static Map<String, GraphAssetDescriptor> loadAllDescriptors(MinecraftServer server) {
        Map<String, GraphAssetDescriptor> loadedGraphs = new LinkedHashMap<>();
        try {
            Path folder = ServerAssetPaths.root(server);
            if (!Files.exists(folder) || !Files.isDirectory(folder)) return loadedGraphs;
            try (var walk = Files.walk(folder)) {
                walk.filter(path -> Files.isRegularFile(path) && !Files.isSymbolicLink(path))
                        .filter(path -> GraphPathMapper.isGraphJsonPath(path.toString()))
                        .forEach(file -> {
                            GraphAssetDescriptor descriptor = loadDescriptor(folder, file);
                            if (descriptor != null) loadedGraphs.put(descriptor.graphId(), descriptor);
                        });
            }
            GeometryNode.LOGGER.info("[DynamicGraphManager] Total load {} graphs.", loadedGraphs.size());
            return loadedGraphs;
        } catch (Exception error) {
            throw new IllegalStateException("Failed to scan the graph repository", error);
        }
    }

    private static GraphAssetDescriptor loadDescriptor(Path root, Path file) {
        try {
            String graphId = GraphPathMapper.pathToId(root, file);
            try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                JsonElement parsed = JsonParser.parseReader(reader);
                if (!parsed.isJsonObject()) return null;
                JsonObject document = parsed.getAsJsonObject();
                GraphType type = GraphDocumentType.require(document);
                CompiledGraph artifact = GraphCompilationService.INSTANCE.compile(graphId, document);
                return new GraphAssetDescriptor(
                        graphId, type, artifact, GraphAssetFingerprint.of(document));
            }
        } catch (Exception error) {
            GeometryNode.LOGGER.error("[DynamicGraphManager] Failed to load graph: {}", file, error);
            return null;
        }
    }

    private static void publishDynamicSnapshot(
            MinecraftServer server, Map<String, GraphAssetDescriptor> loadedGraphs) {
        GraphAssetLifecycleIndex.INSTANCE.replaceDynamicGraphs(server, loadedGraphs);
    }

    private static boolean isCurrent(MinecraftServer server, long generation) {
        synchronized (PENDING) {
            return SERVER_GENERATIONS.getOrDefault(server, 0L) == generation;
        }
    }

    private static void removePublication(MinecraftServer server, CompletableFuture<Void> publication) {
        synchronized (PENDING) {
            Set<CompletableFuture<Void>> publications = ACTIVE_PUBLICATIONS.get(server);
            if (publications == null) return;
            publications.remove(publication);
            if (publications.isEmpty()) ACTIVE_PUBLICATIONS.remove(server);
        }
    }

    private record IncrementalLoad(Map<String, GraphAssetDescriptor> graphs,
                                   Set<String> affectedGraphIds) {
    }
}
