package com.mine.geometry_node.core.engine.graph.compile;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.Gson;
import com.mine.geometry_node.core.node.NodeRegistry;
import com.mine.geometry_node.core.engine.graph.compile.FlattenedGraph.DataConnectionSource;
import com.mine.geometry_node.core.engine.graph.compile.FlattenedGraph.InputKey;
import com.mine.geometry_node.core.engine.graph.compile.FlattenedGraph.TargetConnection;
import com.mine.geometry_node.core.node.group.GroupNodeTypes;
import com.mine.geometry_node.core.node.value.GraphNumberNormalizer;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.port.PortType;
import com.mine.geometry_node.core.node.definition.port.TypeConverter;
import com.mine.geometry_node.core.node.reroute.RerouteNodeSupport;

import java.util.*;

/** Expands nested node groups into a graph-family-neutral compiler input. */
public final class GraphFlattener {
    private static final Gson GSON = new Gson();

    private final Map<String, JsonObject> nodeDataLookup = new HashMap<>();
    private final Map<String, NodeData> nodeInstanceLookup = new HashMap<>();
    private final Map<String, CanonicalNodeSchema> nodeSchemaLookup = new HashMap<>();
    private final Map<String, Map<String, TargetConnection>> flowOutputLookup = new HashMap<>();
    private final Map<InputKey, DataConnectionSource> inputLookup = new HashMap<>();
    private final Map<String, List<String>> typeLookup = new HashMap<>();
    private final Map<String, Map<String, Object>> authoredStaticInputLookup = new HashMap<>();
    private final Map<String, Map<String, Object>> bridgedStaticInputLookup = new HashMap<>();
    private final Map<String, Map<String, Object>> staticInputLookup = new HashMap<>();
    private final Map<String, GroupBoundary> groupBoundaries = new HashMap<>();
    private final Map<String, String> boundaryToGroupMap = new HashMap<>();
    private final Set<String> virtualNodeIds = new HashSet<>();
    private final Map<InputKey, DataResolution> dataResolutionCache = new HashMap<>();
    private final Map<InputKey, Optional<TargetConnection>> executionTargetCache = new HashMap<>();

    private GraphFlattener() {
    }

    public static FlattenedGraph flatten(JsonObject rootNodes) {
        GraphFlattener flattener = new GraphFlattener();
        flattener.flattenInternal(rootNodes);
        return flattener.snapshot();
    }

    private void flattenInternal(JsonObject rootNodes) {
        if (rootNodes == null) return;

        flattenRecursive("", rootNodes);
        Map<InputKey, DataConnectionSource> unbridgedInputs = new HashMap<>(inputLookup);

        bridgeDataInputs();
        finalizeNodeSchemas();

        resetBridgedStaticInputs();
        inputLookup.clear();
        inputLookup.putAll(unbridgedInputs);
        dataResolutionCache.clear();
        bridgeDataInputs();
        finalizeNodeSchemas();

        bridgeExecutionOutputs();
        removeVirtualNodes();
    }

    private void flattenRecursive(String prefix, JsonObject nodesMap) {
        if (nodesMap == null) return;

        for (String localId : nodesMap.keySet()) {
            JsonObject nodeObj = asObject(nodesMap.get(localId));
            if (nodeObj == null) continue;

            String globalId = prefix + localId;
            String type = NodeDef.canonicalTypeId(readString(nodeObj, "node_type", "unknown"));

            nodeDataLookup.put(globalId, nodeObj);
            typeLookup.computeIfAbsent(type, ignored -> new ArrayList<>()).add(globalId);
            NodeData instanceData = GSON.fromJson(nodeObj, NodeData.class);
            instanceData.id = globalId;
            instanceData.restoreDocumentDefaults();
            nodeInstanceLookup.put(globalId, instanceData);
            NodeDef instanceDefinition = NodeRegistry.INSTANCE.resolveDefinition(instanceData);
            CanonicalNodeSchema schema = CanonicalNodeSchema.from(type, instanceDefinition);
            nodeSchemaLookup.put(globalId, schema);

            if (isVirtualType(type)) {
                virtualNodeIds.add(globalId);
            }

            if (GroupNodeTypes.NODE_GROUP.equals(type) && nodeObj.has("sub_nodes")) {
                virtualNodeIds.add(globalId);
                GroupBoundary boundary = parseGroupBoundary(globalId, nodeObj);
                groupBoundaries.put(globalId, boundary);
                if (boundary.groupInId != null) boundaryToGroupMap.put(boundary.groupInId, globalId);
                if (boundary.groupOutId != null) boundaryToGroupMap.put(boundary.groupOutId, globalId);
            }

            parseStaticInputs(globalId, schema, nodeObj);
            parseExecutionOutputs(globalId, prefix, nodeObj);
            parseDataOutputs(globalId, prefix, nodeObj);

            if (GroupNodeTypes.NODE_GROUP.equals(type) && nodeObj.has("sub_nodes")) {
                flattenRecursive(globalId + "/", asObject(nodeObj.get("sub_nodes")));
            }
        }
    }

