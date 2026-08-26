package com.mine.geometry_node.client.ai.command;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mine.geometry_node.client.ai.protocol.ToolContract;

import java.util.List;
import java.util.Map;

/** P2 read-only tools. Definitions remain part of the single production registry. */
final class QueryCommandCatalog {
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private QueryCommandCatalog() {}

    static void registerInto(CommandRegistry registry) {
        registry.register(searchNodes());
        registry.register(getNodeTypeDetails());
        registry.register(getNodeTypePortOptions());
        registry.register(searchGraphNodes());
        registry.register(getGraphStats());
        registry.register(getNodeDetails());
        registry.register(getNodeConnections());
        registry.register(getGraphContext());
        registry.register(validateGraph());
        registry.register(getPortOptions());
        registry.register(getUiContext());
        registry.register(getSurfaceContext());
    }

    private static CommandSpec searchNodes() {
        return querySpec("search_nodes", "搜索可创建的已注册节点类型", "search_nodes [关键词] [offset] [limit]",
                List.of(queryArgument(), offsetArgument(), limitArgument()), searchNodeOutput(), false,
                (target, values) -> target.searchNodeTypes(text(values, "query"), integer(values, "offset"),
                        integer(values, "limit")));
    }

    private static CommandSpec searchGraphNodes() {
        List<CommandArgumentSpec> arguments = List.of(
                queryArgument(),
                argument("type_id", "可选的精确节点类型 ID", false, new JsonPrimitive(""), stringSchema(0)),
                argument("category", "可选的精确节点分类", false, new JsonPrimitive(""),
                        enumStringSchema("", "event", "flow_control", "action", "dialogue", "quest", "math",
                                "logic", "data", "variable", "custom")),
                argument("comment_filter", "Comment 过滤: any/with/without", false, new JsonPrimitive("any"),
                        enumStringSchema("any", "with", "without")),
                argument("connection_state", "连接状态: any/connected/unconnected", false,
                        new JsonPrimitive("any"), enumStringSchema("any", "connected", "unconnected")),
                offsetArgument(), limitArgument()
        );
        return querySpec("search_graph_nodes", "按关键词、精确类型、分类、Comment 或连接状态查找当前图中的节点实例",
                "search_graph_nodes [关键词] [type_id] [category] [any|with|without] "
                        + "[any|connected|unconnected] [offset] [limit]",
                arguments, graphNodeSearchOutput(), true,
                (target, values) -> target.searchGraphNodes(text(values, "query"), text(values, "type_id"),
                        text(values, "category"), text(values, "comment_filter"),
                        text(values, "connection_state"), integer(values, "offset"), integer(values, "limit")));
    }

    private static CommandSpec getNodeTypeDetails() {
        List<CommandArgumentSpec> arguments = List.of(
                argument("type_id", "已注册节点类型 ID", true, null, stringSchema(1))
        );
        return querySpec("get_node_type_details",
                "创建节点前查询已注册节点类型的默认端口、Comment、写入能力和选项来源；动态端口创建后再查询实例详情",
                "get_node_type_details <type_id>", arguments, nodeTypeDetailsOutput(), false,
                (target, values) -> target.getNodeTypeDetails(text(values, "type_id")));
    }

    private static CommandSpec getNodeTypePortOptions() {
        List<CommandArgumentSpec> arguments = List.of(
                argument("type_id", "已注册节点类型 ID", true, null, stringSchema(1)),
                argument("port_id", "默认定义中的 SELECT 输入端口 ID", true, null, stringSchema(1)),
                queryArgument(), offsetArgument(), limitArgument()
        );
        return querySpec("get_node_type_port_options",
                "创建节点前查询指定类型 SELECT 端口的方块、物品、实体或其他合法选项，返回稳定 option ID 和上下文 token",
                "get_node_type_port_options <type_id> <port_id> [关键词] [offset] [limit]",
                arguments, nodeTypePortOptionsOutput(), false,
                (target, values) -> target.getNodeTypePortOptions(text(values, "type_id"), text(values, "port_id"),
                        text(values, "query"), integer(values, "offset"), integer(values, "limit")));
    }

