package com.mine.geometry_node.client.ui.editor.terminal.command;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mine.geometry_node.client.ai.command.CommandResult;
import com.mine.geometry_node.client.ui.session.GraphSession;
import com.mine.geometry_node.client.ui.viewport.menu.NodeSearchService;
import com.mine.geometry_node.client.ui.viewport.node.comment.NodeCommentTextBuilder;
import com.mine.geometry_node.core.node.NodeComment;
import com.mine.geometry_node.core.node.NodeRegistry;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.document.NodeGraph;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.port.PortDef;
import com.mine.geometry_node.core.node.port.PortOptionResolver;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.PortType;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Implements P2 queries against read-only registry and graph snapshots. */
final class TerminalGraphQueryService {
    private static final Gson GSON = new Gson();
    private static final int DETAILS_COLLECTION_LIMIT = 200;
    private static final int VALUE_TEXT_LIMIT = 4096;

    private final NodeGraph graph;

    TerminalGraphQueryService(GraphSession session) {
        this(session == null ? null : session.editorContext.getCurrentGraph());
    }

    TerminalGraphQueryService(NodeGraph graph) {
        this.graph = graph;
    }

    CommandResult searchNodeTypes(String query, int offset, int limit) {
        NodeSearchService.Page page = NodeSearchService.search(
                NodeRegistry.INSTANCE.getAllDefinitions(), query, offset, limit);
        JsonArray items = new JsonArray();
        for (NodeSearchService.Match match : page.items()) {
            NodeDef definition = match.definition();
            JsonObject item = new JsonObject();
            item.addProperty("type_id", definition.typeId());
            item.addProperty("display_name", match.displayName());
            item.addProperty("category", definition.category().name().toLowerCase(Locale.ROOT));
            item.addProperty("comment", match.comment());
            items.add(item);
        }
        JsonObject data = pagedData("query", query, items, page.offset(), page.limit(), page.total());
        return CommandResult.success("NODE_TYPES_FOUND", "找到 " + page.total() + " 个匹配的节点类型", data);
    }

    CommandResult searchGraphNodes(String query, int offset, int limit) {
        GraphReadSnapshot snapshot = snapshot();
        List<Map.Entry<String, NodeData>> matches = snapshot.nodes().entrySet().stream()
                .filter(entry -> matchesNode(entry.getKey(), entry.getValue(), query)).toList();
        PageSlice<Map.Entry<String, NodeData>> page = page(matches, offset, limit);
        JsonArray items = new JsonArray();
        for (Map.Entry<String, NodeData> entry : page.items()) items.add(nodeSummary(entry.getKey(), entry.getValue()));
        JsonObject data = pagedData("query", query, items, page.offset(), page.limit(), page.total());
        return CommandResult.success("GRAPH_NODES_FOUND", "在蓝图中找到 " + page.total() + " 个匹配节点", data);
    }

    CommandResult getNodeDetails(String nodeId) {
        GraphReadSnapshot snapshot = snapshot();
        NodeData node = snapshot.node(nodeId);
        if (node == null) return missingNode(nodeId);
        NodeDef definition = NodeRegistry.INSTANCE.resolveDefinition(node);

        JsonObject data = new JsonObject();
        data.add("node", nodeSummary(nodeId, node));
        data.addProperty("definition_comment", NodeCommentTextBuilder.build(definition));
        JsonArray ports = new JsonArray();
        if (definition != null) {
            for (PortRow row : definition.rows()) {
                if (ports.size() >= DETAILS_COLLECTION_LIMIT) break;
                if (row.leftPort() != null) {
                    ports.add(portDetails(nodeId, node, definition, row, row.leftPort(), "input", snapshot));
                }
                if (ports.size() >= DETAILS_COLLECTION_LIMIT) break;
                if (row.rightPort() != null) {
                    ports.add(portDetails(nodeId, node, definition, row, row.rightPort(), "output", snapshot));
                }
            }
        }
        data.add("ports", ports);
        List<GraphReadSnapshot.Edge> directEdges = new ArrayList<>(snapshot.incoming(nodeId));
        directEdges.addAll(snapshot.outgoing(nodeId));
        directEdges = directEdges.stream().distinct().sorted(GraphReadSnapshot.Edge.ORDER).toList();
        JsonArray connections = edgeArray(limit(directEdges, DETAILS_COLLECTION_LIMIT));
        data.add("connections", connections);
        return CommandResult.success("NODE_DETAILS", "已读取节点 " + nodeId, data);
    }