    private void parseStaticInputs(String globalId, CanonicalNodeSchema schema, JsonObject nodeObj) {
        Map<String, Object> overrides = new HashMap<>();
        addPortConfigInputDefaults(nodeObj, overrides);

        JsonObject inputs = asObject(nodeObj.get("inputs"));
        if (inputs != null) {
            overrides.putAll(parseValueMap(inputs));
        }

        authoredStaticInputLookup.put(globalId, overrides);
        Map<String, Object> bakedInputs = new HashMap<>(overrides);
        canonicalizeStaticInputs(bakedInputs, schema);

        staticInputLookup.put(globalId, bakedInputs);
    }

    private static void canonicalizeStaticInputs(
            Map<String, Object> inputs, CanonicalNodeSchema schema) {
        for (var input : schema.inputs().values()) {
            if (input.type().isFlow()
                    || inputs.get(input.id()) == ExplicitNullInput.INSTANCE) {
                continue;
            }
            Object value = inputs.containsKey(input.id())
                    ? inputs.get(input.id()) : input.defaultValue();
            if (value == null) continue;
            Object converted = TypeConverter.convertForPort(value, input.type());
            inputs.put(input.id(), converted != null ? converted : value);
        }
    }

    /**
     * Publishes the final schema after group inputs have been bridged. Dynamic definitions may
     * depend on selector inputs, so the preliminary schema used to flatten connections is not
     * necessarily the schema that runtime compilation must consume.
     */
    private void finalizeNodeSchemas() {
        for (Map.Entry<String, NodeData> entry : nodeInstanceLookup.entrySet()) {
            String nodeId = entry.getKey();
            NodeData instance = entry.getValue();
            Map<String, Object> staticInputs = staticInputLookup.getOrDefault(nodeId, Map.of());
            Map<String, Object> definitionInputs = new LinkedHashMap<>();
            staticInputs.forEach((port, value) -> {
                if (value != ExplicitNullInput.INSTANCE) definitionInputs.put(port, value);
            });
            instance.inputs = definitionInputs;

            NodeDef definition = NodeRegistry.INSTANCE.resolveDefinition(instance);
            CanonicalNodeSchema schema = CanonicalNodeSchema.from(instance.type, definition);
            nodeSchemaLookup.put(nodeId, schema);

            Map<String, Object> canonicalInputs = new HashMap<>(
                    authoredStaticInputLookup.getOrDefault(nodeId, Map.of()));
            canonicalInputs.putAll(bridgedStaticInputLookup.getOrDefault(nodeId, Map.of()));
            canonicalizeStaticInputs(canonicalInputs, schema);
            staticInputLookup.put(nodeId, canonicalInputs);
        }
    }

    private void resetBridgedStaticInputs() {
        bridgedStaticInputLookup.clear();
        for (Map.Entry<String, CanonicalNodeSchema> entry : nodeSchemaLookup.entrySet()) {
            Map<String, Object> inputs = new HashMap<>(
                    authoredStaticInputLookup.getOrDefault(entry.getKey(), Map.of()));
            canonicalizeStaticInputs(inputs, entry.getValue());
            staticInputLookup.put(entry.getKey(), inputs);
        }
    }