    private static CommandSpec getGraphStats() {
        List<CommandArgumentSpec> arguments = List.of(
                argument("type_id", "可选的精确节点类型 ID 过滤", false, new JsonPrimitive(""), stringSchema(0)),
                argument("category", "可选的精确节点分类过滤", false, new JsonPrimitive(""),
                        enumStringSchema("", "event", "flow_control", "action", "dialogue", "quest", "math",
                                "logic", "data", "variable", "custom")),
                argument("group_by", "聚合维度: none/type/category", false, new JsonPrimitive("none"),
                        enumStringSchema("none", "type", "category")),
                offsetArgument(), limitArgument()
        );
        return querySpec("get_graph_stats",
                "轻量查询当前图的节点数量、连接数量、Frame 数量、Comment 数量、孤立节点以及按类型或分类统计；不返回整图内容",
                "get_graph_stats [type_id] [category] [none|type|category] [offset] [limit]",
                arguments, graphStatsOutput(), true,
                (target, values) -> target.getGraphStats(text(values, "type_id"), text(values, "category"),
                        text(values, "group_by"), integer(values, "offset"), integer(values, "limit")));
    }

    private static CommandSpec getNodeDetails() {
        return querySpec("get_node_details", "当用户询问指定节点的信息、端口、Comment 或端口值时调用",
                "get_node_details <节点ID>",
                List.of(nodeIdArgument()), nodeDetailsOutput(), true,
                (target, values) -> target.getNodeDetails(text(values, "node_id")));
    }

    private static CommandSpec getNodeConnections() {
        JsonObject direction = stringSchema(1);
        JsonArray allowed = new JsonArray();
        allowed.add("all");
        allowed.add("incoming");
        allowed.add("outgoing");
        direction.add("enum", allowed);
        List<CommandArgumentSpec> arguments = List.of(
                nodeIdArgument(),
                argument("direction", "连接方向: all/incoming/outgoing", false, new JsonPrimitive("all"), direction),
                argument("depth", "局部邻域深度，1 到 4", false, new JsonPrimitive(1), integerSchema(1, 4)),
                offsetArgument(), limitArgument()
        );
        return querySpec("get_node_connections", "当用户询问指定节点连接了哪些节点或局部邻域时调用",
                "get_node_connections <节点ID> [all|incoming|outgoing] [depth] [offset] [limit]",
                arguments, connectionOutput(), true,
                (target, values) -> target.getNodeConnections(text(values, "node_id"), text(values, "direction"),
                        integer(values, "depth"), integer(values, "offset"), integer(values, "limit")));
    }

    private static CommandSpec getGraphContext() {
        List<CommandArgumentSpec> arguments = List.of(
                argument("focus_node_id", "可选的中心节点 ID；为空时返回全图分页", false,
                        new JsonPrimitive(""), stringSchema(0), QueryCommandCatalog::completeNodeIds),
                argument("depth", "中心节点的局部邻域深度，0 到 4", false,
                        new JsonPrimitive(1), integerSchema(0, 4)),
                offsetArgument(), limitArgument()
        );
        return querySpec("get_graph_context", "当用户需要当前图的节点内容、连接内容或指定节点局部上下文时调用；纯统计改用 get_graph_stats",
                "get_graph_context [中心节点ID] [depth] [offset] [limit]", arguments, graphContextOutput(), true,
                (target, values) -> target.getGraphContext(text(values, "focus_node_id"), integer(values, "depth"),
                        integer(values, "offset"), integer(values, "limit")));
    }

    private static CommandSpec validateGraph() {
        return querySpec("validate_graph", "只读检查当前蓝图的节点、端口和连接引用", "validate_graph [offset] [limit]",
                List.of(offsetArgument(), limitArgument()), validationOutput(), true,
                (target, values) -> target.validateGraph(integer(values, "offset"), integer(values, "limit")));
    }

    private static CommandSpec getPortOptions() {
        List<CommandArgumentSpec> arguments = List.of(
                nodeIdArgument(),
                argument("port_id", "SELECT 输入端口 ID", true, null, stringSchema(1),
                        QueryCommandCatalog::completeInputPorts),
                queryArgument(), offsetArgument(), limitArgument()
        );
        return querySpec("get_port_options", "查询 SELECT 端口的静态或动态下拉选项",
                "get_port_options <节点ID> <端口ID> [关键词] [offset] [limit]", arguments, portOptionsOutput(), true,
                (target, values) -> target.getPortOptions(text(values, "node_id"), text(values, "port_id"),
                        text(values, "query"), integer(values, "offset"), integer(values, "limit")));
    }

