package com.mine.geometry_node.core.node.reroute;

import com.mine.geometry_node.core.node.document.Connection;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.NodeRegistry;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.nodes.special.RerouteNode;
import com.mine.geometry_node.core.node.definition.port.PortDef;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.PortType;

import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

public final class RerouteNodeSupport {
    public static final String INPUT_PORT = "in";
    public static final String OUTPUT_PORT = "out";

    private RerouteNodeSupport() {
    }

    public static boolean isReroute(NodeData node) {
        return node != null && isRerouteType(node.type);
    }

    public static boolean isRerouteType(String type) {
        return type != null && NodeDef.canonicalTypeId(RerouteNode.TYPE_ID).equals(type);
    }

    public static PortType resolveLockedType(NodeData node) {
        if (!isReroute(node)) return PortType.ANY;

        PortType inputType = readConfiguredType(node, true);
        if (inputType != null) return inputType;

        PortType outputType = readConfiguredType(node, false);
        return outputType != null ? outputType : PortType.ANY;
    }

    public static PortType resolvePortType(Map<String, NodeData> scopeNodes, NodeData node, String portId, boolean inputSide) {
        return resolvePortType(scopeNodes, node, portId, inputSide, new HashSet<>());
    }

    private static PortType resolvePortType(Map<String, NodeData> scopeNodes,
                                            NodeData node,
                                            String portId,
                                            boolean inputSide,
                                            Set<String> visited) {
        if (node == null || portId == null) return null;
        if (isReroute(node)) {
            return resolveRerouteType(scopeNodes, node, visited);
        }

        NodeDef def = NodeRegistry.INSTANCE.resolveDefinition(node);
        if (def == null) return null;

        for (PortRow row : def.rows()) {
            PortDef port = inputSide ? row.leftPort() : row.rightPort();
            if (port != null && port.id().equals(portId)) {
                return port.type();
            }
        }
        return null;
    }

    public static PortType resolveRerouteType(Map<String, NodeData> scopeNodes, NodeData node) {
        return resolveRerouteType(scopeNodes, node, new HashSet<>());
    }

    private static PortType resolveRerouteType(Map<String, NodeData> scopeNodes, NodeData node, Set<String> visited) {
        if (!isReroute(node)) return null;
        String key = node.id != null ? node.id : Integer.toHexString(System.identityHashCode(node));
        if (!visited.add(key)) {
            return PortType.ANY;
        }

        PortType fromInput = inferFromInputConnection(scopeNodes, node, visited);
        if (fromInput != null && fromInput != PortType.ANY) {
            visited.remove(key);
            return fromInput;
        }

        PortType fromOutput = inferFromOutputConnection(scopeNodes, node, visited);
        if (fromOutput != null) {
            visited.remove(key);
            return fromOutput;
        }

        visited.remove(key);
        return fromInput != null ? fromInput : resolveLockedType(node);
    }

    public static boolean refreshLockedType(Map<String, NodeData> scopeNodes, NodeData node) {
        if (!isReroute(node)) return false;
        PortType previous = resolveLockedType(node);

        PortType inferred = inferFromInputConnection(scopeNodes, node, new HashSet<>());
        if (inferred == null || inferred == PortType.ANY) {
            PortType fromOutput = inferFromOutputConnection(scopeNodes, node, new HashSet<>());
            if (fromOutput != null && fromOutput != PortType.ANY) {
                inferred = fromOutput;
            }
        }

        PortType next = inferred != null ? inferred : PortType.ANY;
        setLockedType(node, next);
        return previous != next;
    }

