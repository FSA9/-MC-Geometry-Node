package com.mine.geometry_node.core.engine.graph.storage;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.core.engine.graph.GraphKind;
import com.mine.geometry_node.core.engine.graph.compile.artifact.CompiledGraph;
import com.mine.geometry_node.core.engine.runtime.ServerEngine;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Owns loading, publication, querying and lifecycle for every server graph repository. */
public final class ServerGraphRepository implements ServerEngine {
    public static final ServerGraphRepository INSTANCE = new ServerGraphRepository();

    private final ExecutorService buildExecutor = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "GeometryNode-GraphRepository-Build");
        thread.setDaemon(true);
        return thread;
    });
    private final Object stateLock = new Object();
    private final Map<MinecraftServer, ServerState> states = new IdentityHashMap<>();
    private final Map<GraphKind, CopyOnWriteArrayList<ChangeListener>> listeners =
            new EnumMap<>(GraphKind.class);

    private ServerGraphRepository() {
    }

    @Override
    public String id() {
        return "geometry_node:server_graph_repository";
    }

    @Override
    public int tickOrder() {
        return Integer.MAX_VALUE;
    }

    /** Startup has no active tick loop, so the first snapshot is built synchronously. */
    public void start(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        long generation;
        synchronized (stateLock) {
            ServerState state = states.computeIfAbsent(server, ignored -> new ServerState());
            state.active = true;
            generation = ++state.generation;
        }
        try {
            publish(server, generation, GraphRepositoryLoader.loadAll(server), null);
        } catch (RuntimeException error) {
            GeometryNode.LOGGER.error("[ServerGraphRepository] Initial graph load failed", error);
        }
    }

    /** Schedules an ordered background rebuild after an asset mutation. */
    public CompletableFuture<Void> refresh(MinecraftServer server, Set<String> affectedPaths,
                                           boolean directoryScope) {
        if (server == null) return CompletableFuture.completedFuture(null);
        Set<String> paths = affectedPaths == null ? Set.of() : Set.copyOf(affectedPaths);
        CompletableFuture<Void> next;
        synchronized (stateLock) {
            ServerState state = states.get(server);
            if (state == null || !state.active) return CompletableFuture.completedFuture(null);
            long generation = state.generation;
            CompletableFuture<Void> previous = state.pending;
            next = previous.handle((ignored, error) -> null)
                    .thenComposeAsync(ignored -> rebuildAndPublish(
                            server, paths, directoryScope, generation), buildExecutor);
            state.pending = next;
        }
        next.whenComplete((ignored, error) -> clearPending(server, next));
        return next;
    }

    public CompletableFuture<Void> refreshAll(MinecraftServer server) {
        return refresh(server, Set.of(), true);
    }

    public void addChangeListener(GraphKind runtimeKind, ChangeListener listener) {
        if (runtimeKind == null || runtimeKind == GraphKind.UNKNOWN || listener == null) return;
        synchronized (listeners) {
            listeners.computeIfAbsent(runtimeKind, ignored -> new CopyOnWriteArrayList<>())
                    .addIfAbsent(listener);
        }
    }

    @Nullable
    public GraphAssetDescriptor getGraph(MinecraftServer server, String graphId) {
        return snapshot(server).graphs().get(GraphAssetId.canonicalize(graphId));
    }

    @Nullable
    public CompiledGraph getArtifact(MinecraftServer server, String graphId, GraphKind runtimeKind) {
        GraphAssetDescriptor descriptor = getGraph(server, graphId);
        return descriptor != null && descriptor.runtimeKind() == runtimeKind
                ? descriptor.artifact() : null;
    }

    public Set<String> getGraphIds(MinecraftServer server) {
        return snapshot(server).graphs().keySet();
    }

    public Set<String> getGraphIds(MinecraftServer server, GraphKind runtimeKind) {
        if (runtimeKind == null || runtimeKind == GraphKind.UNKNOWN) return Set.of();
        Set<String> result = new TreeSet<>();
        snapshot(server).graphs().forEach((graphId, descriptor) -> {
            if (descriptor.runtimeKind() == runtimeKind) result.add(graphId);
        });
        return Collections.unmodifiableSet(result);
    }

    @Override
    public void shutdown(MinecraftServer server) {
        if (server == null) return;
        ServerState removed;
        synchronized (stateLock) {
            removed = states.remove(server);
            if (removed == null) return;
            removed.active = false;
            removed.generation++;
        }
        removed.pending.cancel(false);
        for (CompletableFuture<Void> publication : removed.publications) {
            publication.cancel(false);
        }
    }

    private CompletableFuture<Void> rebuildAndPublish(MinecraftServer server, Set<String> paths,
                                                       boolean directoryScope, long generation) {
        if (!isCurrent(server, generation)) return CompletableFuture.completedFuture(null);
        try {
            GraphRepositorySnapshot base = snapshot(server);
            Map<String, GraphAssetDescriptor> graphs;
            Set<String> affectedGraphIds = null;
            if (directoryScope || paths.isEmpty()) {
                graphs = GraphRepositoryLoader.loadAll(server);
            } else {
                GraphRepositoryLoader.IncrementalLoad load = GraphRepositoryLoader.loadChanged(
                        server, base.graphs(), paths);
                graphs = load.graphs();
                affectedGraphIds = load.affectedGraphIds();
            }
            if (!isCurrent(server, generation)) return CompletableFuture.completedFuture(null);
            GraphRepositorySnapshot replacement = new GraphRepositorySnapshot(graphs);
            Map<GraphKind, Change> changes = calculateChanges(
                    server, base, replacement, affectedGraphIds);
            CompletableFuture<Void> publication = new CompletableFuture<>();
            synchronized (stateLock) {
                ServerState state = states.get(server);
                if (state == null || !state.active || state.generation != generation) {
                    return CompletableFuture.completedFuture(null);
                }
                state.publications.add(publication);
            }
            publication.whenComplete((ignored, error) -> removePublication(server, publication));
            try {
                server.execute(() -> {
                    try {
                        publish(server, generation, replacement, base, changes);
                        publication.complete(null);
                    } catch (Throwable error) {
                        publication.completeExceptionally(error);
                    }
                });
            } catch (Throwable error) {
                publication.completeExceptionally(error);
            }
            return publication;
        } catch (Throwable error) {
            return CompletableFuture.failedFuture(error);
        }
    }

    private void publish(MinecraftServer server, long generation,
                         Map<String, GraphAssetDescriptor> graphs,
                         @Nullable Set<String> affectedGraphIds) {
        GraphRepositorySnapshot replacement = new GraphRepositorySnapshot(graphs);
        GraphRepositorySnapshot previous = snapshot(server);
        publish(server, generation, replacement, previous,
                calculateChanges(server, previous, replacement, affectedGraphIds));
    }

    private void publish(MinecraftServer server, long generation,
                         GraphRepositorySnapshot replacement, GraphRepositorySnapshot expected,
                         Map<GraphKind, Change> changes) {
        synchronized (stateLock) {
            ServerState state = states.get(server);
            if (state == null || !state.active || state.generation != generation) return;
            if (state.snapshot != expected) {
                throw new IllegalStateException(
                        "Graph snapshot changed while an update was being prepared");
            }
            state.snapshot = replacement;
        }
        changes.values().forEach(this::notifyListeners);
    }

    private GraphRepositorySnapshot snapshot(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        synchronized (stateLock) {
            ServerState state = states.get(server);
            return state != null ? state.snapshot : GraphRepositorySnapshot.EMPTY;
        }
    }

    private boolean isCurrent(MinecraftServer server, long generation) {
        synchronized (stateLock) {
            ServerState state = states.get(server);
            return state != null && state.active && state.generation == generation;
        }
    }

    private void clearPending(MinecraftServer server, CompletableFuture<Void> pending) {
        synchronized (stateLock) {
            ServerState state = states.get(server);
            if (state != null && state.pending == pending) {
                state.pending = CompletableFuture.completedFuture(null);
            }
        }
    }

    private void removePublication(MinecraftServer server, CompletableFuture<Void> publication) {
        synchronized (stateLock) {
            ServerState state = states.get(server);
            if (state != null) state.publications.remove(publication);
        }
    }

    private void notifyListeners(Change change) {
        CopyOnWriteArrayList<ChangeListener> kindListeners;
        synchronized (listeners) {
            kindListeners = listeners.get(change.runtimeKind());
        }
        if (kindListeners == null) return;
        for (ChangeListener listener : kindListeners) {
            try {
                listener.onGraphAssetsChanged(change);
            } catch (RuntimeException exception) {
                GeometryNode.LOGGER.error("Graph asset lifecycle listener failed for {}: assets={}",
                        change.runtimeKind().id(), change.assetIds(), exception);
            }
        }
    }

    private static Map<GraphKind, Change> calculateChanges(
            MinecraftServer server, GraphRepositorySnapshot previous,
            GraphRepositorySnapshot current, @Nullable Set<String> candidateIds) {
        Set<String> ids = candidateIds == null
                ? new HashSet<>(previous.graphs().keySet())
                : new HashSet<>(candidateIds);
        if (candidateIds == null) ids.addAll(current.graphs().keySet());
        Set<String> changedAssets = new TreeSet<>();
        for (String graphId : ids) {
            if (!sameContent(previous.graphs().get(graphId), current.graphs().get(graphId))) {
                changedAssets.add(graphId);
            }
        }
        if (changedAssets.isEmpty()) return Map.of();

        Map<GraphKind, Set<String>> changedByKind = new EnumMap<>(GraphKind.class);
        for (String graphId : changedAssets) {
            GraphAssetDescriptor oldDescriptor = previous.graphs().get(graphId);
            GraphAssetDescriptor newDescriptor = current.graphs().get(graphId);
            if (oldDescriptor != null) {
                changedByKind.computeIfAbsent(oldDescriptor.runtimeKind(), ignored -> new TreeSet<>())
                        .add(graphId);
            }
            if (newDescriptor != null) {
                changedByKind.computeIfAbsent(newDescriptor.runtimeKind(), ignored -> new TreeSet<>())
                        .add(graphId);
            }
        }
        Map<GraphKind, Change> result = new EnumMap<>(GraphKind.class);
        changedByKind.forEach((kind, assetIds) -> result.put(kind,
                new Change(server, kind, assetIds)));
        return result;
    }

    private static boolean sameContent(@Nullable GraphAssetDescriptor first,
                                       @Nullable GraphAssetDescriptor second) {
        return first == second || first != null && first.hasSameContent(second);
    }

    @FunctionalInterface
    public interface ChangeListener {
        void onGraphAssetsChanged(Change change);
    }

    public record Change(MinecraftServer server, GraphKind runtimeKind, Set<String> assetIds) {
        public Change {
            Objects.requireNonNull(server, "server");
            Objects.requireNonNull(runtimeKind, "runtimeKind");
            assetIds = Set.copyOf(assetIds);
        }
    }

    private static final class ServerState {
        private GraphRepositorySnapshot snapshot = GraphRepositorySnapshot.EMPTY;
        private long generation;
        private boolean active;
        private CompletableFuture<Void> pending = CompletableFuture.completedFuture(null);
        private final Set<CompletableFuture<Void>> publications = new HashSet<>();
    }
}