    private static CommandSpec getUiContext() {
        JsonObject surfaces = new JsonObject();
        surfaces.addProperty("type", "array");
        surfaces.add("items", object(properties(
                property("surface_ref", stringSchema(1)), property("type", stringSchema(1)),
                property("visible", booleanSchema())), "surface_ref", "type", "visible"));
        JsonObject output = object(properties(
                property("default_viewport", stringSchema(0)), property("surfaces", surfaces)),
                "default_viewport", "surfaces");
        return new CommandSpec("get_ui_context", List.of(),
                "查询 GeometryNode 当前窗口、Viewport 编号和默认图目标；用户提到 V1/T1 等界面引用时优先调用",
                "get_ui_context", List.of(), output, ToolContract.CommandEffect.READ_ONLY,
                ToolContract.RiskLevel.READ_ONLY, false, false, CommandSpec.Exposure.MODEL_VISIBLE,
                (context, values) -> context.target() instanceof UiSurfaceQueryTarget target
                        ? target.getUiContext()
                        : CommandResult.failure("UI_CONTEXT_UNAVAILABLE", "当前环境不支持界面上下文查询"));
    }

    private static CommandSpec getSurfaceContext() {
        List<CommandArgumentSpec> arguments = List.of(argument(
                "surface_ref", "界面引用，例如 V1、T1 或 A1", true, null, stringSchema(2)));
        JsonObject output = object(properties(
                property("surface_ref", stringSchema(1)), property("type", stringSchema(1)),
                property("visible", booleanSchema()), property("has_graph", booleanSchema()),
                property("tab_name", stringSchema(0)), property("session_id", stringSchema(1)),
                property("scope_id", stringSchema(1)), property("revision", integerSchema(0, null))),
                "surface_ref", "type", "visible");
        return new CommandSpec("get_surface_context", List.of(),
                "读取指定 GeometryNode 窗口的当前上下文；Viewport 会包含当前蓝图 Tab、Group Scope 和 revision",
                "get_surface_context <surface_ref>", arguments, output, ToolContract.CommandEffect.READ_ONLY,
                ToolContract.RiskLevel.READ_ONLY, false, false, CommandSpec.Exposure.MODEL_VISIBLE,
                (context, values) -> context.target() instanceof UiSurfaceQueryTarget target
                        ? target.getSurfaceContext(text(values, "surface_ref"))
                        : CommandResult.failure("UI_CONTEXT_UNAVAILABLE", "当前环境不支持界面上下文查询"));
    }

    private static CommandSpec querySpec(String name, String description, String usage,
                                         List<CommandArgumentSpec> arguments, JsonObject outputSchema,
                                         boolean requiresGraph, QueryHandler handler) {
        List<CommandArgumentSpec> effectiveArguments = arguments;
        if (requiresGraph) {
            effectiveArguments = new java.util.ArrayList<>(arguments);
            effectiveArguments.add(surfaceRefArgument());
            effectiveArguments = List.copyOf(effectiveArguments);
        }
        return new CommandSpec(name, List.of(), description, usage, effectiveArguments, outputSchema,
                ToolContract.CommandEffect.READ_ONLY, ToolContract.RiskLevel.READ_ONLY, requiresGraph, false,
                CommandSpec.Exposure.MODEL_VISIBLE, (context, values) -> {
                    if (!(context.target() instanceof GraphQueryTarget target)) {
                        return CommandResult.failure("QUERY_TARGET_REQUIRED", "当前环境不支持节点查询");
                    }
                    return handler.execute(target, values);
                });
    }

    private static CommandArgumentSpec surfaceRefArgument() {
        return argument("surface_ref", "可选的 Viewport 引用，例如 V1；为空时使用最近交互或唯一 Viewport",
                false, new JsonPrimitive(""), stringSchema(0));
    }

    private static CommandArgumentSpec nodeIdArgument() {
        return argument("node_id", "图中的节点实例 ID", true, null, stringSchema(1),
                QueryCommandCatalog::completeNodeIds);
    }

    private static CommandArgumentSpec queryArgument() {
        return argument("query", "名称、类型 ID 或 Comment 关键词", false, new JsonPrimitive(""), stringSchema(0));
    }

    private static CommandArgumentSpec offsetArgument() {
        return argument("offset", "从第几条匹配结果开始", false, new JsonPrimitive(0), integerSchema(0, null));
    }

    private static CommandArgumentSpec limitArgument() {
        return argument("limit", "本次最多返回的结果数", false, new JsonPrimitive(DEFAULT_LIMIT),
                integerSchema(1, MAX_LIMIT));
    }

    private static CommandArgumentSpec argument(String name, String description, boolean required,
                                                JsonPrimitive defaultValue, JsonObject schema) {
        return argument(name, description, required, defaultValue, schema, null);
    }