    public static List<NodeData> refreshLockedTypes(Map<String, NodeData> scopeNodes) {
        if (scopeNodes == null) return List.of();
        java.util.LinkedHashSet<NodeData> changed = new java.util.LinkedHashSet<>();
        int maxPasses = Math.max(1, scopeNodes.size() + 1);
        for (int pass = 0; pass < maxPasses; pass++) {
            boolean passChanged = false;
            for (NodeData node : scopeNodes.values()) {
                if (refreshLockedType(scopeNodes, node)) {
                    changed.add(node);
                    passChanged = true;
                }
            }
            if (!passChanged) break;
        }
        return List.copyOf(changed);
    }

    private static PortType inferFromInputConnection(Map<String, NodeData> scopeNodes, NodeData reroute, Set<String> visited) {
        if (scopeNodes == null || reroute == null) return null;
        if (reroute.id == null) return null;

        for (NodeData node : scopeNodes.values()) {
            if (node == null) continue;

            if (node.outputs != null) {
                for (Map.Entry<String, List<Connection>> entry : node.outputs.entrySet()) {
                    if (entry.getValue() == null) continue;
                    for (Connection link : entry.getValue()) {
                        if (link != null && reroute.id.equals(link.targetNodeId()) && INPUT_PORT.equals(link.targetPortName())) {
                            return resolvePortType(scopeNodes, node, entry.getKey(), false, visited);
                        }
                    }
                }
            }

            if (node.execOutputs != null) {
                for (Map.Entry<String, Connection> entry : node.execOutputs.entrySet()) {
                    Connection link = entry.getValue();
                    if (link != null && reroute.id.equals(link.targetNodeId()) && INPUT_PORT.equals(link.targetPortName())) {
                        return resolvePortType(scopeNodes, node, entry.getKey(), false, visited);
                    }
                }
            }
        }
        return null;
    }

    private static PortType inferFromOutputConnection(Map<String, NodeData> scopeNodes, NodeData reroute, Set<String> visited) {
        if (scopeNodes == null || reroute == null) return null;

        if (reroute.outputs != null) {
            List<Connection> links = reroute.outputs.get(OUTPUT_PORT);
            if (links != null) {
                for (Connection link : links) {
                    if (link == null) continue;
                    NodeData target = scopeNodes.get(link.targetNodeId());
                    PortType type = resolvePortType(scopeNodes, target, link.targetPortName(), true, visited);
                    if (type != null && type != PortType.ANY) return type;
                }
            }
        }

        if (reroute.execOutputs != null) {
            Connection link = reroute.execOutputs.get(OUTPUT_PORT);
            if (link != null) {
                NodeData target = scopeNodes.get(link.targetNodeId());
                return resolvePortType(scopeNodes, target, link.targetPortName(), true, visited);
            }
        }
        return null;
    }

    private static PortType readConfiguredType(NodeData node, boolean inputSide) {
        NodeData.PortConfig config = getPortConfig(node, inputSide);
        return config != null ? config.type : null;
    }

    private static void setLockedType(NodeData node, PortType type) {
        if (node == null) return;
        PortType safeType = type != null ? type : PortType.ANY;

        NodeData.PortConfig inputConfig = ensurePortConfig(node, true);
        NodeData.PortConfig outputConfig = ensurePortConfig(node, false);
        inputConfig.type = safeType;
        outputConfig.type = safeType;
    }

    private static NodeData.PortConfig getPortConfig(NodeData node, boolean inputSide) {
        if (node == null || node.portConfig == null) return null;
        Map<String, NodeData.PortConfig> ports = inputSide ? node.portConfig.inputs : node.portConfig.outputs;
        return ports != null ? ports.get(inputSide ? INPUT_PORT : OUTPUT_PORT) : null;
    }

    private static NodeData.PortConfig ensurePortConfig(NodeData node, boolean inputSide) {
        NodeData.PortsConfig config = node.ensurePortConfig();
        Map<String, NodeData.PortConfig> ports = inputSide ? config.inputs : config.outputs;
        String portId = inputSide ? INPUT_PORT : OUTPUT_PORT;
        return ports.computeIfAbsent(portId, ignored -> new NodeData.PortConfig());
    }
}
