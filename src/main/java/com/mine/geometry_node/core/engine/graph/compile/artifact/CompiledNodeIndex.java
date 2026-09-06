package com.mine.geometry_node.core.engine.graph.compile.artifact;

import com.mine.geometry_node.core.engine.graph.value.GraphValueSnapshot;
import com.mine.geometry_node.core.engine.graph.compile.ExplicitNullInput;
import com.mine.geometry_node.core.node.definition.port.TypeConverter;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Immutable graph-family-neutral index for compiled nodes, ports and data inputs.
 */
public final class CompiledNodeIndex implements CompiledDataIndex {
    private final String[] nodeIds;
    private final Map<String, Integer> nodeKeys;
    private final String[] nodeTypes;
    private final BaseNode[] nodeImplementations;
    private final Map<Integer, Object>[] staticInputs;
    private final Set<Integer>[] copiedStaticInputs;
    private final Map<Integer, DataConnectionSource>[] dataInputs;
    private final Set<Integer>[] ports;
    private final Set<Integer>[] dataPassthroughOutputs;
    private final Map<String, Integer> portKeys;
    private final String[] portNames;

    public CompiledNodeIndex(
            String[] nodeIds,
            String[] nodeTypes,
            BaseNode[] nodeImplementations,
            Map<String, Object>[] staticInputs,
            Map<Integer, DataConnectionSource>[] dataInputs,
            Set<String>[] ports,
            Set<String>[] dataPassthroughOutputs,
            Map<String, Integer> portKeys) {
        if (nodeIds.length != nodeTypes.length || nodeIds.length != nodeImplementations.length
                || nodeIds.length != staticInputs.length
                || nodeIds.length != dataInputs.length || nodeIds.length != ports.length
                || nodeIds.length != dataPassthroughOutputs.length) {
            throw new IllegalArgumentException("Compiled node arrays must have the same length");
        }
        this.nodeIds = nodeIds.clone();
        this.nodeKeys = buildNodeKeys(this.nodeIds);
        this.nodeTypes = nodeTypes.clone();
        this.nodeImplementations = nodeImplementations.clone();
        this.staticInputs = copyStaticInputArray(staticInputs, portKeys);
        this.copiedStaticInputs = mutableInputKeys(this.staticInputs);
        this.dataInputs = copyIntegerMapArray(dataInputs);
        this.ports = mapPortSets(ports, portKeys);
        this.dataPassthroughOutputs = mapPortSets(dataPassthroughOutputs, portKeys);
        this.portKeys = Map.copyOf(portKeys);
        this.portNames = buildPortNames(this.portKeys);
    }

    @Override
    public int getNodeCount() {
        return nodeIds.length;
    }

    @Override
    @Nullable
    public String getNodeId(int nodeId) {
        return validNode(nodeId) ? nodeIds[nodeId] : null;
    }

    public int getNodeKey(String nodeId) {
        return nodeKeys.getOrDefault(nodeId, -1);
    }

    @Override
    public String getNodeType(int nodeId) {
        return validNode(nodeId) ? nodeTypes[nodeId] : "";
    }

    @Override
    public @Nullable BaseNode getNodeImplementation(int nodeId) {
        return validNode(nodeId) ? nodeImplementations[nodeId] : null;
    }

    @Override
    public int getPortKey(String portName) {
        return portName != null ? portKeys.getOrDefault(portName, -1) : -1;
    }

    @Nullable
    @Override
    public String getPortName(int portKey) {
        return portKey >= 0 && portKey < portNames.length ? portNames[portKey] : null;
    }

    public int getPortCount() {
        return portKeys.size();
    }

    @Override
    @Nullable
    public DataConnectionSource findDataInput(int targetNodeId, int inputPortKey) {
        return validNode(targetNodeId) && inputPortKey >= 0
                ? dataInputs[targetNodeId].get(inputPortKey) : null;
    }