    CommandResult getNodeConnections(String nodeId, String direction, int depth, int offset, int limit) {
        GraphReadSnapshot snapshot = snapshot();
        if (snapshot.node(nodeId) == null) return missingNode(nodeId);
        List<GraphReadSnapshot.Edge> candidates;
        if (depth <= 1) {
            candidates = new ArrayList<>(snapshot.incoming(nodeId));
            candidates.addAll(snapshot.outgoing(nodeId));
            candidates = candidates.stream().distinct().sorted(GraphReadSnapshot.Edge.ORDER).toList();
        } else {
            candidates = snapshot.inducedEdges(snapshot.neighborhood(nodeId, depth));
        }
        List<GraphReadSnapshot.Edge> edges = candidates.stream()
                .filter(edge -> matchesDirection(edge, nodeId, direction)).toList();
        PageSlice<GraphReadSnapshot.Edge> page = page(edges, offset, limit);
        JsonObject data = new JsonObject();
        data.addProperty("node_id", nodeId);
        data.addProperty("direction", direction);
        data.addProperty("depth", depth);
        data.add("items", edgeArray(page.items()));
        data.add("page", pageJson(page.offset(), page.limit(), page.total(), page.items().size()));
        return CommandResult.success("NODE_CONNECTIONS", "已读取节点连接", data);
    }

    CommandResult getGraphContext(String focusNodeId, int depth, int offset, int limit) {
        GraphReadSnapshot snapshot = snapshot();
        if (!focusNodeId.isEmpty() && snapshot.node(focusNodeId) == null) return missingNode(focusNodeId);
        List<String> candidateIds = focusNodeId.isEmpty()
                ? List.copyOf(snapshot.nodes().keySet())
                : snapshot.neighborhood(focusNodeId, depth).stream().sorted().toList();
        PageSlice<String> page = page(candidateIds, offset, limit);
        Set<String> returnedIds = new LinkedHashSet<>(page.items());
        JsonArray nodes = new JsonArray();
        for (String nodeId : page.items()) nodes.add(nodeSummary(nodeId, snapshot.node(nodeId)));
        List<GraphReadSnapshot.Edge> visibleEdges = limit(snapshot.inducedEdges(returnedIds), DETAILS_COLLECTION_LIMIT);

        NodeGraph graph = snapshot.graph();
        JsonObject data = new JsonObject();
        data.addProperty("graph_kind", text(graph.graphKind));
        data.addProperty("version", text(graph.version));
        data.addProperty("comment", text(graph.comment));
        JsonArray tags = new JsonArray();
        if (graph.tags != null) graph.tags.stream().filter(value -> value != null).limit(DETAILS_COLLECTION_LIMIT).forEach(tags::add);
        data.add("tags", tags);
        data.addProperty("node_count", snapshot.nodes().size());
        data.addProperty("connection_count", snapshot.edges().size());
        data.addProperty("focus_node_id", focusNodeId);
        data.addProperty("depth", depth);
        data.add("nodes", nodes);
        data.add("connections", edgeArray(visibleEdges));
        data.add("page", pageJson(page.offset(), page.limit(), page.total(), page.items().size()));
        return CommandResult.success("GRAPH_CONTEXT", "已读取蓝图上下文", data);
    }