    private static CommandArgumentSpec argument(String name, String description, boolean required,
                                                JsonPrimitive defaultValue, JsonObject schema,
                                                CommandArgumentSpec.CompletionProvider completionProvider) {
        return new CommandArgumentSpec(name, description, required, defaultValue, schema, completionProvider);
    }

    private static java.util.Collection<String> completeNodeIds(String prefix, JsonObject parsed,
                                                                 CommandInvocationContext context) {
        return context.target() instanceof GraphCommandTarget target ? target.nodeIds() : List.of();
    }

    private static java.util.Collection<String> completeInputPorts(String prefix, JsonObject parsed,
                                                                    CommandInvocationContext context) {
        if (!(context.target() instanceof GraphCommandTarget target) || !parsed.has("node_id")) return List.of();
        return target.portIds(parsed.get("node_id").getAsString(), GraphCommandTarget.PortDirection.INPUT);
    }

    private static JsonObject searchNodeOutput() {
        JsonObject item = object(properties(
                property("type_id", stringSchema(1)), property("display_name", stringSchema(0)),
                property("category", stringSchema(1)), property("comment", stringSchema(0))
        ), "type_id", "display_name", "category", "comment");
        return pagedOutput("query", item);
    }

    private static JsonObject graphNodeSearchOutput() {
        JsonObject item = object(properties(
                property("node_id", stringSchema(0)), property("type_id", stringSchema(0)),
                property("display_name", stringSchema(0)), property("custom_name", stringSchema(0)),
                property("comment", stringSchema(0)), property("x", numberSchema()), property("y", numberSchema()),
                property("incoming_count", integerSchema(0, null)),
                property("outgoing_count", integerSchema(0, null))
        ), "node_id", "type_id", "display_name", "custom_name", "comment", "x", "y",
                "incoming_count", "outgoing_count");
        return object(properties(
                property("query", stringSchema(0)), property("type_id", stringSchema(0)),
                property("category", stringSchema(0)), property("comment_filter", stringSchema(1)),
                property("connection_state", stringSchema(1)),
                property("items", arraySchema(item, 0, MAX_LIMIT)), property("page", pageSchema())
        ), "query", "type_id", "category", "comment_filter", "connection_state", "items", "page");
    }

    private static JsonObject nodeTypeDetailsOutput() {
        JsonObject options = object(properties(
                property("source", stringSchema(1)), property("registry_id", stringSchema(0)),
                property("available", booleanSchema()), property("total", integerSchema(0, null))
        ), "source", "registry_id", "available", "total");
        JsonObject port = object(properties(
                property("port_id", stringSchema(1)), property("direction", stringSchema(1)),
                property("type", stringSchema(1)), property("display_name", stringSchema(0)),
                property("ui_hint", stringSchema(1)), property("hidden", booleanSchema()),
                property("default_value_json", stringSchema(0)), property("comment", stringSchema(0)),
                property("dynamic", booleanSchema()), property("writable", booleanSchema()),
                property("write_operation", stringSchema(0)), property("write_restriction", stringSchema(0)),
                property("options", options)
        ), "port_id", "direction", "type", "display_name", "ui_hint", "hidden", "default_value_json",
                "comment", "dynamic", "writable", "write_operation", "write_restriction", "options");
        JsonObject dynamicLimits = object(properties(
                property("min_inputs", integerSchema(0, null)), property("max_inputs", integerSchema(0, null)),
                property("min_outputs", integerSchema(0, null)), property("max_outputs", integerSchema(0, null))
        ));
        return object(properties(
                property("type_id", stringSchema(1)), property("display_name", stringSchema(0)),
                property("category", stringSchema(1)), property("definition_comment", stringSchema(0)),
                property("definition_mode", stringSchema(1)), property("declares_dynamic_ports", booleanSchema()),
                property("port_count", integerSchema(0, null)), property("ports_truncated", booleanSchema()),
                property("dynamic_limits", dynamicLimits), property("ports", arraySchema(port, 0, MAX_LIMIT))
        ), "type_id", "display_name", "category", "definition_comment", "definition_mode",
                "declares_dynamic_ports", "port_count", "ports_truncated", "dynamic_limits", "ports");
    }

    private static JsonObject nodeTypePortOptionsOutput() {
        JsonObject item = object(properties(
                property("id", stringSchema(0)), property("label", stringSchema(0))
        ), "id", "label");
        return object(properties(
                property("type_id", stringSchema(1)), property("port_id", stringSchema(1)),
                property("source", stringSchema(1)), property("registry_id", stringSchema(0)),
                property("available", booleanSchema()), property("option_context_token", stringSchema(64)),
                property("items", arraySchema(item, 0, MAX_LIMIT)), property("page", pageSchema())
        ), "type_id", "port_id", "source", "registry_id", "available", "option_context_token", "items", "page");
    }