    private void addPortConfigInputDefaults(JsonObject nodeObj, Map<String, Object> bakedInputs) {
        JsonObject portConfig = asObject(nodeObj.get("port_config"));
        JsonObject inputPorts = portConfig != null ? asObject(portConfig.get(GroupNodeTypes.CATEGORY_INPUTS)) : null;
        if (inputPorts == null) return;

        for (String portId : inputPorts.keySet()) {
            if (bakedInputs.containsKey(portId)) continue;

            JsonObject config = asObject(inputPorts.get(portId));
            PortType type = readPortType(config);
            Object defaultValue = type != null ? type.getDefaultValue() : null;
            if (defaultValue != null) {
                bakedInputs.put(portId, defaultValue);
            }
        }
    }

    private void parseExecutionOutputs(String globalId, String prefix, JsonObject nodeObj) {
        String execKey = nodeObj.has("exec_outputs") ? "exec_outputs" : (nodeObj.has("execution") ? "execution" : null);
        JsonObject execObj = execKey != null ? asObject(nodeObj.get(execKey)) : null;
        if (execObj == null) return;

        Map<String, TargetConnection> flowMap = new HashMap<>();
        for (String port : execObj.keySet()) {

            TargetConnection target = parseTarget(prefix, execObj.get(port), "flow_in");
            if (target != null) {
                flowMap.put(port, target);
            }
        }
        flowOutputLookup.put(globalId, flowMap);
    }

    private void parseDataOutputs(String globalId, String prefix, JsonObject nodeObj) {
        JsonObject outObj = asObject(nodeObj.get("outputs"));
        if (outObj == null) return;

        for (String sourcePort : outObj.keySet()) {

            JsonArray targets = asArray(outObj.get(sourcePort));
            if (targets == null) continue;

            for (JsonElement targetElement : targets) {
                TargetConnection target = parseTarget(prefix, targetElement, null);
                if (target == null || target.targetPortName() == null || target.targetPortName().isBlank()) continue;

                inputLookup.put(
                        new InputKey(target.targetNodeId(), target.targetPortName()),
                        new DataConnectionSource(globalId, sourcePort)
                );
            }
        }
    }

    private void bridgeDataInputs() {
        Map<InputKey, DataConnectionSource> finalInputLookup = new HashMap<>();

        for (Map.Entry<InputKey, DataConnectionSource> entry : inputLookup.entrySet()) {
            InputKey target = entry.getKey();
            if (isVirtualNode(target.nodeId())) continue;

            DataResolution resolved = resolveDataSource(entry.getValue(), new HashSet<>());
            if (resolved.source != null) {
                finalInputLookup.put(entry.getKey(), resolved.source);
            } else if (resolved.hasStaticValue) {
                setStaticInput(target.nodeId(), target.portName(), resolved.staticValue);
            }
        }

        inputLookup.clear();
        inputLookup.putAll(finalInputLookup);
    }

    private void bridgeExecutionOutputs() {
        Map<String, Map<String, TargetConnection>> finalFlowLookup = new HashMap<>();

        for (Map.Entry<String, Map<String, TargetConnection>> sourceEntry : flowOutputLookup.entrySet()) {
            String sourceId = sourceEntry.getKey();
            if (isVirtualNode(sourceId)) continue;

            Map<String, TargetConnection> rewrittenOutputs = new HashMap<>();
            for (Map.Entry<String, TargetConnection> outputEntry : sourceEntry.getValue().entrySet()) {
                TargetConnection initialTarget = outputEntry.getValue();
                TargetConnection resolvedTarget = resolveExecutionTarget(
                        initialTarget.targetNodeId(),
                        initialTarget.targetPortName(),
                        new HashSet<>()
                );
                if (resolvedTarget != null && !isVirtualNode(resolvedTarget.targetNodeId())) {
                    rewrittenOutputs.put(outputEntry.getKey(), resolvedTarget);
                }
            }

            if (!rewrittenOutputs.isEmpty()) {
                finalFlowLookup.put(sourceId, rewrittenOutputs);
            }
        }

        flowOutputLookup.clear();
        flowOutputLookup.putAll(finalFlowLookup);
    }

