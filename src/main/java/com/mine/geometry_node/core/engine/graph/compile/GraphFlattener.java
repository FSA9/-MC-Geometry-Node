package com.mine.geometry_node.core.engine.graph.compile;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.Gson;
import com.mine.geometry_node.core.node.NodeRegistry;
import com.mine.geometry_node.core.node.group.GroupNodeTypes;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.PortType;
import com.mine.geometry_node.core.node.reroute.RerouteNodeSupport;

import java.util.*;

/** Expands nested node groups into a graph-family-neutral compiler input. */
public final class GraphFlattener {
    private static final Gson GSON = new Gson();

    private final Map<String, JsonObject> nodeDataLookup = new HashMap<>();
    private final Map<String, Map<String, TargetConnection>> flowOutputLookup = new HashMap<>();
    // Behavior-tree-only edge index; kept here so group flattening has one implementation.
    private final Map<String, Map<String, TargetConnection>> behaviorOutputLookup = new HashMap<>();
    private final Map<String, DataConnectionSource> inputLookup = new HashMap<>();
    private final Map<String, List<String>> typeLookup = new HashMap<>();
    private final Map<String, Map<String, Object>> propertyLookup = new HashMap<>();
    private final Map<String, Map<String, Object>> staticInputLookup = new HashMap<>();
    private final Map<String, Set<String>> portLookup = new HashMap<>();

    private final Set<String> allStaticKeys = new HashSet<>();

