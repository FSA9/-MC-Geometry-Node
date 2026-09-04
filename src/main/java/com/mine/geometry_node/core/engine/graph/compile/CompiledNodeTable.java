package com.mine.geometry_node.core.engine.graph.compile;

import com.mine.geometry_node.core.engine.graph.compile.artifact.CompiledDataIndex;
import com.mine.geometry_node.core.engine.graph.compile.artifact.CompiledNodeIndex;
import com.mine.geometry_node.core.node.definition.port.PortDef;
import com.mine.geometry_node.core.node.definition.port.PortType;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Compiler-facing, graph-family-neutral node table. It compacts the canonical
 * flattened schemas and connections into the representation shared by runtime plans.
 */
public final class CompiledNodeTable {
    private final List<String> nodeIds;
    private final Map<String, NodeDescriptor> descriptors;
    private final CompiledNodeIndex index;

    private CompiledNodeTable(List<String> nodeIds,
                              Map<String, NodeDescriptor> descriptors,
                              CompiledNodeIndex index) {
        this.nodeIds = List.copyOf(nodeIds);
        this.descriptors = Map.copyOf(descriptors);
        this.index = index;
    }

    public static CompiledNodeTable build(FlattenedGraph flattened) {
        List<String> nodeIds = flattened.nodeSchemas().keySet().stream().sorted().toList();
        int size = nodeIds.size();
        Map<String, NodeDescriptor> descriptors = new LinkedHashMap<>();
        String[] ids = new String[size];
        String[] types = new String[size];
        @SuppressWarnings("unchecked") Map<String, Object>[] staticInputs = new Map[size];
        @SuppressWarnings("unchecked") Set<String>[] ports = new Set[size];
        @SuppressWarnings("unchecked") Set<String>[] dataPassthroughOutputs = new Set[size];

        for (int nodeIndex = 0; nodeIndex < size; nodeIndex++) {
            String nodeId = nodeIds.get(nodeIndex);
            CanonicalNodeSchema schema = flattened.nodeSchemas().get(nodeId);
            Map<String, Object> flattenedInputs =
                    flattened.staticInputs().getOrDefault(nodeId, Map.of());
            ids[nodeIndex] = nodeId;
            types[nodeIndex] = schema.typeId();
            staticInputs[nodeIndex] = flattenedInputs;
            ports[nodeIndex] = schema.portIds();
            dataPassthroughOutputs[nodeIndex] = schema.dataPassthroughOutputs();
            descriptors.put(nodeId, new NodeDescriptor(nodeId, nodeIndex, schema.typeId(),
                    schema.inputs(), schema.outputs()));
        }

        Map<String, Integer> portKeys = buildPortKeys(ports, flattened.portNames());
        @SuppressWarnings("unchecked")
        Map<Integer, CompiledDataIndex.DataConnectionSource>[] dataInputs = new Map[size];
        for (int index = 0; index < size; index++) dataInputs[index] = new LinkedHashMap<>();
        for (Map.Entry<FlattenedGraph.InputKey, FlattenedGraph.DataConnectionSource> entry
                : flattened.dataInputs().entrySet()) {
            NodeDescriptor target = descriptors.get(entry.getKey().nodeId());
            NodeDescriptor source = descriptors.get(entry.getValue().sourceNodeId());
            if (source == null || target == null) continue;
            Integer portKey = portKeys.get(entry.getKey().portName());
            if (portKey == null) continue;
            PortDef sourcePort = source.outputs().get(entry.getValue().sourcePortName());
            PortDef targetPort = target.inputs().get(entry.getKey().portName());
            dataInputs[target.index()].putIfAbsent(portKey,
                    new CompiledDataIndex.DataConnectionSource(
                            source.index(), entry.getValue().sourcePortName(),
                            portType(sourcePort), portType(targetPort)));
        }

        return new CompiledNodeTable(nodeIds, descriptors,
                new CompiledNodeIndex(ids, types, staticInputs, dataInputs, ports,
                        dataPassthroughOutputs, portKeys));
    }

    public List<String> nodeIds() {
        return nodeIds;
    }

    @Nullable
    public NodeDescriptor descriptor(String nodeId) {
        return descriptors.get(nodeId);
    }

    public CompiledNodeIndex index() {
        return index;
    }

    private static Map<String, Integer> buildPortKeys(Set<String>[] ports,
                                                      Set<String> flattenedNames) {
        Set<String> names = new TreeSet<>(flattenedNames);
        for (Set<String> nodePorts : ports) names.addAll(nodePorts);
        Map<String, Integer> keys = new LinkedHashMap<>();
        for (String name : names) keys.put(name, keys.size());
        return Map.copyOf(keys);
    }

    private static PortType portType(@Nullable PortDef port) {
        return port != null && port.type() != null ? port.type() : PortType.ANY;
    }

    public record NodeDescriptor(String id, int index, String type,
                                 Map<String, PortDef> inputs,
                                 Map<String, PortDef> outputs) {
        public NodeDescriptor {
            inputs = Collections.unmodifiableMap(new LinkedHashMap<>(inputs));
            outputs = Collections.unmodifiableMap(new LinkedHashMap<>(outputs));
        }
    }
}