    private DataResolution resolveDataSource(DataConnectionSource currentSource, Set<InputKey> visited) {
        if (currentSource == null) return DataResolution.empty();

        String nodeId = currentSource.sourceNodeId();
        String port = currentSource.sourcePortName();
        InputKey cacheKey = new InputKey(nodeId, port);
        DataResolution cached = dataResolutionCache.get(cacheKey);
        if (cached != null) return cached;

        if (!visited.add(cacheKey)) {
            return DataResolution.staticValue(null);
        }

        DataResolution resolved;
        CanonicalNodeSchema schema = nodeSchemaLookup.get(nodeId);
        String passthroughInput = schema != null && schema.isDataPassthroughOutput(port)
                ? port : null;
        GroupBoundary groupBoundary = groupBoundaries.get(nodeId);
        if (passthroughInput != null) {
            DataConnectionSource provider = inputLookup.get(
                    new InputKey(nodeId, passthroughInput));
            resolved = provider != null
                    ? resolveDataSource(provider, visited)
                    : resolvePassthroughInputDefault(nodeId, passthroughInput);
        } else if (groupBoundary != null) {
            if (groupBoundary.groupOutId == null) {
                resolved = DataResolution.empty();
            } else {
                DataConnectionSource internalProvider = inputLookup.get(
                        new InputKey(groupBoundary.groupOutId, port));
                resolved = internalProvider != null
                        ? resolveDataSource(internalProvider, visited)
                        : DataResolution.empty();
            }

        } else if (boundaryToGroupMap.containsKey(nodeId)) {
            String ownerGroupId = boundaryToGroupMap.get(nodeId);
            GroupBoundary ownerBoundary = groupBoundaries.get(ownerGroupId);
            if (ownerBoundary != null && nodeId.equals(ownerBoundary.groupInId)) {
                DataConnectionSource externalProvider = inputLookup.get(new InputKey(ownerGroupId, port));
                resolved = externalProvider != null
                        ? resolveDataSource(externalProvider, visited)
                        : resolveGroupInputDefault(ownerGroupId, port);
            } else {
                resolved = DataResolution.empty();
            }

        } else if (isRerouteNode(nodeId)) {
            DataConnectionSource rerouteProvider = inputLookup.get(
                    new InputKey(nodeId, RerouteNodeSupport.INPUT_PORT));
            resolved = rerouteProvider != null
                    ? resolveDataSource(rerouteProvider, visited)
                    : DataResolution.staticValue(null);

        } else if (isVirtualNode(nodeId)) {
            resolved = DataResolution.empty();
        } else {
            resolved = DataResolution.source(currentSource);
        }

        visited.remove(cacheKey);
        dataResolutionCache.put(cacheKey, resolved);
        return resolved;
    }

    private TargetConnection resolveExecutionTarget(String targetId, String targetPort, Set<InputKey> visited) {
        if (targetId == null || targetPort == null) return null;
        InputKey cacheKey = new InputKey(targetId, targetPort);
        Optional<TargetConnection> cached = executionTargetCache.get(cacheKey);
        if (cached != null) return cached.orElse(null);

        if (!visited.add(cacheKey)) return null;

        TargetConnection resolved;
        GroupBoundary groupBoundary = groupBoundaries.get(targetId);
        if (groupBoundary != null) {
            if (groupBoundary.groupInId == null) {
                resolved = null;
            } else {
                TargetConnection nextHop = getFlowTarget(groupBoundary.groupInId, targetPort);
                resolved = nextHop != null
                        ? resolveExecutionTarget(nextHop.targetNodeId(), nextHop.targetPortName(), visited)
                        : null;
            }

        } else if (boundaryToGroupMap.containsKey(targetId)) {
            String ownerGroupId = boundaryToGroupMap.get(targetId);
            GroupBoundary ownerBoundary = groupBoundaries.get(ownerGroupId);
            if (ownerBoundary != null && targetId.equals(ownerBoundary.groupOutId)) {
                TargetConnection nextHop = getFlowTarget(ownerGroupId, targetPort);
                resolved = nextHop != null
                        ? resolveExecutionTarget(nextHop.targetNodeId(), nextHop.targetPortName(), visited)
                        : null;
            } else {
                resolved = null;
            }

        } else if (isRerouteNode(targetId)) {
            TargetConnection nextHop = getFlowTarget(targetId, RerouteNodeSupport.OUTPUT_PORT);
            resolved = nextHop != null
                    ? resolveExecutionTarget(nextHop.targetNodeId(), nextHop.targetPortName(), visited)
                    : null;

        } else if (isVirtualNode(targetId)) {
            resolved = null;
        } else {
            resolved = new TargetConnection(targetId, targetPort);
        }

        visited.remove(cacheKey);
        executionTargetCache.put(cacheKey, Optional.ofNullable(resolved));
        return resolved;
    }