    private static JsonObject graphStatsOutput() {
        JsonObject group = object(properties(
                property("key", stringSchema(0)), property("count", integerSchema(0, null))
        ), "key", "count");
        return object(properties(
                property("filter_type_id", stringSchema(0)), property("filter_category", stringSchema(0)),
                property("group_by", stringSchema(1)), property("total_node_count", integerSchema(0, null)),
                property("node_count", integerSchema(0, null)),
                property("total_connection_count", integerSchema(0, null)),
                property("flow_connection_count", integerSchema(0, null)),
                property("data_connection_count", integerSchema(0, null)),
                property("induced_connection_count", integerSchema(0, null)),
                property("frame_count", integerSchema(0, null)),
                property("commented_node_count", integerSchema(0, null)),
                property("unconnected_node_count", integerSchema(0, null)),
                property("groups", arraySchema(group, 0, MAX_LIMIT)), property("page", pageSchema()),
                property("session_id", stringSchema(1)), property("scope_id", stringSchema(1)),
                property("revision", integerSchema(0, null)), property("surface_ref", stringSchema(0))
        ), "filter_type_id", "filter_category", "group_by", "total_node_count", "node_count",
                "total_connection_count", "flow_connection_count", "data_connection_count",
                "induced_connection_count", "frame_count", "commented_node_count", "unconnected_node_count",
                "groups", "page");
    }

    private static JsonObject nodeDetailsOutput() {
        return object(properties(
                property("node", nodeSummarySchema()), property("definition_comment", stringSchema(0)),
                property("ports", arraySchema(portSchema(), 0, MAX_LIMIT)),
                property("connections", arraySchema(connectionSchema(), 0, MAX_LIMIT))
        ), "node", "definition_comment", "ports", "connections");
    }

    private static JsonObject connectionOutput() {
        return object(properties(
                property("node_id", stringSchema(1)), property("direction", stringSchema(1)),
                property("depth", integerSchema(1, 4)), property("items", arraySchema(connectionSchema(), 0, MAX_LIMIT)),
                property("page", pageSchema())
        ), "node_id", "direction", "depth", "items", "page");
    }

    private static JsonObject graphContextOutput() {
        return object(properties(
                property("graph_kind", stringSchema(0)), property("version", stringSchema(0)),
                property("comment", stringSchema(0)), property("tags", arraySchema(stringSchema(0), 0, MAX_LIMIT)),
                property("node_count", integerSchema(0, null)), property("connection_count", integerSchema(0, null)),
                property("focus_node_id", stringSchema(0)), property("depth", integerSchema(0, 4)),
                property("nodes", arraySchema(nodeSummarySchema(), 0, MAX_LIMIT)),
                property("connections", arraySchema(connectionSchema(), 0, MAX_LIMIT)), property("page", pageSchema()),
                property("session_id", stringSchema(1)), property("scope_id", stringSchema(1)),
                property("revision", integerSchema(0, null)), property("surface_ref", stringSchema(0))
        ), "graph_kind", "version", "comment", "tags", "node_count", "connection_count", "focus_node_id",
                "depth", "nodes", "connections", "page");
    }

    private static JsonObject validationOutput() {
        JsonObject diagnostic = object(properties(
                property("severity", stringSchema(1)), property("code", stringSchema(1)),
                property("message", stringSchema(1)), property("node_id", stringSchema(0)),
                property("port_id", stringSchema(0))
        ), "severity", "code", "message", "node_id", "port_id");
        return object(properties(
                property("valid", booleanSchema()), property("error_count", integerSchema(0, null)),
                property("warning_count", integerSchema(0, null)),
                property("diagnostics", arraySchema(diagnostic, 0, MAX_LIMIT)), property("page", pageSchema())
        ), "valid", "error_count", "warning_count", "diagnostics", "page");
    }

    private static JsonObject portOptionsOutput() {
        JsonObject item = object(properties(
                property("id", stringSchema(0)), property("label", stringSchema(0)), property("selected", booleanSchema())
        ), "id", "label", "selected");
        return object(properties(
                property("node_id", stringSchema(1)), property("port_id", stringSchema(1)),
                property("source", stringSchema(1)), property("registry_id", stringSchema(0)),
                property("available", booleanSchema()), property("selected_value", stringSchema(0)),
                property("option_context_token", stringSchema(64)),
                property("items", arraySchema(item, 0, MAX_LIMIT)), property("page", pageSchema())
        ), "node_id", "port_id", "source", "registry_id", "available", "selected_value",
                "option_context_token", "items", "page");
    }

