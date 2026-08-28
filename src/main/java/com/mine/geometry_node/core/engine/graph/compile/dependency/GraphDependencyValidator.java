package com.mine.geometry_node.core.engine.graph.compile.dependency;

import com.mine.geometry_node.core.engine.graph.compile.artifact.CompiledGraph;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Deterministic cycle detection over already compiled graph dependency manifests. */
public final class GraphDependencyValidator {
    private GraphDependencyValidator() {
    }

    public static List<String> findCycle(Map<String, ? extends CompiledGraph> graphs) {
        Map<String, VisitState> states = new HashMap<>();
        for (String start : new TreeSet<>(graphs.keySet())) {
            if (states.get(start) == VisitState.DONE) continue;
            List<String> path = new ArrayList<>();
            Deque<TraversalFrame> stack = new ArrayDeque<>();
            states.put(start, VisitState.ACTIVE);
            path.add(start);
            stack.push(new TraversalFrame(start, dependenciesOf(graphs.get(start)).iterator()));
            while (!stack.isEmpty()) {
                TraversalFrame frame = stack.peek();
                if (!frame.dependencies.hasNext()) {
                    stack.pop();
                    path.removeLast();
                    states.put(frame.graphId, VisitState.DONE);
                    continue;
                }
                String dependency = frame.dependencies.next();
                if (!graphs.containsKey(dependency)) continue;
                VisitState state = states.get(dependency);
                if (state == VisitState.ACTIVE) {
                    int cycleStart = path.indexOf(dependency);
                    List<String> cycle = new ArrayList<>(path.subList(cycleStart, path.size()));
                    cycle.add(dependency);
                    return List.copyOf(cycle);
                }
                if (state == VisitState.DONE) continue;
                states.put(dependency, VisitState.ACTIVE);
                path.add(dependency);
                stack.push(new TraversalFrame(dependency,
                        dependenciesOf(graphs.get(dependency)).iterator()));
            }
        }
        return List.of();
    }

    /**
     * Returns every graph that must be rejected because it belongs to a dependency cycle or
     * transitively depends on one. The result order is stable for diagnostics and reload logs.
     */
    public static Set<String> findInvalidGraphs(Map<String, ? extends CompiledGraph> graphs) {
        Set<String> rejected = new TreeSet<>();
        Map<String, CompiledGraph> remaining = new HashMap<>(graphs);
        while (true) {
            List<String> cycle = findCycle(remaining);
            if (cycle.isEmpty()) break;
            rejected.addAll(cycle);
            rejected.forEach(remaining::remove);
        }

        boolean changed;
        do {
            changed = false;
            for (String graphId : new TreeSet<>(graphs.keySet())) {
                if (rejected.contains(graphId)) continue;
                CompiledGraph graph = graphs.get(graphId);
                Set<String> dependencies = graph instanceof CompiledGraphDependencies manifest
                        && manifest.requiresAvailableDependencies()
                        ? manifest.graphDependencies() : Set.of();
                if (dependencies.stream().anyMatch(rejected::contains)) {
                    rejected.add(graphId);
                    changed = true;
                }
            }
        } while (changed);
        return java.util.Collections.unmodifiableSet(new LinkedHashSet<>(rejected));
    }

    private static Set<String> dependenciesOf(CompiledGraph graph) {
        Set<String> dependencies = graph instanceof CompiledGraphDependencies manifest
                && manifest.requiresAvailableDependencies()
                ? manifest.graphDependencies() : Set.of();
        return new TreeSet<>(dependencies);
    }

    private record TraversalFrame(String graphId, Iterator<String> dependencies) {
    }

    private enum VisitState { ACTIVE, DONE }
}