    CommandResult validateGraph(int offset, int limit) {
        GraphReadSnapshot snapshot = snapshot();
        List<ValidationDiagnostic> diagnostics = validate(snapshot);
        PageSlice<ValidationDiagnostic> page = page(diagnostics, offset, limit);
        int errors = (int) diagnostics.stream().filter(value -> value.severity().equals("error")).count();
        int warnings = diagnostics.size() - errors;
        JsonArray items = new JsonArray();
        for (ValidationDiagnostic diagnostic : page.items()) items.add(diagnostic.toJson());
        JsonObject data = new JsonObject();
        data.addProperty("valid", errors == 0);
        data.addProperty("error_count", errors);
        data.addProperty("warning_count", warnings);
        data.add("diagnostics", items);
        data.add("page", pageJson(page.offset(), page.limit(), page.total(), page.items().size()));
        return CommandResult.success("GRAPH_VALIDATION", errors == 0 ? "蓝图只读校验通过" : "蓝图存在校验错误", data);
    }

    CommandResult getPortOptions(String nodeId, String portId, String query, int offset, int limit) {
        GraphReadSnapshot snapshot = snapshot();
        NodeData node = snapshot.node(nodeId);
        if (node == null) return missingNode(nodeId);
        NodeDef definition = NodeRegistry.INSTANCE.resolveDefinition(node);
        PortRow row = findInputRow(definition, portId);
        if (row == null) return CommandResult.failure("PORT_NOT_FOUND", "节点不存在输入端口: " + portId);
        if (row.uiHint() != UIHint.SELECT) {
            return CommandResult.failure("PORT_OPTIONS_UNSUPPORTED", "指定端口不是 SELECT 下拉端口");
        }
        PortOptionResolver.Resolution resolution = PortOptionResolver.resolve(row, registryAccess(),
                key -> Component.translatable(key).getString());
        Object selectedRaw = node.inputs != null && node.inputs.containsKey(portId)
                ? node.inputs.get(portId) : row.leftPort().defaultValue();
        String selected = selectedRaw == null ? "" : selectedRaw.toString();
        List<PortOptionResolver.Option> matches = resolution.options().stream()
                .filter(option -> NodeSearchService.matches(query, option.id(), option.label())).toList();
        PageSlice<PortOptionResolver.Option> page = page(matches, offset, limit);
        JsonArray items = new JsonArray();
        for (PortOptionResolver.Option option : page.items()) {
            JsonObject item = new JsonObject();
            item.addProperty("id", option.id());
            item.addProperty("label", option.label());
            item.addProperty("selected", option.id().equals(selected));
            items.add(item);
        }
        JsonObject data = new JsonObject();
        data.addProperty("node_id", nodeId);
        data.addProperty("port_id", portId);
        data.addProperty("source", resolution.source().name().toLowerCase(Locale.ROOT));
        data.addProperty("registry_id", resolution.registryId());
        data.addProperty("available", resolution.available());
        data.addProperty("selected_value", selected);
        data.add("items", items);
        data.add("page", pageJson(page.offset(), page.limit(), page.total(), page.items().size()));
        return CommandResult.success("PORT_OPTIONS", "已读取端口选项", data);
    }

    private GraphReadSnapshot snapshot() {
        return GraphReadSnapshot.capture(graph);
    }

    private static boolean matchesNode(String nodeId, NodeData node, String query) {
        NodeDef definition = NodeRegistry.INSTANCE.resolveDefinition(node);
        return NodeSearchService.matches(query, nodeId, node.type, node.customName, node.comment,
                definition == null ? "" : definition.displayName().getString(), NodeCommentTextBuilder.build(definition));
    }

    private static JsonObject nodeSummary(String nodeId, NodeData node) {
        NodeDef definition = NodeRegistry.INSTANCE.resolveDefinition(node);
        JsonObject result = new JsonObject();
        result.addProperty("node_id", nodeId);
        result.addProperty("type_id", text(node.type));
        result.addProperty("display_name", definition == null ? "" : definition.displayName().getString());
        result.addProperty("custom_name", text(node.customName));
        result.addProperty("comment", text(node.comment));
        result.addProperty("x", node.uiPos != null && node.uiPos.length > 0 ? finite(node.uiPos[0]) : 0.0);
        result.addProperty("y", node.uiPos != null && node.uiPos.length > 1 ? finite(node.uiPos[1]) : 0.0);
        return result;
    }

