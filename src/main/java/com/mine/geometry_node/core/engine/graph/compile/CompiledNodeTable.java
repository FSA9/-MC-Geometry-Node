package com.mine.geometry_node.core.engine.graph.compile;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mine.geometry_node.core.engine.graph.compile.artifact.CompiledDataIndex;
import com.mine.geometry_node.core.engine.graph.compile.artifact.CompiledNodeIndex;
import com.mine.geometry_node.core.node.NodeCapabilities;
import com.mine.geometry_node.core.node.NodeRegistry;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.port.PortDef;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.TypeConverter;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Compiler-facing, graph-family-neutral node table. It owns the canonical
 * node/port/static-input/data-input representation shared by runtime plans.
 */
public final class CompiledNodeTable {
    private static final Gson GSON = new Gson();

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
        List<String> nodeIds = flattened.nodes().keySet().stream().sorted().toList();
        int size = nodeIds.size();
        Map<String, NodeDescriptor> descriptors = new LinkedHashMap<>();
        String[] ids = new String[size];
        String[] types = new String[size];
        @SuppressWarnings("unchecked") Map<String, Object>[] staticInputs = new Map[size];
        @SuppressWarnings("unchecked") Set<String>[] ports = new Set[size];

        for (int nodeIndex = 0; nodeIndex < size; nodeIndex++) {
            String nodeId = nodeIds.get(nodeIndex);
            Map<String, Object> flattenedInputs =
                    flattened.staticInputs().getOrDefault(nodeId, Map.of());
            NodeData node = decodeNode(nodeId, flattened.nodes().get(nodeId), flattenedInputs);
            NodeDef definition = node != null ? NodeRegistry.INSTANCE.resolveDefinition(node) : null;
            PortCatalog catalog = PortCatalog.from(definition);
            String type = node != null && node.type != null ? node.type : "unknown";
            NodeCapabilities capabilities = NodeRegistry.INSTANCE.has(type)
                    ? NodeRegistry.INSTANCE.getCapabilities(type) : null;

            ids[nodeIndex] = nodeId;
            types[nodeIndex] = type;
            staticInputs[nodeIndex] = canonicalStaticInputs(
                    flattenedInputs, catalog.inputs());
            Set<String> nodePorts = new LinkedHashSet<>();
            nodePorts.addAll(catalog.inputs().keySet());
            nodePorts.addAll(catalog.outputs().keySet());
            nodePorts.addAll(flattened.ports().getOrDefault(nodeId, Set.of()));
            ports[nodeIndex] = Set.copyOf(nodePorts);
            descriptors.put(nodeId, new NodeDescriptor(nodeId, nodeIndex, type,
                    capabilities, catalog.inputs(), catalog.outputs()));
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
            dataInputs[target.index()].putIfAbsent(portKey,
                    new CompiledDataIndex.DataConnectionSource(
                            source.index(), entry.getValue().sourcePortName()));
        }

        return new CompiledNodeTable(nodeIds, descriptors,
                new CompiledNodeIndex(ids, types, staticInputs, dataInputs, ports, portKeys));
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

    private static @Nullable NodeData decodeNode(String nodeId, @Nullable JsonObject encoded,
                                                 Map<String, Object> flattenedInputs) {
        if (encoded == null) return null;
        NodeData node = GSON.fromJson(encoded, NodeData.class);
        if (node == null) return null;
        node.id = nodeId;
        node.restoreDocumentDefaults();
        node.inputs = new LinkedHashMap<>(flattenedInputs);
        return node;
    }

    private static Map<String, Object> canonicalStaticInputs(
            Map<String, Object> flattenedInputs, Map<String, PortDef> inputPorts) {
        Map<String, Object> result = new LinkedHashMap<>(flattenedInputs);
        for (PortDef input : inputPorts.values()) {
            if (input.type().isFlow()) continue;
            Object value = flattenedInputs.containsKey(input.id())
                    ? flattenedInputs.get(input.id()) : input.defaultValue();
            if (value == null) continue;
            Object converted = TypeConverter.convertForPort(value, input.type());
            if (converted != null) {
                result.put(input.id(), converted);
            } else {
                // Invalid and world-dependent values remain available to runtime conversion.
                result.put(input.id(), value);
            }
        }
        return Map.copyOf(result);
    }

    private static Map<String, Integer> buildPortKeys(Set<String>[] ports,
                                                      Set<String> flattenedNames) {
        Set<String> names = new TreeSet<>(flattenedNames);
        for (Set<String> nodePorts : ports) names.addAll(nodePorts);
        Map<String, Integer> keys = new LinkedHashMap<>();
        for (String name : names) keys.put(name, keys.size());
        return Map.copyOf(keys);
    }

    public record NodeDescriptor(String id, int index, String type,
                                 @Nullable NodeCapabilities capabilities,
                                 Map<String, PortDef> inputs,
                                 Map<String, PortDef> outputs) {
        public NodeDescriptor {
            inputs = Collections.unmodifiableMap(new LinkedHashMap<>(inputs));
            outputs = Collections.unmodifiableMap(new LinkedHashMap<>(outputs));
        }
    }

    private record PortCatalog(Map<String, PortDef> inputs, Map<String, PortDef> outputs) {
        private static PortCatalog from(@Nullable NodeDef definition) {
            Map<String, PortDef> inputs = new LinkedHashMap<>();
            Map<String, PortDef> outputs = new LinkedHashMap<>();
            if (definition != null) {
                for (PortRow row : definition.rows()) {
                    addPort(row.leftPort(), inputs);
                    addPort(row.rightPort(), outputs);
                }
            }
            return new PortCatalog(inputs, outputs);
        }

        private static void addPort(@Nullable PortDef port, Map<String, PortDef> target) {
            if (port == null || port.id() == null || port.id().isBlank() || port.type() == null) return;
            target.putIfAbsent(port.id(), port);
        }
    }
}
