package com.mine.geometry_node.core.engine.graph.storage;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.core.engine.graph.GraphKind;
import com.mine.geometry_node.core.engine.graph.compile.artifact.CompiledGraph;
import com.mine.geometry_node.core.engine.graph.compile.dependency.CompiledGraphDependencies;
import com.mine.geometry_node.core.engine.graph.compile.dependency.GraphDependencyDiagnostic;
import com.mine.geometry_node.core.engine.graph.compile.dependency.GraphDependencyValidator;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Event-driven effective graph view. Dynamic assets shadow packaged assets, including a
 * dynamically stored document that currently fails compilation. Dependency validation and
 * reverse dependency traversal are centralized here so runtimes do not need cache scans.
 */
public final class GraphAssetLifecycleIndex {
    public static final GraphAssetLifecycleIndex INSTANCE = new GraphAssetLifecycleIndex();

    private final Map<GraphKind, CopyOnWriteArrayList<ChangeListener>> listeners =
            new ConcurrentHashMap<>();
    private Map<String, GraphAssetDescriptor> packaged = Map.of();
    private Map<String, GraphAssetDescriptor> dynamic = Map.of();
    private Set<String> invalidDynamicIds = Set.of();
    private Snapshot snapshot = Snapshot.EMPTY;

    GraphAssetLifecycleIndex() {
    }

    @FunctionalInterface
    public interface ChangeListener {
        void onGraphAssetsChanged(Change change);
    }

    public record Change(@Nullable MinecraftServer server, GraphKind runtimeKind,
                         Set<String> changedAssetIds, Set<String> affectedAssetIds) {
        public Change {
            Objects.requireNonNull(runtimeKind, "runtimeKind");
            changedAssetIds = Set.copyOf(changedAssetIds);
            affectedAssetIds = Set.copyOf(affectedAssetIds);
        }
    }

    public void addChangeListener(GraphKind runtimeKind, ChangeListener listener) {
        if (runtimeKind == null || runtimeKind == GraphKind.UNKNOWN || listener == null) return;
        listeners.computeIfAbsent(runtimeKind, ignored -> new CopyOnWriteArrayList<>())
                .addIfAbsent(listener);
    }

    public void replacePackagedGraphs(Map<String, GraphAssetDescriptor> graphs) {
        update(null, graphs, null, null);
    }

    public void replaceDynamicGraphs(MinecraftServer server,
                                     Map<String, GraphAssetDescriptor> graphs,
                                     Set<String> invalidIds) {
        update(server, null, graphs, invalidIds);
    }

    @Nullable
    public synchronized GraphAssetDescriptor getGraph(String graphId) {
        return snapshot.effective.get(graphId);
    }

    @Nullable
    public synchronized CompiledGraph getArtifact(String graphId, GraphKind runtimeKind) {
        GraphAssetDescriptor descriptor = snapshot.effective.get(graphId);
        return descriptor != null && descriptor.runtimeKind() == runtimeKind
                ? descriptor.artifact() : null;
    }

    public synchronized Set<String> getGraphIds() {
        return snapshot.effective.keySet();
    }

    public synchronized Set<String> getGraphIds(GraphKind runtimeKind) {
        if (runtimeKind == null || runtimeKind == GraphKind.UNKNOWN) return Set.of();
        Set<String> result = new TreeSet<>();
        snapshot.effective.forEach((graphId, descriptor) -> {
            if (descriptor.runtimeKind() == runtimeKind) result.add(graphId);
        });
        return Collections.unmodifiableSet(result);
    }

    public synchronized Set<String> invalidGraphIds() {
        return snapshot.invalidIds;
    }

    public synchronized List<GraphDependencyDiagnostic> diagnostics(String graphId) {
        return snapshot.diagnostics.getOrDefault(graphId, List.of());
    }

    public synchronized Map<String, List<GraphDependencyDiagnostic>> diagnostics() {
        return snapshot.diagnostics;
    }

    public synchronized Set<String> directDependents(String graphId) {
        return snapshot.reverseDependencies.getOrDefault(graphId, Set.of());
    }

    public synchronized Set<String> affectedBy(Set<String> graphIds) {
        return dependencyClosure(graphIds, snapshot.reverseDependencies);
    }

