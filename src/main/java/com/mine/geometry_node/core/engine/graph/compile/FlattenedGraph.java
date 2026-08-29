package com.mine.geometry_node.core.engine.graph.compile;

import com.google.gson.JsonObject;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Detached graph-family-neutral snapshot produced after node-group expansion. */
public final class FlattenedGraph {
    private final Map<String, JsonObject> nodes;
    private final Map<String, Map<String, TargetConnection>> executionOutputs;
    // Behavior-tree-only control edges; blueprint execution uses executionOutputs.
    private final Map<String, Map<String, TargetConnection>> behaviorOutputs;
    private final Map<InputKey, DataConnectionSource> dataInputs;
    private final Map<String, List<String>> nodesByType;
    private final Map<String, Map<String, Object>> staticInputs;
    private final Map<String, Set<String>> ports;
    private final Set<String> portNames;

    FlattenedGraph(Map<String, JsonObject> nodes,
                   Map<String, Map<String, TargetConnection>> executionOutputs,
                   Map<String, Map<String, TargetConnection>> behaviorOutputs,
                   Map<InputKey, DataConnectionSource> dataInputs,
                   Map<String, List<String>> nodesByType,
                   Map<String, Map<String, Object>> staticInputs,
                   Map<String, Set<String>> ports,
                   Set<String> portNames) {
        Map<String, JsonObject> nodeCopies = new LinkedHashMap<>();
        nodes.forEach((id, node) -> nodeCopies.put(id, node.deepCopy()));
        this.nodes = Map.copyOf(nodeCopies);
        this.executionOutputs = copyNested(executionOutputs);
        this.behaviorOutputs = copyNested(behaviorOutputs);
        this.dataInputs = Map.copyOf(dataInputs);
        this.nodesByType = copyLists(nodesByType);
        this.staticInputs = copyNested(staticInputs);
        this.ports = copySets(ports);
        this.portNames = Set.copyOf(portNames);
    }

    public Map<String, JsonObject> nodes() { return nodes; }
    public Map<String, Map<String, TargetConnection>> executionOutputs() { return executionOutputs; }
    /** Behavior-tree-only child/control edges after group boundaries are flattened. */
    public Map<String, Map<String, TargetConnection>> behaviorOutputs() { return behaviorOutputs; }
    public Map<InputKey, DataConnectionSource> dataInputs() { return dataInputs; }
    public Map<String, List<String>> nodesByType() { return nodesByType; }
    public Map<String, Map<String, Object>> staticInputs() { return staticInputs; }
    public Map<String, Set<String>> ports() { return ports; }
    public Set<String> portNames() { return portNames; }

    private static <T> Map<String, Map<String, T>> copyNested(Map<String, Map<String, T>> source) {
        Map<String, Map<String, T>> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(key,
                Collections.unmodifiableMap(new LinkedHashMap<>(value))));
        return Map.copyOf(result);
    }

    private static Map<String, List<String>> copyLists(Map<String, List<String>> source) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(key, List.copyOf(value)));
        return Map.copyOf(result);
    }

    private static Map<String, Set<String>> copySets(Map<String, Set<String>> source) {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(key, Set.copyOf(value)));
        return Map.copyOf(result);
    }

    public record TargetConnection(String targetNodeId, String targetPortName) {
    }

    public record DataConnectionSource(String sourceNodeId, String sourcePortName) {
    }

    public record InputKey(String nodeId, String portName) {
    }
}