    private static JsonObject pagedOutput(String queryName, JsonObject itemSchema) {
        return object(properties(
                property(queryName, stringSchema(0)), property("items", arraySchema(itemSchema, 0, MAX_LIMIT)),
                property("page", pageSchema())
        ), queryName, "items", "page");
    }

    private static JsonObject pageSchema() {
        return object(properties(
                property("offset", integerSchema(0, null)), property("limit", integerSchema(1, MAX_LIMIT)),
                property("total", integerSchema(0, null)), property("has_more", booleanSchema())
        ), "offset", "limit", "total", "has_more");
    }

    private static JsonObject nodeSummarySchema() {
        return object(properties(
                property("node_id", stringSchema(0)), property("type_id", stringSchema(0)),
                property("display_name", stringSchema(0)), property("custom_name", stringSchema(0)),
                property("comment", stringSchema(0)), property("x", numberSchema()), property("y", numberSchema())
        ), "node_id", "type_id", "display_name", "custom_name", "comment", "x", "y");
    }

    private static JsonObject portSchema() {
        return object(properties(
                property("port_id", stringSchema(0)), property("direction", stringSchema(1)),
                property("type", stringSchema(1)), property("display_name", stringSchema(0)),
                property("ui_hint", stringSchema(1)), property("hidden", booleanSchema()),
                property("connected", booleanSchema()), property("has_stored_value", booleanSchema()),
                property("stored_value_json", stringSchema(0)), property("default_value_json", stringSchema(0)),
                property("effective_value_json", stringSchema(0)), property("comment", stringSchema(0))
        ), "port_id", "direction", "type", "display_name", "ui_hint", "hidden", "connected",
                "has_stored_value", "stored_value_json", "default_value_json", "effective_value_json", "comment");
    }

    private static JsonObject connectionSchema() {
        return object(properties(
                property("kind", stringSchema(1)), property("output_node_id", stringSchema(1)),
                property("output_port_id", stringSchema(1)), property("input_node_id", stringSchema(1)),
                property("input_port_id", stringSchema(1))
        ), "kind", "output_node_id", "output_port_id", "input_node_id", "input_port_id");
    }

    private static JsonObject object(JsonObject properties, String... required) {
        return CommandSpec.objectSchema(properties, required);
    }

    private static JsonObject arraySchema(JsonObject items, Integer minItems, Integer maxItems) {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "array");
        schema.add("items", items);
        if (minItems != null) schema.addProperty("minItems", minItems);
        if (maxItems != null) schema.addProperty("maxItems", maxItems);
        return schema;
    }

    private static JsonObject stringSchema(int minLength) {
        JsonObject schema = CommandSpec.scalarSchema("string");
        schema.addProperty("minLength", minLength);
        return schema;
    }

    private static JsonObject integerSchema(Integer minimum, Integer maximum) {
        JsonObject schema = CommandSpec.scalarSchema("integer");
        if (minimum != null) schema.addProperty("minimum", minimum);
        if (maximum != null) schema.addProperty("maximum", maximum);
        return schema;
    }

    private static JsonObject numberSchema() { return CommandSpec.scalarSchema("number"); }

    private static JsonObject booleanSchema() { return CommandSpec.scalarSchema("boolean"); }

    private static JsonObject enumStringSchema(String... values) {
        JsonObject schema = stringSchema(0);
        JsonArray allowed = new JsonArray();
        for (String value : values) allowed.add(value);
        schema.add("enum", allowed);
        return schema;
    }

    private static Map.Entry<String, JsonObject> property(String name, JsonObject schema) {
        return Map.entry(name, schema);
    }

    @SafeVarargs
    private static JsonObject properties(Map.Entry<String, JsonObject>... values) {
        JsonObject properties = new JsonObject();
        for (Map.Entry<String, JsonObject> value : values) properties.add(value.getKey(), value.getValue());
        return properties;
    }

    private static String text(JsonObject values, String name) { return values.get(name).getAsString(); }

    private static int integer(JsonObject values, String name) { return values.get(name).getAsInt(); }

    @FunctionalInterface
    private interface QueryHandler {
        CommandResult execute(GraphQueryTarget target, JsonObject values);
    }
}