    private DataResolution resolveGroupInputDefault(String groupId, String port) {
        Map<String, Object> groupInputs = staticInputLookup.get(groupId);
        if (groupInputs != null && groupInputs.containsKey(port)) {
            return DataResolution.staticValue(groupInputs.get(port));
        }
        CanonicalNodeSchema schema = nodeSchemaLookup.get(groupId);
        return schema != null && schema.portIds().contains(port)
                ? DataResolution.staticValue(null)
                : DataResolution.empty();
    }

    private DataResolution resolvePassthroughInputDefault(String nodeId, String port) {
        Map<String, Object> inputs = staticInputLookup.get(nodeId);
        return DataResolution.staticValue(inputs != null ? inputs.get(port) : null);
    }

    private TargetConnection getFlowTarget(String sourceId, String port) {
        Map<String, TargetConnection> flows = flowOutputLookup.get(sourceId);
        if (flows == null) return null;

        TargetConnection exact = flows.get(port);
        if (exact != null) return exact;

        return flows.get("flow_out");
    }

    private void setStaticInput(String nodeId, String portName, Object value) {
        Object canonicalValue = value;
        CanonicalNodeSchema schema = nodeSchemaLookup.get(nodeId);
        if (value != null && schema != null) {
            var input = schema.inputs().get(portName);
            if (input != null && !input.type().isFlow()) {
                Object converted = TypeConverter.convertForPort(value, input.type());
                if (converted != null) canonicalValue = converted;
            }
        }
        Object storedValue = canonicalValue != null ? canonicalValue : ExplicitNullInput.INSTANCE;
        bridgedStaticInputLookup.computeIfAbsent(nodeId, ignored -> new HashMap<>())
                .put(portName, storedValue);
        staticInputLookup.computeIfAbsent(nodeId, ignored -> new HashMap<>())
                .put(portName, storedValue);
    }

    private void removeVirtualNodes() {
        if (virtualNodeIds.isEmpty()) return;

        for (String virtualId : virtualNodeIds) {
            nodeDataLookup.remove(virtualId);
            nodeInstanceLookup.remove(virtualId);
            nodeSchemaLookup.remove(virtualId);
            flowOutputLookup.remove(virtualId);
            authoredStaticInputLookup.remove(virtualId);
            bridgedStaticInputLookup.remove(virtualId);
            staticInputLookup.remove(virtualId);
        }

        for (Iterator<Map.Entry<String, List<String>>> iterator = typeLookup.entrySet().iterator(); iterator.hasNext();) {
            Map.Entry<String, List<String>> entry = iterator.next();
            entry.getValue().removeIf(virtualNodeIds::contains);
            if (entry.getValue().isEmpty()) {
                iterator.remove();
            }
        }
    }

    private GroupBoundary parseGroupBoundary(String groupId, JsonObject groupObj) {
        JsonObject subNodes = asObject(groupObj.get("sub_nodes"));
        if (subNodes == null) return new GroupBoundary(groupId, null, null);

        String inId = null;
        String outId = null;
        for (String localId : subNodes.keySet()) {
            JsonObject sub = asObject(subNodes.get(localId));
            if (sub == null) continue;

            String declaredType = readString(sub, "node_type", null);
            String type = declaredType != null ? NodeDef.canonicalTypeId(declaredType) : "";
            String globalId = groupId + "/" + localId;
            if (GroupNodeTypes.GROUP_IN_ID.equals(localId) || GroupNodeTypes.GROUP_IN.equals(type)) {
                inId = globalId;
            } else if (GroupNodeTypes.GROUP_OUT_ID.equals(localId) || GroupNodeTypes.GROUP_OUT.equals(type)) {
                outId = globalId;
            }
        }

        return new GroupBoundary(groupId, inId, outId);
    }

