package com.mine.geometry_node.core.engine.graph.storage;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.core.engine.graph.GraphKind;
import com.mine.geometry_node.core.engine.graph.compile.artifact.CompiledGraph;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Event-driven runtime view of graphs published by the server graph repository.
 * Publishes complete immutable snapshots and notifies each runtime about its changed assets.
 */
public final class GraphAssetLifecycleIndex {
    public static final GraphAssetLifecycleIndex INSTANCE = new GraphAssetLifecycleIndex();

    private final Map<GraphKind, CopyOnWriteArrayList<ChangeListener>> listeners =
            new ConcurrentHashMap<>();
    private volatile Snapshot snapshot = Snapshot.EMPTY;

    GraphAssetLifecycleIndex() {
    }

    @FunctionalInterface
    public interface ChangeListener {
        void onGraphAssetsChanged(Change change);
    }

    public record Change(@Nullable MinecraftServer server, GraphKind runtimeKind,
                         Set<String> assetIds) {
        public Change {
            Objects.requireNonNull(runtimeKind, "runtimeKind");
            assetIds = Set.copyOf(assetIds);
        }
    }

    public void addChangeListener(GraphKind runtimeKind, ChangeListener listener) {
        if (runtimeKind == null || runtimeKind == GraphKind.UNKNOWN || listener == null) return;
        listeners.computeIfAbsent(runtimeKind, ignored -> new CopyOnWriteArrayList<>())
                .addIfAbsent(listener);
    }

    public void replaceDynamicGraphs(MinecraftServer server,
                                     Map<String, GraphAssetDescriptor> graphs) {
        update(server, graphs);
    }

    @Nullable
    public GraphAssetDescriptor getGraph(String graphId) {
        return snapshot.effective.get(GraphAssetId.canonicalize(graphId));
    }

    @Nullable
    public CompiledGraph getArtifact(String graphId, GraphKind runtimeKind) {
        GraphAssetDescriptor descriptor = snapshot.effective.get(GraphAssetId.canonicalize(graphId));
        return descriptor != null && descriptor.runtimeKind() == runtimeKind
                ? descriptor.artifact() : null;
    }

    public Set<String> getGraphIds() {
        return snapshot.effective.keySet();
    }

    public Set<String> getGraphIds(GraphKind runtimeKind) {
        if (runtimeKind == null || runtimeKind == GraphKind.UNKNOWN) return Set.of();
        Set<String> result = new TreeSet<>();
        snapshot.effective.forEach((graphId, descriptor) -> {
            if (descriptor.runtimeKind() == runtimeKind) result.add(graphId);
        });
        return Collections.unmodifiableSet(result);
    }

    private void update(@Nullable MinecraftServer server,
                        Map<String, GraphAssetDescriptor> dynamicReplacement) {
        Map<GraphKind, Change> changes;
        synchronized (this) {
            Snapshot previous = snapshot;
            snapshot = new Snapshot(server, canonicalizeDescriptors(dynamicReplacement));
            Snapshot comparisonBase = previous.belongsTo(server) ? previous : Snapshot.EMPTY;
            changes = calculateChanges(server, comparisonBase, snapshot);
        }
        changes.forEach(this::notifyListeners);
    }

    private static Map<String, GraphAssetDescriptor> canonicalizeDescriptors(
            Map<String, GraphAssetDescriptor> descriptors) {
        Map<String, GraphAssetDescriptor> canonical = new LinkedHashMap<>();
        for (GraphAssetDescriptor descriptor : descriptors.values()) {
            GraphAssetDescriptor previous = canonical.putIfAbsent(descriptor.graphId(), descriptor);
            if (previous != null && previous != descriptor) {
                throw new IllegalArgumentException("Duplicate canonical graph id: " + descriptor.graphId());
            }
        }
        return Map.copyOf(canonical);
    }

    private void notifyListeners(GraphKind kind, Change change) {
        CopyOnWriteArrayList<ChangeListener> kindListeners = listeners.get(kind);
        if (kindListeners == null) return;
        for (ChangeListener listener : kindListeners) {
            try {
                listener.onGraphAssetsChanged(change);
            } catch (RuntimeException exception) {
                GeometryNode.LOGGER.error("Graph asset lifecycle listener failed for {}: assets={}",
                        kind.id(), change.assetIds(), exception);
            }
        }
    }

    private static Map<GraphKind, Change> calculateChanges(@Nullable MinecraftServer server,
                                                            Snapshot previous, Snapshot current) {
        Set<String> changedAssets = new TreeSet<>();
        Set<String> ids = new HashSet<>(previous.effective.keySet());
        ids.addAll(current.effective.keySet());
        for (String graphId : ids) {
            if (!sameContent(previous.effective.get(graphId), current.effective.get(graphId))) {
                changedAssets.add(graphId);
            }
        }
        if (changedAssets.isEmpty()) return Map.of();

        Map<GraphKind, Set<String>> changedByKind = new HashMap<>();
        for (String graphId : changedAssets) {
            for (GraphKind kind : kindsOf(graphId, previous, current)) {
                changedByKind.computeIfAbsent(kind, ignored -> new TreeSet<>()).add(graphId);
            }
        }

        Map<GraphKind, Change> result = new HashMap<>();
        changedByKind.forEach((kind, assetIds) -> result.put(kind,
                new Change(server, kind, assetIds)));
        return result;
    }

    private static boolean sameContent(@Nullable GraphAssetDescriptor first,
                                       @Nullable GraphAssetDescriptor second) {
        return first == second || first != null && first.hasSameContent(second);
    }

    private static Set<GraphKind> kindsOf(String graphId, Snapshot previous, Snapshot current) {
        Set<GraphKind> kinds = java.util.EnumSet.noneOf(GraphKind.class);
        GraphAssetDescriptor oldDescriptor = previous.effective.get(graphId);
        GraphAssetDescriptor newDescriptor = current.effective.get(graphId);
        if (oldDescriptor != null) kinds.add(oldDescriptor.runtimeKind());
        if (newDescriptor != null) kinds.add(newDescriptor.runtimeKind());
        return kinds;
    }

    private record Snapshot(WeakReference<MinecraftServer> serverReference,
                            Map<String, GraphAssetDescriptor> effective) {
        private static final Snapshot EMPTY = new Snapshot((MinecraftServer) null, Map.of());

        private Snapshot(@Nullable MinecraftServer server,
                         Map<String, GraphAssetDescriptor> effective) {
            this(new WeakReference<>(server), effective);
        }

        private boolean belongsTo(@Nullable MinecraftServer server) {
            return serverReference.get() == server;
        }
    }
}