    private void update(@Nullable MinecraftServer server,
                        @Nullable Map<String, GraphAssetDescriptor> packagedReplacement,
                        @Nullable Map<String, GraphAssetDescriptor> dynamicReplacement,
                        @Nullable Set<String> invalidDynamicReplacement) {
        Map<GraphKind, Change> changes;
        synchronized (this) {
            Snapshot previous = snapshot;
            if (packagedReplacement != null) packaged = Map.copyOf(packagedReplacement);
            if (dynamicReplacement != null) dynamic = Map.copyOf(dynamicReplacement);
            if (invalidDynamicReplacement != null) {
                invalidDynamicIds = Set.copyOf(invalidDynamicReplacement);
            }
            snapshot = buildSnapshot(packaged, dynamic, invalidDynamicIds);
            changes = calculateChanges(server, previous, snapshot);
        }
        changes.forEach(this::notifyListeners);
    }

    private void notifyListeners(GraphKind kind, Change change) {
        CopyOnWriteArrayList<ChangeListener> kindListeners = listeners.get(kind);
        if (kindListeners == null) return;
        for (ChangeListener listener : kindListeners) {
            try {
                listener.onGraphAssetsChanged(change);
            } catch (RuntimeException exception) {
                GeometryNode.LOGGER.error("Graph asset lifecycle listener failed for {}: affected={}",
                        kind.id(), change.affectedAssetIds(), exception);
            }
        }
    }

    private static Snapshot buildSnapshot(Map<String, GraphAssetDescriptor> packaged,
                                          Map<String, GraphAssetDescriptor> dynamic,
                                          Set<String> invalidDynamicIds) {
        Map<String, GraphAssetDescriptor> selected = new LinkedHashMap<>();
        Set<String> allIds = new TreeSet<>(packaged.keySet());
        allIds.addAll(dynamic.keySet());
        allIds.addAll(invalidDynamicIds);
        for (String graphId : allIds) {
            if (invalidDynamicIds.contains(graphId)) continue;
            GraphAssetDescriptor descriptor = dynamic.get(graphId);
            if (descriptor == null) descriptor = packaged.get(graphId);
            if (descriptor != null) selected.put(graphId, descriptor);
        }

        Map<String, Set<String>> reverse = new HashMap<>();
        selected.forEach((graphId, descriptor) -> dependenciesOf(descriptor.artifact())
                .forEach(dependency -> reverse.computeIfAbsent(dependency,
                        ignored -> new TreeSet<>()).add(graphId)));

        Set<String> invalid = new TreeSet<>(invalidDynamicIds);
        Map<String, LinkedHashSet<GraphDependencyDiagnostic>> diagnostics = new TreeMap<>();
        invalidDynamicIds.forEach(graphId -> addDiagnostic(diagnostics,
                new GraphDependencyDiagnostic(graphId, "DYNAMIC_GRAPH_COMPILE_INVALID",
                        "Dynamic graph document does not have a compiled artifact", "", graphId)));
        boolean changed;
        do {
            changed = false;
            for (Map.Entry<String, GraphAssetDescriptor> entry : selected.entrySet()) {
                if (invalid.contains(entry.getKey())) continue;
                for (String dependencyId : requiredDependenciesOf(entry.getValue().artifact())) {
                    GraphAssetDescriptor dependency = selected.get(dependencyId);
                    if (dependency == null || invalid.contains(dependencyId)
                            || dependency.runtimeKind() != entry.getValue().runtimeKind()) {
                        changed |= invalid.add(entry.getKey());
                        addDiagnostic(diagnostics, new GraphDependencyDiagnostic(
                                entry.getKey(), dependency == null
                                        ? "GRAPH_DEPENDENCY_MISSING" : "GRAPH_DEPENDENCY_INVALID",
                                dependency == null ? "Graph dependency is unavailable"
                                        : "Graph dependency is invalid or belongs to another runtime kind",
                                "", dependencyId));
                        break;
                    }
                }
            }
        } while (changed);

        Map<String, CompiledGraph> cycleCandidates = new HashMap<>();
        selected.forEach((graphId, descriptor) -> {
            if (!invalid.contains(graphId)) cycleCandidates.put(graphId, descriptor.artifact());
        });
        Set<String> cycleInvalid = GraphDependencyValidator.findInvalidGraphs(cycleCandidates);
        for (String graphId : cycleInvalid) {
            invalid.add(graphId);
            addDiagnostic(diagnostics, new GraphDependencyDiagnostic(graphId,
                    "GRAPH_DEPENDENCY_CYCLE", "Graph belongs to or transitively depends on a cycle",
                    "", graphId));
        }

        Map<String, GraphAssetDescriptor> effective = new LinkedHashMap<>(selected);
        invalid.forEach(effective::remove);
        Map<String, Set<String>> immutableReverse = new HashMap<>();
        reverse.forEach((graphId, dependents) -> immutableReverse.put(graphId, Set.copyOf(dependents)));
        Map<String, List<GraphDependencyDiagnostic>> immutableDiagnostics = new TreeMap<>();
        diagnostics.forEach((graphId, values) -> immutableDiagnostics.put(graphId, List.copyOf(values)));
        return new Snapshot(Map.copyOf(effective), Map.copyOf(selected),
                Map.copyOf(immutableReverse), Set.copyOf(invalid), Map.copyOf(immutableDiagnostics));
    }