    private static JsonObject portDetails(String nodeId, NodeData node, NodeDef definition, PortRow row, PortDef port,
                                          String direction, GraphReadSnapshot snapshot) {
        boolean input = direction.equals("input");
        boolean hasStored = input && node.inputs != null && node.inputs.containsKey(port.id());
        Object stored = hasStored ? node.inputs.get(port.id()) : null;
        Object effective = hasStored ? stored : port.defaultValue();
        boolean connected = input
                ? snapshot.incoming(nodeId).stream().anyMatch(edge -> edge.inputPortId().equals(port.id()))
                : snapshot.outgoing(nodeId).stream().anyMatch(edge -> edge.outputPortId().equals(port.id()));
        JsonObject result = new JsonObject();
        result.addProperty("port_id", port.id());
        result.addProperty("direction", direction);
        result.addProperty("type", port.type() == null ? "any" : port.type().name().toLowerCase(Locale.ROOT));
        result.addProperty("display_name", effectivePortName(node, portCategory(port, input), port,
                port.displayName().getString()));
        result.addProperty("ui_hint", row.uiHint() == null ? "default" : row.uiHint().name().toLowerCase(Locale.ROOT));
        result.addProperty("hidden", port.hidePin());
        result.addProperty("connected", connected);
        result.addProperty("has_stored_value", hasStored);
        result.addProperty("stored_value_json", hasStored ? valueJson(stored) : "");
        result.addProperty("default_value_json", valueJson(port.defaultValue()));
        result.addProperty("effective_value_json", valueJson(effective));
        result.addProperty("comment", portComment(definition, port.id(), input));
        return result;
    }

    private static String effectivePortName(NodeData node, String category, PortDef port, String fallback) {
        if (node.portConfig == null) return fallback;
        Map<String, NodeData.PortConfig> configs = switch (category) {
            case "inputs" -> node.portConfig.inputs;
            case "exec_inputs" -> node.portConfig.execInputs;
            case "exec_outputs" -> node.portConfig.execOutputs;
            case "outputs" -> node.portConfig.outputs;
            default -> null;
        };
        NodeData.PortConfig config = configs == null ? null : configs.get(port.id());
        return config != null && config.customName != null && !config.customName.isBlank()
                ? config.customName : fallback;
    }

    private static String portCategory(PortDef port, boolean input) {
        boolean flow = port.type() != null && port.type().isFlow();
        if (input) return flow ? "exec_inputs" : "inputs";
        return flow ? "exec_outputs" : "outputs";
    }

    private static String portComment(NodeDef definition, String portId, boolean input) {
        if (definition == null || definition.nodeComment() == null) return "";
        List<NodeComment.PortComment> comments = input ? definition.nodeComment().inputs() : definition.nodeComment().outputs();
        return comments.stream().filter(value -> value.portId().equals(portId)).findFirst()
                .map(value -> Component.translatable(value.textKey()).getString()).orElse("");
    }

    private static PortRow findInputRow(NodeDef definition, String portId) {
        if (definition == null) return null;
        for (PortRow row : definition.rows()) {
            if (row.leftPort() != null && row.leftPort().id().equals(portId)) return row;
        }
        return null;
    }

    private static boolean matchesDirection(GraphReadSnapshot.Edge edge, String nodeId, String direction) {
        return switch (direction) {
            case "incoming" -> edge.inputNodeId().equals(nodeId);
            case "outgoing" -> edge.outputNodeId().equals(nodeId);
            default -> true;
        };
    }