    private final Map<String, GroupBoundary> groupBoundaries = new HashMap<>();
    private final Map<String, String> boundaryToGroupMap = new HashMap<>();
    private final Set<String> virtualNodeIds = new HashSet<>();
    private final Map<String, DataResolution> dataResolutionCache = new HashMap<>();
    private final Map<String, Optional<TargetConnection>> executionTargetCache = new HashMap<>();
    // Behavior-tree-only counterpart to executionTargetCache.
    private final Map<String, Optional<TargetConnection>> behaviorTargetCache = new HashMap<>();

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
        bridgeGroups();
        removeVirtualNodes();
    }

    private void flattenRecursive(String prefix, JsonObject nodesMap) {
        if (nodesMap == null) return;

        for (String localId : nodesMap.keySet()) {
            JsonObject nodeObj = asObject(nodesMap.get(localId));
            if (nodeObj == null) continue;

            String globalId = prefix + localId;
            String type = readString(nodeObj, "node_type", "unknown");

            nodeDataLookup.put(globalId, nodeObj);
            typeLookup.computeIfAbsent(type, ignored -> new ArrayList<>()).add(globalId);
            NodeData instanceData = GSON.fromJson(nodeObj, NodeData.class);
            instanceData.id = globalId;
            instanceData.restoreDocumentDefaults();
            NodeDef instanceDefinition = NodeRegistry.INSTANCE.resolveDefinition(instanceData);
            collectPorts(globalId, instanceDefinition);

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

            parseProperties(globalId, nodeObj);
            parseStaticInputs(globalId, instanceDefinition, nodeObj);
            parseExecutionOutputs(globalId, prefix, nodeObj);
            parseDataOutputs(globalId, prefix, nodeObj);
            parseBehaviorOutputs(globalId, prefix, nodeObj);

            if (GroupNodeTypes.NODE_GROUP.equals(type) && nodeObj.has("sub_nodes")) {
                flattenRecursive(globalId + "/", asObject(nodeObj.get("sub_nodes")));
            }
        }
    }

    private void parseProperties(String globalId, JsonObject nodeObj) {
        JsonObject properties = asObject(nodeObj.get("properties"));
        if (properties == null) return;

        Map<String, Object> props = parseValueMap(properties);
        propertyLookup.put(globalId, props);
        for (Object val : props.values()) {
            if (val instanceof String s) allStaticKeys.add(s);
        }
    }

    private void parseStaticInputs(String globalId, NodeDef definition, JsonObject nodeObj) {
        Map<String, Object> bakedInputs = new HashMap<>();
        if (definition != null) {
            for (PortRow row : definition.rows()) {
                if (row.leftPort() != null && !row.leftPort().type().isFlow()
                        && row.leftPort().defaultValue() != null) {
                    bakedInputs.put(row.leftPort().id(), row.leftPort().defaultValue());
                }
            }
        }

        addPortConfigInputDefaults(nodeObj, bakedInputs);

        JsonObject inputs = asObject(nodeObj.get("inputs"));
        if (inputs != null) {
            bakedInputs.putAll(parseValueMap(inputs));
        }

        staticInputLookup.put(globalId, bakedInputs);
        allStaticKeys.addAll(bakedInputs.keySet());
    }

    private void collectPorts(String globalId, NodeDef definition) {
        if (definition == null) return;
        Set<String> ports = new HashSet<>();
        for (PortRow row : definition.rows()) {
            if (row.leftPort() != null) ports.add(row.leftPort().id());
            if (row.rightPort() != null) ports.add(row.rightPort().id());
        }
        portLookup.put(globalId, Set.copyOf(ports));
        allStaticKeys.addAll(ports);
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
            allStaticKeys.add(portId);
        }
    }

    private void parseExecutionOutputs(String globalId, String prefix, JsonObject nodeObj) {
        String execKey = nodeObj.has("exec_outputs") ? "exec_outputs" : (nodeObj.has("execution") ? "execution" : null);
        JsonObject execObj = execKey != null ? asObject(nodeObj.get(execKey)) : null;
        if (execObj == null) return;

        Map<String, TargetConnection> flowMap = new HashMap<>();
        for (String port : execObj.keySet()) {
            allStaticKeys.add(port);

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
            allStaticKeys.add(sourcePort);

            JsonArray targets = asArray(outObj.get(sourcePort));
            if (targets == null) continue;

            for (JsonElement targetElement : targets) {
                TargetConnection target = parseTarget(prefix, targetElement, null);
                if (target == null || target.targetPortName == null || target.targetPortName.isBlank()) continue;

                allStaticKeys.add(target.targetPortName);
                inputLookup.put(
                        makeKey(target.targetNodeId, target.targetPortName),
                        new DataConnectionSource(globalId, sourcePort)
                );
            }
        }
    }

    /** Reads behavior-tree child/control edges from the editable graph document. */
    private void parseBehaviorOutputs(String globalId, String prefix, JsonObject nodeObj) {
        JsonObject outputs = asObject(nodeObj.get("behavior_outputs"));
        if (outputs == null) return;

        Map<String, TargetConnection> connections = new LinkedHashMap<>();
        for (String port : outputs.keySet()) {
            allStaticKeys.add(port);
            TargetConnection target = parseTarget(prefix, outputs.get(port), null);
            if (target != null) connections.put(port, target);
        }
        behaviorOutputLookup.put(globalId, connections);
    }

    private void bridgeGroups() {
        bridgeDataInputs();
        bridgeExecutionOutputs();
        bridgeBehaviorOutputs();
    }

    private void bridgeDataInputs() {
        Map<String, DataConnectionSource> finalInputLookup = new HashMap<>();

        for (Map.Entry<String, DataConnectionSource> entry : inputLookup.entrySet()) {
            NodePortKey target = parseKey(entry.getKey());
            if (target == null || isVirtualNode(target.nodeId)) continue;

            DataResolution resolved = resolveDataSource(entry.getValue(), new HashSet<>());
            if (resolved.source != null) {
                finalInputLookup.put(entry.getKey(), resolved.source);
            } else if (resolved.hasStaticValue) {
                setStaticInput(target.nodeId, target.portName, resolved.staticValue);
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
                        initialTarget.targetNodeId,
                        initialTarget.targetPortName,
                        new HashSet<>()
                );
                if (resolvedTarget != null && !isVirtualNode(resolvedTarget.targetNodeId)) {
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

    /** Rewrites behavior-tree-only edges across nested group boundaries. */
    private void bridgeBehaviorOutputs() {
        Map<String, Map<String, TargetConnection>> flattened = new HashMap<>();
        for (Map.Entry<String, Map<String, TargetConnection>> sourceEntry : behaviorOutputLookup.entrySet()) {
            String sourceId = sourceEntry.getKey();
            if (isVirtualNode(sourceId)) continue;

            Map<String, TargetConnection> rewritten = new LinkedHashMap<>();
            sourceEntry.getValue().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> {
                        TargetConnection target = entry.getValue();
                        TargetConnection resolved = resolveBehaviorTarget(
                                target.targetNodeId, target.targetPortName, new HashSet<>());
                        if (resolved != null && !isVirtualNode(resolved.targetNodeId)) {
                            rewritten.put(entry.getKey(), resolved);
                        }
                    });
            if (!rewritten.isEmpty()) flattened.put(sourceId, rewritten);
        }
        behaviorOutputLookup.clear();
        behaviorOutputLookup.putAll(flattened);
    }

    private DataResolution resolveDataSource(DataConnectionSource currentSource, Set<String> visited) {
        if (currentSource == null) return DataResolution.empty();

        String nodeId = currentSource.sourceNodeId();
        String port = currentSource.sourcePortName();
        String cacheKey = makeKey(nodeId, port);
        DataResolution cached = dataResolutionCache.get(cacheKey);
        if (cached != null) return cached;

        if (!visited.add(cacheKey)) {
            return DataResolution.empty();
        }

        DataResolution resolved;
        GroupBoundary groupBoundary = groupBoundaries.get(nodeId);
        if (groupBoundary != null) {
            if (groupBoundary.groupOutId == null) {
                resolved = DataResolution.empty();
            } else {
                DataConnectionSource internalProvider = inputLookup.get(makeKey(groupBoundary.groupOutId, port));
                resolved = internalProvider != null
                        ? resolveDataSource(internalProvider, visited)
                        : DataResolution.empty();
            }

        } else if (boundaryToGroupMap.containsKey(nodeId)) {
            String ownerGroupId = boundaryToGroupMap.get(nodeId);
            GroupBoundary ownerBoundary = groupBoundaries.get(ownerGroupId);
            if (ownerBoundary != null && nodeId.equals(ownerBoundary.groupInId)) {
                DataConnectionSource externalProvider = inputLookup.get(makeKey(ownerGroupId, port));
                resolved = externalProvider != null
                        ? resolveDataSource(externalProvider, visited)
                        : resolveGroupInputDefault(ownerGroupId, port);
            } else {
                resolved = DataResolution.empty();
            }

        } else if (isRerouteNode(nodeId)) {
            DataConnectionSource rerouteProvider = inputLookup.get(makeKey(nodeId, RerouteNodeSupport.INPUT_PORT));
            resolved = rerouteProvider != null
                    ? resolveDataSource(rerouteProvider, visited)
                    : DataResolution.empty();

        } else if (isVirtualNode(nodeId)) {
            resolved = DataResolution.empty();
        } else {
            resolved = DataResolution.source(currentSource);
        }

        visited.remove(cacheKey);
        dataResolutionCache.put(cacheKey, resolved);
        return resolved;
    }

    private TargetConnection resolveExecutionTarget(String targetId, String targetPort, Set<String> visited) {
        if (targetId == null || targetPort == null) return null;
        String cacheKey = makeKey(targetId, targetPort);
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
                        ? resolveExecutionTarget(nextHop.targetNodeId, nextHop.targetPortName, visited)
                        : null;
            }

        } else if (boundaryToGroupMap.containsKey(targetId)) {
            String ownerGroupId = boundaryToGroupMap.get(targetId);
            GroupBoundary ownerBoundary = groupBoundaries.get(ownerGroupId);
            if (ownerBoundary != null && targetId.equals(ownerBoundary.groupOutId)) {
                TargetConnection nextHop = getFlowTarget(ownerGroupId, targetPort);
                resolved = nextHop != null
                        ? resolveExecutionTarget(nextHop.targetNodeId, nextHop.targetPortName, visited)
                        : null;
            } else {
                resolved = null;
            }

        } else if (isRerouteNode(targetId)) {
            TargetConnection nextHop = getFlowTarget(targetId, RerouteNodeSupport.OUTPUT_PORT);
            resolved = nextHop != null
                    ? resolveExecutionTarget(nextHop.targetNodeId, nextHop.targetPortName, visited)
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

    /** Resolves one behavior-tree-only edge through virtual group nodes. */
    private TargetConnection resolveBehaviorTarget(String targetId, String targetPort, Set<String> visited) {
        if (targetId == null || targetPort == null) return null;
        String cacheKey = makeKey(targetId, targetPort);
        Optional<TargetConnection> cached = behaviorTargetCache.get(cacheKey);
        if (cached != null) return cached.orElse(null);
        if (!visited.add(cacheKey)) return null;

        TargetConnection resolved;
        GroupBoundary groupBoundary = groupBoundaries.get(targetId);
        if (groupBoundary != null) {
            TargetConnection next = groupBoundary.groupInId != null
                    ? getBehaviorTarget(groupBoundary.groupInId, targetPort) : null;
            resolved = next != null
                    ? resolveBehaviorTarget(next.targetNodeId, next.targetPortName, visited) : null;
        } else if (boundaryToGroupMap.containsKey(targetId)) {
            String ownerGroupId = boundaryToGroupMap.get(targetId);
            GroupBoundary owner = groupBoundaries.get(ownerGroupId);
            if (owner != null && targetId.equals(owner.groupOutId)) {
                TargetConnection next = getBehaviorTarget(ownerGroupId, targetPort);
                resolved = next != null
                        ? resolveBehaviorTarget(next.targetNodeId, next.targetPortName, visited) : null;
            } else {
                resolved = null;
            }
        } else if (isVirtualNode(targetId)) {
            resolved = null;
        } else {
            resolved = new TargetConnection(targetId, targetPort);
        }

        visited.remove(cacheKey);
        behaviorTargetCache.put(cacheKey, Optional.ofNullable(resolved));
        return resolved;
    }

    private DataResolution resolveGroupInputDefault(String groupId, String port) {
        Map<String, Object> groupInputs = staticInputLookup.get(groupId);
        if (groupInputs == null || !groupInputs.containsKey(port)) {
            return DataResolution.empty();
        }

        Object value = groupInputs.get(port);
        return value != null ? DataResolution.staticValue(value) : DataResolution.empty();
    }

    private TargetConnection getFlowTarget(String sourceId, String port) {
        Map<String, TargetConnection> flows = flowOutputLookup.get(sourceId);
        if (flows == null) return null;

        TargetConnection exact = flows.get(port);
        if (exact != null) return exact;

        return flows.get("flow_out");
    }

    /** Looks up an unflattened behavior-tree-only edge. */
    private TargetConnection getBehaviorTarget(String sourceId, String port) {
        Map<String, TargetConnection> connections = behaviorOutputLookup.get(sourceId);
        return connections != null ? connections.get(port) : null;
    }

    private void setStaticInput(String nodeId, String portName, Object value) {
        if (value == null) return;
        staticInputLookup.computeIfAbsent(nodeId, ignored -> new HashMap<>()).put(portName, value);
        allStaticKeys.add(portName);
    }

    private void removeVirtualNodes() {
        if (virtualNodeIds.isEmpty()) return;

        for (String virtualId : virtualNodeIds) {
            nodeDataLookup.remove(virtualId);
            flowOutputLookup.remove(virtualId);
            behaviorOutputLookup.remove(virtualId);
            propertyLookup.remove(virtualId);
            staticInputLookup.remove(virtualId);
            portLookup.remove(virtualId);
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

            String type = readString(sub, "node_type", "");
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
            if (primitive.isNumber()) return primitive.getAsNumber();
            if (primitive.isString()) return primitive.getAsString();
        }
        if (element.isJsonArray()) {
            List<Object> values = new ArrayList<>();
            for (JsonElement item : element.getAsJsonArray()) {
                Object value = unwrapJsonElement(item);
                if (value != null) values.add(value);
            }
            return List.copyOf(values);
        }
        if (element.isJsonObject()) {
            Map<String, Object> values = new HashMap<>();
            for (String key : element.getAsJsonObject().keySet()) {
                Object value = unwrapJsonElement(element.getAsJsonObject().get(key));
                if (value != null) values.put(key, value);
            }
            return Map.copyOf(values);
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

    private static String makeKey(String nodeId, String portName) {
        return nodeId + "#" + portName;
    }

    private static NodePortKey parseKey(String key) {
        int separator = key != null ? key.indexOf('#') : -1;
        if (separator <= 0 || separator >= key.length() - 1) return null;
        return new NodePortKey(key.substring(0, separator), key.substring(separator + 1));
    }

    private FlattenedGraph snapshot() {
        return new FlattenedGraph(nodeDataLookup, flowOutputLookup, behaviorOutputLookup,
                inputLookup, typeLookup, propertyLookup, staticInputLookup, portLookup,
                allStaticKeys);
    }

    public record TargetConnection(String targetNodeId, String targetPortName) {
    }

    public record DataConnectionSource(String sourceNodeId, String sourcePortName) {
    }

    private record GroupBoundary(String groupId, String groupInId, String groupOutId) {}

    private record NodePortKey(String nodeId, String portName) {}

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
