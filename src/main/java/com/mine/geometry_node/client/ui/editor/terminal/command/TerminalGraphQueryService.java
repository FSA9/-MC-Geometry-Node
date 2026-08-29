package com.mine.geometry_node.client.ui.editor.terminal.command;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mine.geometry_node.client.ai.command.CommandResult;
import com.mine.geometry_node.client.ai.graph.PortEditCapabilityResolver;
import com.mine.geometry_node.client.ui.persistence.GraphJsonIO;
import com.mine.geometry_node.client.ui.editor.graph.menu.NodeSearchService;
import com.mine.geometry_node.client.ui.editor.graph.node.comment.NodeCommentTextBuilder;
import com.mine.geometry_node.core.engine.graph.compile.GraphCompilationService;
import com.mine.geometry_node.core.engine.graph.compile.validation.GraphValidationException;
import com.mine.geometry_node.core.node.NodeComment;
import com.mine.geometry_node.core.node.NodeRegistry;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.meta.SchemaKeys;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.document.NodeGraph;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.port.PortDef;
import com.mine.geometry_node.core.node.port.PortOptionResolver;
import com.mine.geometry_node.core.node.port.PortOptionContext;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
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

    CommandResult getNodeTypeDetails(String typeId) {
        NodeDef definition = NodeRegistry.INSTANCE.getDefaultDefinition(typeId);
        if (definition == null) return missingNodeType(typeId);

        JsonArray ports = new JsonArray();
        int portCount = 0;
        boolean declaresDynamicPorts = false;
        for (PortRow row : definition.rows()) {
            if (isDynamic(row)) declaresDynamicPorts = true;
            if (row.leftPort() != null) {
                portCount++;
                if (ports.size() < DETAILS_COLLECTION_LIMIT) {
                    ports.add(nodeTypePortDetails(definition, row, row.leftPort(), "input"));
                }
            }
            if (row.rightPort() != null) {
                portCount++;
                if (ports.size() < DETAILS_COLLECTION_LIMIT) {
                    ports.add(nodeTypePortDetails(definition, row, row.rightPort(), "output"));
                }
            }
        }
        JsonObject dynamicLimits = new JsonObject();
        addOptionalInt(dynamicLimits, "min_inputs", definition.getMeta(SchemaKeys.MIN_DYNAMIC_INPUT).orElse(null));
        addOptionalInt(dynamicLimits, "max_inputs", definition.getMeta(SchemaKeys.MAX_DYNAMIC_INPUT).orElse(null));
        addOptionalInt(dynamicLimits, "min_outputs", definition.getMeta(SchemaKeys.MIN_DYNAMIC_OUTPUT).orElse(null));
        addOptionalInt(dynamicLimits, "max_outputs", definition.getMeta(SchemaKeys.MAX_DYNAMIC_OUTPUT).orElse(null));
        declaresDynamicPorts |= dynamicLimits.size() > 0;

        JsonObject data = new JsonObject();
        data.addProperty("type_id", definition.typeId());
        data.addProperty("display_name", definition.displayName().getString());
        data.addProperty("category", definition.category().name().toLowerCase(Locale.ROOT));
        data.addProperty("definition_comment", NodeCommentTextBuilder.build(definition));
        data.addProperty("definition_mode", "default");
        data.addProperty("declares_dynamic_ports", declaresDynamicPorts);
        data.addProperty("port_count", portCount);
        data.addProperty("ports_truncated", portCount > ports.size());
        data.add("dynamic_limits", dynamicLimits);
        data.add("ports", ports);
        return CommandResult.success("NODE_TYPE_DETAILS", "已读取节点类型默认定义 " + typeId, data);
    }

    CommandResult getNodeTypePortOptions(String typeId, String portId, String query, int offset, int limit) {
        NodeDef definition = NodeRegistry.INSTANCE.getDefaultDefinition(typeId);
        if (definition == null) return missingNodeType(typeId);
        PortRow row = findInputRow(definition, portId);
        if (row == null) return CommandResult.failure("PORT_NOT_FOUND", "节点类型不存在输入端口: " + portId);
        if (row.uiHint() != UIHint.SELECT) {
            return CommandResult.failure("PORT_OPTIONS_UNSUPPORTED", "指定节点类型端口不是 SELECT 下拉端口");
        }
        PortOptionResolver.Resolution resolution = resolveOptions(row);
        List<PortOptionResolver.Option> matches = resolution.options().stream()
                .filter(option -> NodeSearchService.matches(query, option.id(), option.label())).toList();
        PageSlice<PortOptionResolver.Option> page = page(matches, offset, limit);
        JsonArray items = new JsonArray();
        for (PortOptionResolver.Option option : page.items()) {
            JsonObject item = new JsonObject();
            item.addProperty("id", option.id());
            item.addProperty("label", option.label());
            items.add(item);
        }
        JsonObject data = new JsonObject();
        data.addProperty("type_id", typeId);
        data.addProperty("port_id", portId);
        data.addProperty("source", resolution.source().name().toLowerCase(Locale.ROOT));
        data.addProperty("registry_id", resolution.registryId());
        data.addProperty("available", resolution.available());
        data.addProperty("option_context_token", PortOptionContext.token(resolution));
        data.add("items", items);
        data.add("page", pageJson(page.offset(), page.limit(), page.total(), page.items().size()));
        return CommandResult.success("NODE_TYPE_PORT_OPTIONS", "已读取节点类型端口选项", data);
    }

    CommandResult searchGraphNodes(String query, String typeId, String category, String commentFilter,
                                   String connectionState, int offset, int limit) {
        GraphReadSnapshot snapshot = snapshot();
        List<Map.Entry<String, NodeData>> matches = snapshot.nodes().entrySet().stream()
                .filter(entry -> matchesNode(entry.getKey(), entry.getValue(), snapshot.definition(entry.getKey()), query))
                .filter(entry -> matchesTypeAndCategory(entry.getValue(), snapshot.definition(entry.getKey()), typeId, category))
                .filter(entry -> matchesComment(entry.getValue(), commentFilter))
                .filter(entry -> matchesConnectionState(snapshot, entry.getKey(), connectionState)).toList();
        PageSlice<Map.Entry<String, NodeData>> page = page(matches, offset, limit);
        JsonArray items = new JsonArray();
        for (Map.Entry<String, NodeData> entry : page.items()) {
            JsonObject item = nodeSummary(entry.getKey(), entry.getValue(), snapshot.definition(entry.getKey()));
            item.addProperty("incoming_count", snapshot.incoming(entry.getKey()).size());
            item.addProperty("outgoing_count", snapshot.outgoing(entry.getKey()).size());
            items.add(item);
        }
        JsonObject data = pagedData("query", query, items, page.offset(), page.limit(), page.total());
        data.addProperty("type_id", typeId);
        data.addProperty("category", category);
        data.addProperty("comment_filter", commentFilter);
        data.addProperty("connection_state", connectionState);
        return CommandResult.success("GRAPH_NODES_FOUND", "在蓝图中找到 " + page.total() + " 个匹配节点", data);
    }

    CommandResult getGraphStats(String typeId, String category, String groupBy, int offset, int limit) {
        GraphReadSnapshot snapshot = snapshot();
        List<Map.Entry<String, NodeData>> matchingNodes = snapshot.nodes().entrySet().stream()
                .filter(entry -> matchesTypeAndCategory(entry.getValue(), snapshot.definition(entry.getKey()),
                        typeId, category)).toList();
        Set<String> matchingIds = matchingNodes.stream().map(Map.Entry::getKey).collect(java.util.stream.Collectors.toSet());
        long flowConnections = snapshot.edges().stream().filter(edge -> edge.kind().equals("flow")).count();
        long dataConnections = snapshot.edges().stream().filter(edge -> edge.kind().equals("data")).count();
        long behaviorConnections = snapshot.edges().stream()
                .filter(edge -> edge.kind().equals("behavior")).count();
        long inducedConnections = snapshot.edges().stream()
                .filter(edge -> matchingIds.contains(edge.outputNodeId()) && matchingIds.contains(edge.inputNodeId())).count();
        long commentedNodes = matchingNodes.stream().filter(entry -> hasText(entry.getValue().comment)).count();
        long unconnectedNodes = matchingNodes.stream().filter(entry -> snapshot.incoming(entry.getKey()).isEmpty()
                && snapshot.outgoing(entry.getKey()).isEmpty()).count();

        Map<String, Integer> counts = new java.util.TreeMap<>();
        if (!"none".equals(groupBy)) {
            for (Map.Entry<String, NodeData> entry : matchingNodes) {
                String key = "type".equals(groupBy) ? text(entry.getValue().type)
                        : nodeCategory(snapshot.definition(entry.getKey()));
                counts.merge(key, 1, Integer::sum);
            }
        }
        PageSlice<Map.Entry<String, Integer>> groupsPage = page(List.copyOf(counts.entrySet()), offset, limit);
        JsonArray groups = new JsonArray();
        for (Map.Entry<String, Integer> entry : groupsPage.items()) {
            JsonObject item = new JsonObject();
            item.addProperty("input", entry.getKey());
            item.addProperty("count", entry.getValue());
            groups.add(item);
        }

        NodeGraph current = snapshot.graph();
        JsonObject data = new JsonObject();
        data.addProperty("filter_type_id", typeId);
        data.addProperty("filter_category", category);
        data.addProperty("group_by", groupBy);
        data.addProperty("total_node_count", snapshot.nodes().size());
        data.addProperty("node_count", matchingNodes.size());
        data.addProperty("total_connection_count", snapshot.edges().size());
        data.addProperty("flow_connection_count", flowConnections);
        data.addProperty("data_connection_count", dataConnections);
        data.addProperty("behavior_connection_count", behaviorConnections);
        data.addProperty("induced_connection_count", inducedConnections);
        data.addProperty("frame_count", current == null || current.frames == null ? 0 : current.frames.size());
        data.addProperty("commented_node_count", commentedNodes);
        data.addProperty("unconnected_node_count", unconnectedNodes);
        data.add("groups", groups);
        data.add("page", pageJson(groupsPage.offset(), groupsPage.limit(), groupsPage.total(), groupsPage.items().size()));
        return CommandResult.success("GRAPH_STATS", "已读取蓝图统计", data);
    }

    CommandResult getNodeDetails(String nodeId) {
        GraphReadSnapshot snapshot = snapshot();
        NodeData node = snapshot.node(nodeId);
        if (node == null) return missingNode(nodeId);
        NodeDef definition = snapshot.definition(nodeId);

        JsonObject data = new JsonObject();
        data.add("node", nodeSummary(nodeId, node, definition));
        data.addProperty("definition_comment", NodeCommentTextBuilder.build(definition));
        JsonArray ports = new JsonArray();
        Set<String> connectedInputs = snapshot.incoming(nodeId).stream()
                .map(GraphReadSnapshot.Edge::inputPortId).collect(java.util.stream.Collectors.toSet());
        Set<String> connectedOutputs = snapshot.outgoing(nodeId).stream()
                .map(GraphReadSnapshot.Edge::outputPortId).collect(java.util.stream.Collectors.toSet());
        if (definition != null) {
            for (PortRow row : definition.rows()) {
                if (ports.size() >= DETAILS_COLLECTION_LIMIT) break;
                if (row.leftPort() != null) {
                    ports.add(portDetails(node, definition, row, row.leftPort(), "input", connectedInputs));
                }
                if (ports.size() >= DETAILS_COLLECTION_LIMIT) break;
                if (row.rightPort() != null) {
                    ports.add(portDetails(node, definition, row, row.rightPort(), "output", connectedOutputs));
                }
            }
        }
        data.add("ports", ports);
        JsonArray connections = edgeArray(limit(snapshot.direct(nodeId), DETAILS_COLLECTION_LIMIT));
        data.add("connections", connections);
        return CommandResult.success("NODE_DETAILS", "已读取节点 " + nodeId, data);
    }

    CommandResult getNodeConnections(String nodeId, String direction, int depth, int offset, int limit) {
        GraphReadSnapshot snapshot = snapshot();
        if (snapshot.node(nodeId) == null) return missingNode(nodeId);
        List<GraphReadSnapshot.Edge> candidates;
        if (depth <= 1) {
            candidates = snapshot.direct(nodeId);
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
        for (String nodeId : page.items()) {
            nodes.add(nodeSummary(nodeId, snapshot.node(nodeId), snapshot.definition(nodeId)));
        }
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
        NodeDef definition = snapshot.definition(nodeId);
        PortRow row = findInputRow(definition, portId);
        if (row == null) return CommandResult.failure("PORT_NOT_FOUND", "节点不存在输入端口: " + portId);
        if (row.uiHint() != UIHint.SELECT) {
            return CommandResult.failure("PORT_OPTIONS_UNSUPPORTED", "指定端口不是 SELECT 下拉端口");
        }
        PortOptionResolver.Resolution resolution = resolveOptions(row);
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
        data.addProperty("option_context_token", PortOptionContext.token(resolution));
        data.add("items", items);
        data.add("page", pageJson(page.offset(), page.limit(), page.total(), page.items().size()));
        return CommandResult.success("PORT_OPTIONS", "已读取端口选项", data);
    }

    private GraphReadSnapshot snapshot() {
        return GraphReadSnapshot.capture(graph);
    }

    private static boolean matchesNode(String nodeId, NodeData node, NodeDef definition, String query) {
        return NodeSearchService.matches(query, nodeId, node.type, node.customName, node.comment,
                definition == null ? "" : definition.displayName().getString(), NodeCommentTextBuilder.build(definition));
    }

    private static boolean matchesTypeAndCategory(NodeData node, NodeDef definition,
                                                  String typeId, String category) {
        if (!typeId.isEmpty() && !typeId.equals(text(node.type))) return false;
        return category.isEmpty() || category.equals(nodeCategory(definition));
    }

    private static boolean matchesComment(NodeData node, String filter) {
        return switch (filter) {
            case "with" -> hasText(node.comment);
            case "without" -> !hasText(node.comment);
            default -> true;
        };
    }

    private static boolean matchesConnectionState(GraphReadSnapshot snapshot, String nodeId, String state) {
        boolean connected = !snapshot.incoming(nodeId).isEmpty() || !snapshot.outgoing(nodeId).isEmpty();
        return switch (state) {
            case "connected" -> connected;
            case "unconnected" -> !connected;
            default -> true;
        };
    }

    private static String nodeCategory(NodeDef definition) {
        return definition == null ? "" : definition.category().name().toLowerCase(Locale.ROOT);
    }

    private static JsonObject nodeSummary(String nodeId, NodeData node, NodeDef definition) {
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

    private static JsonObject portDetails(NodeData node, NodeDef definition, PortRow row, PortDef port,
                                          String direction, Set<String> connectedPortIds) {
        boolean input = direction.equals("input");
        boolean hasStored = input && node.inputs != null && node.inputs.containsKey(port.id());
        Object stored = hasStored ? node.inputs.get(port.id()) : null;
        Object effective = hasStored ? stored : port.defaultValue();
        JsonObject result = new JsonObject();
        result.addProperty("port_id", port.id());
        result.addProperty("direction", direction);
        result.addProperty("type", port.type() == null ? "any" : port.type().name().toLowerCase(Locale.ROOT));
        result.addProperty("display_name", effectivePortName(node, portCategory(port, input), port,
                port.displayName().getString()));
        result.addProperty("ui_hint", row.uiHint() == null ? "default" : row.uiHint().name().toLowerCase(Locale.ROOT));
        result.addProperty("hidden", port.hidePin());
        result.addProperty("connected", connectedPortIds.contains(port.id()));
        result.addProperty("has_stored_value", hasStored);
        result.addProperty("stored_value_json", hasStored ? valueJson(stored) : "");
        result.addProperty("default_value_json", valueJson(port.defaultValue()));
        result.addProperty("effective_value_json", valueJson(effective));
        result.addProperty("comment", portComment(definition, port.id(), input));
        return result;
    }

    private static JsonObject nodeTypePortDetails(NodeDef definition, PortRow row, PortDef port, String direction) {
        boolean input = "input".equals(direction);
        PortOptionResolver.Resolution resolution = input && row.uiHint() == UIHint.SELECT
                ? resolveOptions(row)
                : new PortOptionResolver.Resolution(PortOptionResolver.Source.NONE, "", false, List.of());
        PortEditCapabilityResolver.Capability capability;
        if (!input) {
            capability = new PortEditCapabilityResolver.Capability(false, "output ports are not directly writable");
        } else if (row.uiHint() == UIHint.SELECT) {
            boolean writable = resolution.available() && !resolution.options().isEmpty();
            capability = new PortEditCapabilityResolver.Capability(writable,
                    writable ? "" : "select options are unavailable or empty");
        } else {
            capability = PortEditCapabilityResolver.resolve(row);
        }
        JsonObject options = new JsonObject();
        options.addProperty("source", resolution.source().name().toLowerCase(Locale.ROOT));
        options.addProperty("registry_id", resolution.registryId());
        options.addProperty("available", resolution.available());
        options.addProperty("total", resolution.options().size());

        JsonObject result = new JsonObject();
        result.addProperty("port_id", port.id());
        result.addProperty("direction", direction);
        result.addProperty("type", port.type() == null ? "any" : port.type().name().toLowerCase(Locale.ROOT));
        result.addProperty("display_name", port.displayName().getString());
        result.addProperty("ui_hint", row.uiHint() == null ? "default" : row.uiHint().name().toLowerCase(Locale.ROOT));
        result.addProperty("hidden", port.hidePin());
        result.addProperty("default_value_json", valueJson(port.defaultValue()));
        result.addProperty("comment", portComment(definition, port.id(), input));
        result.addProperty("dynamic", isDynamic(row));
        result.addProperty("writable", capability.writable());
        result.addProperty("write_operation", capability.writable()
                ? row.uiHint() == UIHint.SELECT ? "set_select_value" : "set_port_value" : "");
        result.addProperty("write_restriction", capability.reason());
        result.add("options", options);
        return result;
    }

    private static boolean isDynamic(PortRow row) {
        return row != null && row.hintParams() != null
                && Boolean.TRUE.equals(row.hintParams().get(PortMetaKeys.IS_DYNAMIC));
    }

    private static PortOptionResolver.Resolution resolveOptions(PortRow row) {
        return PortOptionResolver.resolve(row, registryAccess(), key -> Component.translatable(key).getString());
    }

    private static void addOptionalInt(JsonObject target, String name, Integer value) {
        if (value != null) target.addProperty(name, value);
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
        try {
            GraphCompilationService.INSTANCE.compile("viewport", GraphJsonIO.toJson(snapshot.graph()));
        } catch (GraphValidationException exception) {
            exception.diagnostics().forEach(diagnostic -> diagnostics.add(error(
                    diagnostic.code(), diagnostic.message(), diagnostic.nodeId(),
                    diagnostic.portId().isEmpty() ? diagnostic.relatedNodeId() : diagnostic.portId())));
        } catch (RuntimeException exception) {
            diagnostics.add(error("GRAPH_COMPILE_FAILED",
                    exception.getMessage() != null ? exception.getMessage()
                            : exception.getClass().getSimpleName(), "", ""));
        }
        diagnostics.sort(Comparator.comparing(ValidationDiagnostic::severity)
                .thenComparing(ValidationDiagnostic::code).thenComparing(ValidationDiagnostic::nodeId)
                .thenComparing(ValidationDiagnostic::portId).thenComparing(ValidationDiagnostic::message));
        return List.copyOf(diagnostics);
    }

    private static CommandResult missingNode(String nodeId) {
        return CommandResult.failure("NODE_NOT_FOUND", "当前蓝图不存在节点: " + nodeId);
    }

    private static CommandResult missingNodeType(String typeId) {
        return CommandResult.failure("NODE_TYPE_NOT_FOUND", "注册表不存在节点类型: " + typeId);
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

    private static boolean hasText(String value) { return value != null && !value.isBlank(); }

    private static double finite(float value) { return Float.isFinite(value) ? value : 0.0; }

    private static RegistryAccess registryAccess() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null ? null : minecraft.level.registryAccess();
    }

    private static ValidationDiagnostic error(String code, String message, String nodeId, String portId) {
        return new ValidationDiagnostic("error", code, message, text(nodeId), text(portId));
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