    private static List<ValidationDiagnostic> validate(GraphReadSnapshot snapshot) {
        List<ValidationDiagnostic> diagnostics = new ArrayList<>();
        Map<String, NodeDef> definitions = new HashMap<>();
        if (snapshot.graph().nodes != null) {
            for (Map.Entry<String, NodeData> entry : snapshot.graph().nodes.entrySet()) {
                if (entry.getKey() == null || entry.getKey().isBlank()) {
                    diagnostics.add(error("NODE_ID_MISSING", "图中存在空节点 ID", "", ""));
                }
                if (entry.getValue() == null) {
                    diagnostics.add(error("NODE_DATA_MISSING", "节点索引指向空数据", text(entry.getKey()), ""));
                }
            }
        }
        for (Map.Entry<String, NodeData> entry : snapshot.nodes().entrySet()) {
            String nodeId = entry.getKey();
            NodeData node = entry.getValue();
            NodeDef definition = NodeRegistry.INSTANCE.resolveDefinition(node);
            definitions.put(nodeId, definition);
            if (node.type == null || node.type.isBlank()) {
                diagnostics.add(error("NODE_TYPE_MISSING", "节点缺少 type_id", nodeId, ""));
            } else if (definition == null) {
                diagnostics.add(error("NODE_TYPE_UNKNOWN", "未找到节点类型定义: " + node.type, nodeId, ""));
            }
            if (node.id != null && !node.id.equals(nodeId)) {
                diagnostics.add(warning("NODE_ID_MISMATCH", "节点内部 ID 与图索引 ID 不一致", nodeId, ""));
            }
            if (definition != null && node.inputs != null) {
                Set<String> inputIds = portTypes(definition, true).keySet();
                for (String storedPort : node.inputs.keySet()) {
                    if (!inputIds.contains(storedPort)) {
                        diagnostics.add(warning("STORED_INPUT_UNKNOWN", "存储值引用了不存在的输入端口", nodeId, storedPort));
                    }
                }
            }
            validateStoredConnections(nodeId, node, diagnostics);
        }

        Set<String> inbound = new HashSet<>();
        for (GraphReadSnapshot.Edge edge : snapshot.edges()) {
            NodeDef outputDefinition = definitions.get(edge.outputNodeId());
            NodeDef inputDefinition = definitions.get(edge.inputNodeId());
            if (!snapshot.nodes().containsKey(edge.inputNodeId())) {
                diagnostics.add(error("CONNECTION_TARGET_NODE_MISSING", "连接目标节点不存在", edge.outputNodeId(), edge.outputPortId()));
                continue;
            }
            PortType outputType = portTypes(outputDefinition, false).get(edge.outputPortId());
            PortType inputType = portTypes(inputDefinition, true).get(edge.inputPortId());
            if (outputType == null) {
                diagnostics.add(error("CONNECTION_OUTPUT_PORT_MISSING", "连接源端口不存在", edge.outputNodeId(), edge.outputPortId()));
            }
            if (inputType == null) {
                diagnostics.add(error("CONNECTION_INPUT_PORT_MISSING", "连接目标端口不存在", edge.inputNodeId(), edge.inputPortId()));
            }
            if (outputType != null && inputType != null && !PortType.isCompatible(outputType, inputType)) {
                diagnostics.add(error("CONNECTION_TYPE_MISMATCH", "连接端口类型不兼容", edge.inputNodeId(), edge.inputPortId()));
            }
            String inboundKey = edge.kind() + "\u0000" + edge.inputNodeId() + "\u0000" + edge.inputPortId();
            if (!inbound.add(inboundKey)) {
                diagnostics.add(error("MULTIPLE_INBOUND_CONNECTIONS", "输入端口存在多个入站连接", edge.inputNodeId(), edge.inputPortId()));
            }
        }
        diagnostics.sort(Comparator.comparing(ValidationDiagnostic::severity)
                .thenComparing(ValidationDiagnostic::code).thenComparing(ValidationDiagnostic::nodeId)
                .thenComparing(ValidationDiagnostic::portId).thenComparing(ValidationDiagnostic::message));
        return List.copyOf(diagnostics);
    }

    private static void validateStoredConnections(String nodeId, NodeData node,
                                                   List<ValidationDiagnostic> diagnostics) {
        if (node.execOutputs != null) {
            node.execOutputs.forEach((portId, connection) -> {
                if (portId == null || portId.isBlank() || connection == null || !connection.isValid()) {
                    diagnostics.add(error("CONNECTION_MALFORMED", "执行连接缺少有效的端口或目标", nodeId, text(portId)));
                }
            });
        }
        if (node.outputs != null) {
            node.outputs.forEach((portId, connections) -> {
                if (portId == null || portId.isBlank() || connections == null) {
                    diagnostics.add(error("CONNECTION_MALFORMED", "数据连接缺少有效的源端口或列表", nodeId, text(portId)));
                    return;
                }
                for (var connection : connections) {
                    if (connection == null || !connection.isValid()) {
                        diagnostics.add(error("CONNECTION_MALFORMED", "数据连接缺少有效目标", nodeId, portId));
                    }
                }
            });
        }
    }

