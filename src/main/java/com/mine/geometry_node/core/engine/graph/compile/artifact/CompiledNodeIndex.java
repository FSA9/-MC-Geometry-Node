package com.mine.geometry_node.core.engine.graph.compile.artifact;

import com.mine.geometry_node.core.engine.graph.value.GraphValueSnapshot;
import com.mine.geometry_node.core.node.definition.port.TypeConverter;
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
    private final Map<String, Object>[] staticInputs;
    private final Set<String>[] copiedStaticInputs;
    private final Map<Integer, DataConnectionSource>[] dataInputs;
    private final Set<String>[] ports;
    private final Map<String, Integer> portKeys;
    private final String[] portNames;

    public CompiledNodeIndex(
            String[] nodeIds,
            String[] nodeTypes,
            Map<String, Object>[] staticInputs,
            Map<Integer, DataConnectionSource>[] dataInputs,
            Set<String>[] ports,
            Map<String, Integer> portKeys) {
        if (nodeIds.length != nodeTypes.length || nodeIds.length != staticInputs.length
                || nodeIds.length != dataInputs.length || nodeIds.length != ports.length) {
            throw new IllegalArgumentException("Compiled node arrays must have the same length");
        }
        this.nodeIds = nodeIds.clone();
        this.nodeKeys = buildNodeKeys(this.nodeIds);
        this.nodeTypes = nodeTypes.clone();
        this.staticInputs = copyStaticInputArray(staticInputs);
        this.copiedStaticInputs = mutableInputKeys(this.staticInputs);
        this.dataInputs = copyIntegerMapArray(dataInputs);
        this.ports = copySetArray(ports);
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
    public int getPortKey(String portName) {
        return portKeys.getOrDefault(portName, -1);
    }

    @Nullable
    public String getPortName(int portKey) {
        return portKey >= 0 && portKey < portNames.length ? portNames[portKey] : null;
    }

    public int getPortCount() {
        return portKeys.size();
    }

    @Override
    @Nullable
    public DataConnectionSource findDataInput(int targetNodeId, String inputPortName) {
        int portKey = getPortKey(inputPortName);
        return validNode(targetNodeId) && portKey >= 0
                ? dataInputs[targetNodeId].get(portKey) : null;
    }

    @Override
    @Nullable
    public Object getStaticInput(int nodeId, String portName) {
        if (!validNode(nodeId)) return null;
        Object value = staticInputs[nodeId].get(portName);
        return copiedStaticInputs[nodeId].contains(portName)
                ? GraphValueSnapshot.snapshot(value) : value;
    }

    public <T> T getStaticInput(int nodeId, String portName, Class<T> type, T defaultValue) {
        T converted = TypeConverter.convert(getStaticInput(nodeId, portName), type, null);
        return converted != null ? converted : defaultValue;
    }

    @Override
    public boolean hasPort(int nodeId, String portName) {
        return validNode(nodeId) && ports[nodeId].contains(portName);
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

    private static Map<String, Object>[] copyStaticInputArray(Map<String, Object>[] source) {
        @SuppressWarnings("unchecked") Map<String, Object>[] result = new Map[source.length];
        for (int i = 0; i < source.length; i++) {
            if (source[i] == null || source[i].isEmpty()) {
                result[i] = Map.of();
                continue;
            }
            Map<String, Object> copy = new HashMap<>(source[i].size());
            source[i].forEach((key, value) -> copy.put(key, GraphValueSnapshot.snapshot(value)));
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

    private static Set<String>[] copySetArray(Set<String>[] source) {
        @SuppressWarnings("unchecked") Set<String>[] result = new Set[source.length];
        for (int i = 0; i < source.length; i++) {
            result[i] = source[i] != null ? Set.copyOf(source[i]) : Set.of();
        }
        return result;
    }

    private static Set<String>[] mutableInputKeys(Map<String, Object>[] inputs) {
        @SuppressWarnings("unchecked") Set<String>[] result = new Set[inputs.length];
        for (int index = 0; index < inputs.length; index++) {
            Set<String> keys = new java.util.HashSet<>();
            inputs[index].forEach((key, value) -> {
                if (GraphValueSnapshot.requiresReadCopy(value)) keys.add(key);
            });
            result[index] = Set.copyOf(keys);
        }
        return result;
    }
}
