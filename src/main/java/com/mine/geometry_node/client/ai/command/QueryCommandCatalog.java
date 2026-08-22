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
        registry.register(searchGraphNodes());
        registry.register(getNodeDetails());
        registry.register(getNodeConnections());
        registry.register(getGraphContext());
        registry.register(validateGraph());
        registry.register(getPortOptions());
    }

    private static CommandSpec searchNodes() {
        return querySpec("search_nodes", "搜索可创建的已注册节点类型", "search_nodes [关键词] [offset] [limit]",
                List.of(queryArgument(), offsetArgument(), limitArgument()), searchNodeOutput(), false,
                (target, values) -> target.searchNodeTypes(text(values, "query"), integer(values, "offset"),
                        integer(values, "limit")));
    }

    private static CommandSpec searchGraphNodes() {
        return querySpec("search_graph_nodes", "在当前蓝图中搜索节点实例", "search_graph_nodes [关键词] [offset] [limit]",
                List.of(queryArgument(), offsetArgument(), limitArgument()), graphNodeSearchOutput(), true,
                (target, values) -> target.searchGraphNodes(text(values, "query"), integer(values, "offset"),
                        integer(values, "limit")));
    }

    private static CommandSpec getNodeDetails() {
        return querySpec("get_node_details", "查询指定节点、端口、Comment 与端口值", "get_node_details <节点ID>",
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
        return querySpec("get_node_connections", "查询指定节点的双向连接与局部邻域",
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
        return querySpec("get_graph_context", "查询当前蓝图概况、节点和连接",
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

    private static CommandSpec querySpec(String name, String description, String usage,
                                         List<CommandArgumentSpec> arguments, JsonObject outputSchema,
                                         boolean requiresGraph, QueryHandler handler) {
        return new CommandSpec(name, 1, List.of(), description, usage, arguments, outputSchema,
                ToolContract.CommandEffect.READ_ONLY, ToolContract.RiskLevel.READ_ONLY, requiresGraph, false,
                CommandSpec.Exposure.MODEL_VISIBLE, (context, values) -> {
                    if (!(context.target() instanceof GraphQueryTarget target)) {
                        return CommandResult.failure("QUERY_TARGET_REQUIRED", "当前环境不支持节点查询");
                    }
                    return handler.execute(target, values);
                });
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
        JsonObject item = nodeSummarySchema();
        return pagedOutput("query", item);
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
                property("connections", arraySchema(connectionSchema(), 0, MAX_LIMIT)), property("page", pageSchema())
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
                property("items", arraySchema(item, 0, MAX_LIMIT)), property("page", pageSchema())
        ), "node_id", "port_id", "source", "registry_id", "available", "selected_value", "items", "page");
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
