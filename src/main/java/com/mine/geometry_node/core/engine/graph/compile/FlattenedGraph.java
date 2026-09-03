package com.mine.geometry_node.core.engine.graph.compile;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Detached graph-family-neutral snapshot produced after node-group expansion. */
public final class FlattenedGraph {
    private final Map<String, CanonicalNodeSchema> nodeSchemas;
    private final Map<String, Map<String, TargetConnection>> executionOutputs;
    private final Map<InputKey, DataConnectionSource> dataInputs;
    private final Map<String, List<String>> nodesByType;
    private final Map<String, Map<String, Object>> staticInputs;
    private final Set<String> portNames;

    FlattenedGraph(Map<String, CanonicalNodeSchema> nodeSchemas,
                   Map<String, Map<String, TargetConnection>> executionOutputs,
                   Map<InputKey, DataConnectionSource> dataInputs,
                   Map<String, List<String>> nodesByType,
                   Map<String, Map<String, Object>> staticInputs,
                   Set<String> portNames) {
        this.nodeSchemas = Map.copyOf(nodeSchemas);
        this.executionOutputs = copyNested(executionOutputs);
        this.dataInputs = Map.copyOf(dataInputs);
        this.nodesByType = copyLists(nodesByType);
        this.staticInputs = copyNested(staticInputs);
        this.portNames = Set.copyOf(portNames);
    }

    public Map<String, CanonicalNodeSchema> nodeSchemas() { return nodeSchemas; }
    public Map<String, Map<String, TargetConnection>> executionOutputs() { return executionOutputs; }
    public Map<InputKey, DataConnectionSource> dataInputs() { return dataInputs; }
    public Map<String, List<String>> nodesByType() { return nodesByType; }
    public Map<String, Map<String, Object>> staticInputs() { return staticInputs; }
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

    public record TargetConnection(String targetNodeId, String targetPortName) {
    }

    public record DataConnectionSource(String sourceNodeId, String sourcePortName) {
    }

    public record InputKey(String nodeId, String portName) {
    }
}