    private static Map<String, PortType> portTypes(NodeDef definition, boolean input) {
        if (definition == null) return Map.of();
        Map<String, PortType> ports = new LinkedHashMap<>();
        for (PortRow row : definition.rows()) {
            PortDef port = input ? row.leftPort() : row.rightPort();
            if (port != null) ports.put(port.id(), port.type());
        }
        return ports;
    }

    private static CommandResult missingNode(String nodeId) {
        return CommandResult.failure("NODE_NOT_FOUND", "当前蓝图不存在节点: " + nodeId);
    }

    private static JsonArray edgeArray(List<GraphReadSnapshot.Edge> edges) {
        JsonArray array = new JsonArray();
        for (GraphReadSnapshot.Edge edge : edges) {
            JsonObject item = new JsonObject();
            item.addProperty("kind", edge.kind());
            item.addProperty("output_node_id", edge.outputNodeId());
            item.addProperty("output_port_id", edge.outputPortId());
            item.addProperty("input_node_id", edge.inputNodeId());
            item.addProperty("input_port_id", edge.inputPortId());
            array.add(item);
        }
        return array;
    }

    private static JsonObject pagedData(String name, String value, JsonArray items, int offset, int limit, int total) {
        JsonObject data = new JsonObject();
        data.addProperty(name, text(value));
        data.add("items", items);
        data.add("page", pageJson(offset, limit, total, items.size()));
        return data;
    }

    private static JsonObject pageJson(int offset, int limit, int total, int returned) {
        JsonObject page = new JsonObject();
        page.addProperty("offset", offset);
        page.addProperty("limit", limit);
        page.addProperty("total", total);
        page.addProperty("has_more", offset + returned < total);
        return page;
    }

    private static <T> PageSlice<T> page(List<T> values, int offset, int limit) {
        int start = Math.min(offset, values.size());
        int end = Math.min(values.size(), start + limit);
        return new PageSlice<>(values.subList(start, end), offset, limit, values.size());
    }

    private static <T> List<T> limit(List<T> values, int limit) {
        return values.size() <= limit ? values : values.subList(0, limit);
    }

    private static String valueJson(Object value) {
        if (value == null) return "null";
        String encoded;
        try {
            encoded = GSON.toJson(value);
        } catch (RuntimeException exception) {
            encoded = GSON.toJson(String.valueOf(value));
        }
        return encoded.length() <= VALUE_TEXT_LIMIT
                ? encoded : GSON.toJson(encoded.substring(0, VALUE_TEXT_LIMIT) + "...<truncated>");
    }

    private static String text(String value) { return value == null ? "" : value; }

    private static double finite(float value) { return Float.isFinite(value) ? value : 0.0; }

    private static RegistryAccess registryAccess() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null ? null : minecraft.level.registryAccess();
    }

    private static ValidationDiagnostic error(String code, String message, String nodeId, String portId) {
        return new ValidationDiagnostic("error", code, message, text(nodeId), text(portId));
    }

    private static ValidationDiagnostic warning(String code, String message, String nodeId, String portId) {
        return new ValidationDiagnostic("warning", code, message, text(nodeId), text(portId));
    }

    private record PageSlice<T>(List<T> items, int offset, int limit, int total) {
        private PageSlice {
            items = List.copyOf(items);
        }
    }

    private record ValidationDiagnostic(String severity, String code, String message, String nodeId, String portId) {
        JsonObject toJson() {
            JsonObject result = new JsonObject();
            result.addProperty("severity", severity);
            result.addProperty("code", code);
            result.addProperty("message", message);
            result.addProperty("node_id", nodeId);
            result.addProperty("port_id", portId);
            return result;
        }
    }
}
