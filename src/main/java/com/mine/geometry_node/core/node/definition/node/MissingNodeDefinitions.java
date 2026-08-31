package com.mine.geometry_node.core.node.definition.node;

import com.mine.geometry_node.core.node.document.Connection;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.definition.port.PortDef;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.PortType;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a read-only visual definition for node types that are present in a
 * graph file but not registered by the current mod set.
 */
public final class MissingNodeDefinitions {
    private static final String COMMENT = """
            Missing node type.
            The original node data and links are kept in the graph file.
            Restore the addon that registered this node, or delete/replace it manually.""";

    private MissingNodeDefinitions() {
    }

    public static NodeDef resolve(NodeData data) {
        String typeId = data != null && data.type != null && !data.type.isBlank()
                ? data.type
                : "unknown";

        NodeDef.Builder builder = NodeDef.builder(typeId, NodeType.CUSTOM, Component.literal("Missing: " + typeId))
                .comment(COMMENT);

        List<PortDef> execInputs = collectConfiguredPorts(data, "exec_inputs", PortType.EXECUTION);
        List<PortDef> inputs = collectInputPorts(data);
        List<PortDef> outputs = collectOutputPorts(data);
        List<PortDef> execOutputs = collectExecOutputs(data);

        appendLeftRows(builder, execInputs);
        appendLeftRows(builder, inputs);
        appendRightRows(builder, outputs);
        appendRightRows(builder, execOutputs);

        return builder.build();
    }

    private static List<PortDef> collectInputPorts(NodeData data) {
        LinkedHashMap<String, PortDef> ports = new LinkedHashMap<>();
        for (PortDef port : collectConfiguredPorts(data, "inputs", null)) {
            ports.put(port.id(), port);
        }

        Map<String, Object> inputs = data != null ? data.inputs : null;
        if (inputs != null) {
            inputs.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> ports.putIfAbsent(
                            entry.getKey(),
                            port(entry.getKey(), inferType(entry.getValue()), entry.getValue())
                    ));
        }
        return new ArrayList<>(ports.values());
    }

    private static List<PortDef> collectOutputPorts(NodeData data) {
        LinkedHashMap<String, PortDef> ports = new LinkedHashMap<>();
        for (PortDef port : collectConfiguredPorts(data, "outputs", null)) {
            ports.put(port.id(), port);
        }

        Map<String, List<Connection>> outputs = data != null ? data.outputs : null;
        if (outputs != null) {
            outputs.keySet().stream()
                    .sorted()
                    .forEach(id -> ports.putIfAbsent(id, port(id, PortType.ANY, null)));
        }
        return new ArrayList<>(ports.values());
    }

    private static List<PortDef> collectExecOutputs(NodeData data) {
        LinkedHashMap<String, PortDef> ports = new LinkedHashMap<>();
        for (PortDef port : collectConfiguredPorts(data, "exec_outputs", PortType.EXECUTION)) {
            ports.put(port.id(), port);
        }

        Map<String, Connection> execOutputs = data != null ? data.execOutputs : null;
        if (execOutputs != null) {
            execOutputs.keySet().stream()
                    .sorted()
                    .forEach(id -> ports.putIfAbsent(id, port(id, PortType.EXECUTION, null)));
        }
        return new ArrayList<>(ports.values());
    }

    private static List<PortDef> collectConfiguredPorts(NodeData data, String category, PortType forcedType) {
        Map<String, NodeData.PortConfig> configs = data != null ? data.getPortConfigMap(category) : null;
        if (configs == null || configs.isEmpty()) {
            return List.of();
        }

        return configs.entrySet().stream()
                .sorted(Comparator
                        .comparingInt((Map.Entry<String, NodeData.PortConfig> entry) -> order(entry.getValue()))
                        .thenComparing(Map.Entry::getKey))
                .map(entry -> {
                    NodeData.PortConfig config = entry.getValue();
                    PortType type = forcedType != null
                            ? forcedType
                            : config != null && config.type != null ? config.type : PortType.ANY;
                    Object defaultValue = !type.isFlow() ? type.getDefaultValue() : null;
                    return port(entry.getKey(), displayName(entry.getKey(), config), type, defaultValue);
                })
                .toList();
    }

    private static int order(NodeData.PortConfig config) {
        return config != null && config.order != null ? config.order : Integer.MAX_VALUE;
    }

    private static PortType inferType(Object value) {
        PortType type = PortType.getTypeOf(value);
        return type != null && !type.isFlow() ? type : PortType.ANY;
    }

    private static Component displayName(String id, NodeData.PortConfig config) {
        if (config != null && config.customName != null && !config.customName.isBlank()) {
            return Component.literal(config.customName.trim());
        }
        return Component.literal(id != null ? id : "");
    }

    private static PortDef port(String id, PortType type, Object defaultValue) {
        return port(id, Component.literal(id != null ? id : ""), type, defaultValue);
    }

    private static PortDef port(String id, Component displayName, PortType type, Object defaultValue) {
        PortType safeType = type != null ? type : PortType.ANY;
        return new PortDef(id, displayName, safeType, defaultValue, false);
    }

    private static void appendLeftRows(NodeDef.Builder builder, List<PortDef> ports) {
        for (PortDef port : ports) {
            builder.addRow(new PortRow(port, null, UIHint.DEFAULT, null, null));
        }
    }

    private static void appendRightRows(NodeDef.Builder builder, List<PortDef> ports) {
        for (PortDef port : ports) {
            builder.addRow(new PortRow(null, port, UIHint.DEFAULT, null, null));
        }
    }
}