    @Override
    @Nullable
    public Object getStaticInput(int nodeId, int portKey) {
        if (!validNode(nodeId)) return null;
        Object value = staticInputs[nodeId].get(portKey);
        if (value == ExplicitNullInput.INSTANCE) return null;
        return copiedStaticInputs[nodeId].contains(portKey)
                ? GraphValueSnapshot.snapshot(value) : value;
    }

    public <T> T getStaticInput(int nodeId, String portName, Class<T> type, T defaultValue) {
        T converted = TypeConverter.convert(getStaticInput(nodeId, portName), type, null);
        return converted != null ? converted : defaultValue;
    }

    @Override
    public boolean isDataPassthroughOutput(int nodeId, int portKey) {
        return validNode(nodeId) && dataPassthroughOutputs[nodeId].contains(portKey);
    }

    @Override
    public boolean hasPort(int nodeId, int portKey) {
        return validNode(nodeId) && ports[nodeId].contains(portKey);
    }

    private boolean validNode(int nodeId) {
        return nodeId >= 0 && nodeId < nodeIds.length;
    }

    private static Map<String, Integer> buildNodeKeys(String[] nodeIds) {
        Map<String, Integer> keys = new HashMap<>(nodeIds.length);
        for (int i = 0; i < nodeIds.length; i++) keys.put(nodeIds[i], i);
        return Map.copyOf(keys);
    }

    private static String[] buildPortNames(Map<String, Integer> portKeys) {
        int length = portKeys.values().stream().mapToInt(Integer::intValue).max().orElse(-1) + 1;
        String[] names = new String[length];
        portKeys.forEach((name, key) -> {
            if (key >= 0 && key < names.length) names[key] = name;
        });
        return names;
    }

    private static Map<Integer, Object>[] copyStaticInputArray(
            Map<String, Object>[] source, Map<String, Integer> portKeys) {
        @SuppressWarnings("unchecked") Map<Integer, Object>[] result = new Map[source.length];
        for (int i = 0; i < source.length; i++) {
            if (source[i] == null || source[i].isEmpty()) {
                result[i] = Map.of();
                continue;
            }
            Map<Integer, Object> copy = new HashMap<>(source[i].size());
            source[i].forEach((name, value) -> {
                Integer key = portKeys.get(name);
                if (key != null) copy.put(key, GraphValueSnapshot.snapshot(value));
            });
            result[i] = Map.copyOf(copy);
        }
        return result;
    }

    private static <T> Map<Integer, T>[] copyIntegerMapArray(Map<Integer, T>[] source) {
        @SuppressWarnings("unchecked") Map<Integer, T>[] result = new Map[source.length];
        for (int i = 0; i < source.length; i++) {
            result[i] = source[i] != null ? Map.copyOf(source[i]) : Map.of();
        }
        return result;
    }

    private static Set<Integer>[] mapPortSets(Set<String>[] source, Map<String, Integer> portKeys) {
        @SuppressWarnings("unchecked") Set<Integer>[] result = new Set[source.length];
        for (int i = 0; i < source.length; i++) {
            if (source[i] == null || source[i].isEmpty()) {
                result[i] = Set.of();
                continue;
            }
            java.util.HashSet<Integer> keys = new java.util.HashSet<>();
            for (String name : source[i]) {
                Integer key = portKeys.get(name);
                if (key != null) keys.add(key);
            }
            result[i] = Set.copyOf(keys);
        }
        return result;
    }

    private static Set<Integer>[] mutableInputKeys(Map<Integer, Object>[] inputs) {
        @SuppressWarnings("unchecked") Set<Integer>[] result = new Set[inputs.length];
        for (int index = 0; index < inputs.length; index++) {
            Set<Integer> keys = new java.util.HashSet<>();
            inputs[index].forEach((key, value) -> {
                if (GraphValueSnapshot.requiresReadCopy(value)) keys.add(key);
            });
            result[index] = Set.copyOf(keys);
        }
        return result;
    }
}