    private TargetConnection parseTarget(String prefix, JsonElement element, String defaultPort) {
        if (element == null || element.isJsonNull()) return null;

        if (element.isJsonObject()) {
            JsonObject target = element.getAsJsonObject();
            String targetNode = readString(target, "target_node", null);
            if (targetNode == null || targetNode.isBlank()) return null;

            String targetPort = readString(target, "target_port", defaultPort);
            return new TargetConnection(prefix + targetNode, targetPort);
        }

        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            String targetNode = element.getAsString();
            if (targetNode == null || targetNode.isBlank()) return null;
            return new TargetConnection(prefix + targetNode, defaultPort);
        }

        return null;
    }

    private static JsonObject asObject(JsonElement element) {
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static JsonArray asArray(JsonElement element) {
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
    }

    private static String readString(JsonObject obj, String key, String defaultValue) {
        if (obj == null || !obj.has(key)) return defaultValue;
        JsonElement element = obj.get(key);
        if (element == null || !element.isJsonPrimitive()) return defaultValue;
        try {
            return element.getAsString();
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private static PortType readPortType(JsonObject obj) {
        String raw = readString(obj, "type", null);
        if (raw == null || raw.isBlank()) return null;
        try {
            return PortType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static Map<String, Object> parseValueMap(JsonObject object) {
        Map<String, Object> result = new HashMap<>();
        for (String key : object.keySet()) {
            Object value = unwrapJsonElement(object.get(key));
            if (value != null) result.put(key, value);
        }
        return Map.copyOf(result);
    }

    private static Object unwrapJsonElement(JsonElement element) {
        if (element == null || element.isJsonNull()) return null;
        if (element.isJsonPrimitive()) {
            var primitive = element.getAsJsonPrimitive();
            if (primitive.isBoolean()) return primitive.getAsBoolean();
            if (primitive.isNumber()) return GraphNumberNormalizer.normalize(primitive.getAsNumber());
            if (primitive.isString()) return primitive.getAsString();
        }
        if (element.isJsonArray()) {
            List<Object> values = new ArrayList<>();
            for (JsonElement item : element.getAsJsonArray()) {
                Object value = unwrapJsonElement(item);
                values.add(value);
            }
            return Collections.unmodifiableList(values);
        }
        if (element.isJsonObject()) {
            Map<String, Object> values = new HashMap<>();
            for (String key : element.getAsJsonObject().keySet()) {
                Object value = unwrapJsonElement(element.getAsJsonObject().get(key));
                values.put(key, value);
            }
            return Collections.unmodifiableMap(values);
        }
        return null;
    }

    private boolean isVirtualNode(String nodeId) {
        return virtualNodeIds.contains(nodeId);
    }

    private static boolean isVirtualType(String type) {
        return GroupNodeTypes.NODE_GROUP.equals(type)
                || GroupNodeTypes.GROUP_IN.equals(type)
                || GroupNodeTypes.GROUP_OUT.equals(type)
                || RerouteNodeSupport.isRerouteType(type);
    }

    private boolean isRerouteNode(String nodeId) {
        JsonObject nodeObj = nodeDataLookup.get(nodeId);
        return nodeObj != null && RerouteNodeSupport.isRerouteType(readString(nodeObj, "node_type", null));
    }

    private FlattenedGraph snapshot() {
        return new FlattenedGraph(nodeSchemaLookup, flowOutputLookup,
                inputLookup, typeLookup, staticInputLookup,
                collectFinalPortNames());
    }

    private Set<String> collectFinalPortNames() {
        Set<String> names = new HashSet<>();
        nodeSchemaLookup.values().forEach(schema -> names.addAll(schema.portIds()));
        staticInputLookup.values().forEach(inputs -> names.addAll(inputs.keySet()));
        flowOutputLookup.values().forEach(outputs -> outputs.forEach((sourcePort, target) -> {
            names.add(sourcePort);
            if (target.targetPortName() != null) names.add(target.targetPortName());
        }));
        inputLookup.forEach((target, source) -> {
            names.add(target.portName());
            names.add(source.sourcePortName());
        });
        return names;
    }

    private record GroupBoundary(String groupId, String groupInId, String groupOutId) {}

    private record DataResolution(
            DataConnectionSource source,
            Object staticValue,
            boolean hasStaticValue
    ) {
        static DataResolution source(DataConnectionSource source) {
            return new DataResolution(source, null, false);
        }

        static DataResolution staticValue(Object value) {
            return new DataResolution(null, value, true);
        }

        static DataResolution empty() {
            return new DataResolution(null, null, false);
        }
    }
}