    private static Map<GraphKind, Change> calculateChanges(@Nullable MinecraftServer server,
                                                            Snapshot previous, Snapshot current) {
        Set<String> changedAssets = new TreeSet<>();
        Set<String> ids = new HashSet<>(previous.effective.keySet());
        ids.addAll(current.effective.keySet());
        ids.addAll(previous.selected.keySet());
        ids.addAll(current.selected.keySet());
        ids.addAll(previous.diagnostics.keySet());
        ids.addAll(current.diagnostics.keySet());
        for (String graphId : ids) {
            if (previous.effective.get(graphId) != current.effective.get(graphId)
                    || previous.selected.get(graphId) != current.selected.get(graphId)
                    || !previous.diagnostics.getOrDefault(graphId, List.of())
                    .equals(current.diagnostics.getOrDefault(graphId, List.of()))) {
                changedAssets.add(graphId);
            }
        }
        if (changedAssets.isEmpty()) return Map.of();

        Set<String> affected = new TreeSet<>(dependencyClosure(changedAssets,
                previous.reverseDependencies));
        affected.addAll(dependencyClosure(changedAssets, current.reverseDependencies));
        Map<GraphKind, Set<String>> changedByKind = new HashMap<>();
        Map<GraphKind, Set<String>> affectedByKind = new HashMap<>();
        for (String graphId : changedAssets) {
            for (GraphKind kind : kindsOf(graphId, previous, current)) {
                changedByKind.computeIfAbsent(kind, ignored -> new TreeSet<>()).add(graphId);
            }
        }
        for (String graphId : affected) {
            for (GraphKind kind : kindsOf(graphId, previous, current)) {
                affectedByKind.computeIfAbsent(kind, ignored -> new TreeSet<>()).add(graphId);
            }
        }

        Map<GraphKind, Change> result = new HashMap<>();
        affectedByKind.forEach((kind, affectedIds) -> result.put(kind, new Change(server, kind,
                changedByKind.getOrDefault(kind, Set.of()), affectedIds)));
        return result;
    }

    private static Set<GraphKind> kindsOf(String graphId, Snapshot previous, Snapshot current) {
        Set<GraphKind> kinds = java.util.EnumSet.noneOf(GraphKind.class);
        GraphAssetDescriptor oldDescriptor = previous.selected.get(graphId);
        GraphAssetDescriptor newDescriptor = current.selected.get(graphId);
        if (oldDescriptor != null) kinds.add(oldDescriptor.runtimeKind());
        if (newDescriptor != null) kinds.add(newDescriptor.runtimeKind());
        return kinds;
    }

    private static Set<String> dependencyClosure(Set<String> roots,
                                                  Map<String, Set<String>> reverseDependencies) {
        Set<String> result = new LinkedHashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>(new TreeSet<>(roots));
        while (!queue.isEmpty()) {
            String graphId = queue.removeFirst();
            if (!result.add(graphId)) continue;
            reverseDependencies.getOrDefault(graphId, Set.of()).stream().sorted()
                    .forEach(queue::addLast);
        }
        return Collections.unmodifiableSet(result);
    }

    private static Set<String> dependenciesOf(CompiledGraph graph) {
        return graph instanceof CompiledGraphDependencies dependencies
                ? dependencies.graphDependencies() : Set.of();
    }

    private static Set<String> requiredDependenciesOf(CompiledGraph graph) {
        return graph instanceof CompiledGraphDependencies dependencies
                && dependencies.requiresAvailableDependencies()
                ? dependencies.graphDependencies() : Set.of();
    }

    private static void addDiagnostic(
            Map<String, LinkedHashSet<GraphDependencyDiagnostic>> diagnostics,
            GraphDependencyDiagnostic diagnostic) {
        diagnostics.computeIfAbsent(diagnostic.assetId(), ignored -> new LinkedHashSet<>())
                .add(diagnostic);
    }

    private record Snapshot(Map<String, GraphAssetDescriptor> effective,
                            Map<String, GraphAssetDescriptor> selected,
                            Map<String, Set<String>> reverseDependencies,
                            Set<String> invalidIds,
                            Map<String, List<GraphDependencyDiagnostic>> diagnostics) {
        private static final Snapshot EMPTY = new Snapshot(Map.of(), Map.of(), Map.of(), Set.of(), Map.of());
    }
}
