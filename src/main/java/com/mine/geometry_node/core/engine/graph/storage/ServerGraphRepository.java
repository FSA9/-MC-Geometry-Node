package com.mine.geometry_node.core.engine.graph.storage;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.core.engine.graph.GraphKind;
import com.mine.geometry_node.core.engine.graph.compile.artifact.CompiledGraph;
import com.mine.geometry_node.core.engine.runtime.ServerEngine;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
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

    /**
     * Marks repository paths dirty and waits for a snapshot containing this request.
     * Concurrent requests are coalesced per server; a full refresh supersedes paths.
     */
    public CompletableFuture<Void> refresh(MinecraftServer server, Set<String> affectedPaths,
                                           boolean directoryScope) {
        if (server == null) return CompletableFuture.completedFuture(null);
        Set<String> paths = affectedPaths == null ? Set.of() : Set.copyOf(affectedPaths);
        CompletableFuture<Void> completion = new CompletableFuture<>();
        boolean startDrain = false;
        long generation = -1L;
        synchronized (stateLock) {
            ServerState state = states.get(server);
            if (state == null || !state.active) return CompletableFuture.completedFuture(null);
            mergeDirty(state, paths, directoryScope || paths.isEmpty());
            state.pendingWaiters.add(completion);
            state.outstandingWaiters.add(completion);
            if (!state.drainRunning) {
                state.drainRunning = true;
                startDrain = true;
                generation = state.generation;
            }
        }
        completion.whenComplete((ignored, error) -> removeWaiter(server, completion));
        if (startDrain) {
            long scheduledGeneration = generation;
            buildExecutor.execute(() -> drain(server, scheduledGeneration));
        }
        return completion;
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
        for (CompletableFuture<Void> waiter : removed.outstandingWaiters) {
            waiter.cancel(false);
        }
        for (CompletableFuture<Void> publication : removed.publications) {
            publication.cancel(false);
        }
    }

    private void drain(MinecraftServer server, long generation) {
        RefreshBatch batch;
        synchronized (stateLock) {
            ServerState state = states.get(server);
            if (state == null || !state.active || state.generation != generation) return;
            if (state.pendingWaiters.isEmpty()) {
                state.drainRunning = false;
                return;
            }
            batch = new RefreshBatch(Set.copyOf(state.dirtyPaths), state.fullRefreshRequested,
                    List.copyOf(state.pendingWaiters));
            state.dirtyPaths.clear();
            state.fullRefreshRequested = false;
            state.pendingWaiters.clear();
        }

        rebuildAndPublish(server, batch.paths(), batch.fullRefresh(), generation)
                .whenCompleteAsync((ignored, error) ->
                        finishBatch(server, generation, batch, error), buildExecutor);
    }

    private void finishBatch(MinecraftServer server, long generation, RefreshBatch batch,
                             @Nullable Throwable error) {
        synchronized (stateLock) {
            ServerState state = states.get(server);
            if (error != null && state != null && state.active && state.generation == generation) {
                mergeDirty(state, batch.paths(), batch.fullRefresh());
            }
        }

        for (CompletableFuture<Void> waiter : batch.waiters()) {
            if (error == null) waiter.complete(null);
            else waiter.completeExceptionally(error);
        }

        boolean continueDrain;
        synchronized (stateLock) {
            ServerState state = states.get(server);
            if (state == null || !state.active || state.generation != generation) return;
            continueDrain = !state.pendingWaiters.isEmpty();
            if (!continueDrain) state.drainRunning = false;
        }
        if (continueDrain) drain(server, generation);
    }

    private static void mergeDirty(ServerState state, Set<String> paths, boolean fullRefresh) {
        if (fullRefresh) {
            state.fullRefreshRequested = true;
            state.dirtyPaths.clear();
        } else if (!state.fullRefreshRequested) {
            state.dirtyPaths.addAll(paths);
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

    private void removeWaiter(MinecraftServer server, CompletableFuture<Void> waiter) {
        synchronized (stateLock) {
            ServerState state = states.get(server);
            if (state != null) state.outstandingWaiters.remove(waiter);
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
        private final Set<String> dirtyPaths = new HashSet<>();
        private final List<CompletableFuture<Void>> pendingWaiters = new ArrayList<>();
        private final Set<CompletableFuture<Void>> outstandingWaiters = new HashSet<>();
        private boolean fullRefreshRequested;
        private boolean drainRunning;
        private final Set<CompletableFuture<Void>> publications = new HashSet<>();
    }

    private record RefreshBatch(Set<String> paths, boolean fullRefresh,
                                List<CompletableFuture<Void>> waiters) {
    }
}
