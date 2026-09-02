package com.mine.geometry_node.client.ai.command;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
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
        registry.register(browseNodeCatalog());
        registry.register(getNodeTypeDetails());
        registry.register(getNodeTypePortOptions());
        registry.register(queryGraphNodes());
        registry.register(getGraphStats());
        registry.register(validateGraph());
        registry.register(getPortOptions());
        registry.register(getUiContext());
        registry.register(getSurfaceContext());
    }

    private static CommandSpec searchNodes() {
        List<CommandArgumentSpec> arguments = List.of(
                queryArgument(),
                argument("path", "目录路径；空字符串表示根目录", false, new JsonPrimitive(""), stringSchema(0)),
                argument("recursive", "是否搜索子目录", false, new JsonPrimitive(true), booleanSchema()),
                offsetArgument(), limitArgument());
        return querySpec("search_nodes", "按短 type_id 搜索可创建节点，可限制到真实节点目录",
                "search_nodes [关键词] [目录] [是否递归] [offset] [limit]",
                arguments, searchNodeOutput(), false,
                (target, values) -> target.searchNodeTypes(text(values, "query"), text(values, "path"),
                        values.get("recursive").getAsBoolean(), integer(values, "offset"), integer(values, "limit")));
    }

    private static CommandSpec browseNodeCatalog() {
        List<CommandArgumentSpec> arguments = List.of(
                argument("path", "目录路径；空字符串表示根目录", false, new JsonPrimitive(""), stringSchema(0)),
                argument("recursive", "是否递归列出后代目录和节点", false, new JsonPrimitive(false), booleanSchema()),
                offsetArgument(), limitArgument());
        return querySpec("browse_node_catalog", "浏览真实节点菜单目录，返回子目录和短 type_id",
                "browse_node_catalog [目录] [是否递归] [offset] [limit]", arguments,
                nodeCatalogOutput(), false,
                (target, values) -> target.browseNodeCatalog(text(values, "path"),
                        values.get("recursive").getAsBoolean(), integer(values, "offset"), integer(values, "limit")));
    }

    private static CommandSpec queryGraphNodes() {
        List<CommandArgumentSpec> arguments = List.of(
                argument("node_ids", "可选的节点实例 ID 列表；空列表查询全部节点", false,
                        jsonArray(), arraySchema(stringSchema(1), 0, MAX_LIMIT)),
                argument("type_ids", "可选的短 type_id 列表，不带 geometry_node: 前缀", false,
                        jsonArray(), arraySchema(stringSchema(1), 0, MAX_LIMIT)),
                argument("directory", "可选节点目录；匹配该目录及其后代", false,
                        new JsonPrimitive(""), stringSchema(0)),
                queryArgument(),
                argument("comment_filter", "Comment 过滤: any/with/without", false, new JsonPrimitive("any"),
                        enumStringSchema("any", "with", "without")),
                argument("connection_state", "连接状态: any/connected/unconnected", false,
                        new JsonPrimitive("any"), enumStringSchema("any", "connected", "unconnected")),
                argument("select", "返回字段的任意组合", false, jsonArray("identity"),
                        enumArraySchema(1, 7, "identity", "values", "connections", "comments", "ports",
                                "position", "metadata")),
                argument("connection_direction", "连接投影和连接状态过滤方向", false,
                        new JsonPrimitive("all"), enumStringSchema("all", "incoming", "outgoing")),
                argument("connection_kinds", "连接类型过滤；空列表表示全部", false, jsonArray(),
                        enumArraySchema(0, 3, "flow", "data", "behavior")),
                offsetArgument(), limitArgument()
        );
        return querySpec("query_graph_nodes",
                "以可组合过滤器查询当前图节点，并只投影 identity、values、connections、comments、ports、position、metadata 中需要的字段",
                "query_graph_nodes [node_ids JSON] [type_ids JSON] [directory] [query] [comment] [connection] "
                        + "[select JSON] [direction] [connection_kinds JSON] [offset] [limit]",
                arguments, graphNodeQueryOutput(), true,
                (target, values) -> target.queryGraphNodes(strings(values, "node_ids"), strings(values, "type_ids"),
                        text(values, "directory"), text(values, "query"), text(values, "comment_filter"),
                        text(values, "connection_state"), strings(values, "select"),
                        text(values, "connection_direction"), strings(values, "connection_kinds"),
                        integer(values, "offset"), integer(values, "limit")));
    }

    private static CommandSpec getNodeTypeDetails() {
        List<CommandArgumentSpec> arguments = List.of(
                argument("type_id", "不带 geometry_node: 前缀的已注册节点短 type_id", true, null, stringSchema(1))
        );
        return querySpec("get_node_type_details",
                "创建节点前查询已注册节点类型的默认端口、Comment、写入能力和选项来源；动态端口创建后再查询实例详情",
                "get_node_type_details <type_id>", arguments, nodeTypeDetailsOutput(), false,
                (target, values) -> target.getNodeTypeDetails(text(values, "type_id")));
    }

    private static CommandSpec getNodeTypePortOptions() {
        List<CommandArgumentSpec> arguments = List.of(
                argument("type_id", "不带 geometry_node: 前缀的已注册节点短 type_id", true, null, stringSchema(1)),
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
                                                JsonElement defaultValue, JsonObject schema) {
        return argument(name, description, required, defaultValue, schema, null);
    }

    private static CommandArgumentSpec argument(String name, String description, boolean required,
                                                JsonElement defaultValue, JsonObject schema,
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
                property("type_id", stringSchema(1)), property("path", stringSchema(0))
        ), "type_id", "path");
        return object(properties(
                property("query", stringSchema(0)), property("path", stringSchema(0)),
                property("recursive", booleanSchema()), property("items", arraySchema(item, 0, MAX_LIMIT)),
                property("page", pageSchema())
        ), "query", "path", "recursive", "items", "page");
    }

    private static JsonObject nodeCatalogOutput() {
        return object(properties(
                property("path", stringSchema(0)), property("recursive", booleanSchema()),
                property("directories", arraySchema(stringSchema(1), 0, MAX_LIMIT)),
                property("type_ids", arraySchema(stringSchema(1), 0, MAX_LIMIT)),
                property("page", pageSchema())
        ), "path", "recursive", "directories", "type_ids", "page");
    }

    private static JsonObject graphNodeQueryOutput() {
        JsonObject identity = object(properties(
                property("node_id", stringSchema(1)), property("type_id", stringSchema(1)),
                property("display_name", stringSchema(0)), property("custom_name", stringSchema(0)),
                property("directory", stringSchema(0))
        ), "node_id", "type_id", "display_name", "custom_name", "directory");
        JsonObject value = object(properties(
                property("port_id", stringSchema(1)), property("type", stringSchema(1)),
                property("connected", booleanSchema()), property("has_stored_value", booleanSchema()),
                property("stored_value_json", stringSchema(0)), property("default_value_json", stringSchema(0)),
                property("effective_value_json", stringSchema(0))
        ), "port_id", "type", "connected", "has_stored_value", "stored_value_json",
                "default_value_json", "effective_value_json");
        JsonObject comments = object(properties(
                property("instance", stringSchema(0)), property("definition", stringSchema(0))
        ), "instance", "definition");
        JsonObject position = object(properties(
                property("x", numberSchema()), property("y", numberSchema())
        ), "x", "y");
        JsonObject metadata = object(properties(
                property("category", stringSchema(0)), property("custom_color", integerSchema(null, null)),
                property("has_custom_color", booleanSchema()), property("parent_frame", stringSchema(0)),
                property("incoming_count", integerSchema(0, null)), property("outgoing_count", integerSchema(0, null))
        ), "category", "custom_color", "has_custom_color", "parent_frame", "incoming_count", "outgoing_count");
        JsonObject item = object(properties(
                property("identity", identity), property("values", arraySchema(value, 0, MAX_LIMIT)),
                property("connections", arraySchema(connectionSchema(), 0, MAX_LIMIT)),
                property("comments", comments), property("ports", arraySchema(portSchema(), 0, MAX_LIMIT)),
                property("position", position), property("metadata", metadata)
        ));
        JsonObject filters = object(properties(
                property("node_ids", arraySchema(stringSchema(1), 0, MAX_LIMIT)),
                property("type_ids", arraySchema(stringSchema(1), 0, MAX_LIMIT)),
                property("directory", stringSchema(0)), property("query", stringSchema(0)),
                property("comment_filter", stringSchema(1)), property("connection_state", stringSchema(1)),
                property("connection_direction", stringSchema(1)),
                property("connection_kinds", arraySchema(stringSchema(1), 0, 3))
        ), "node_ids", "type_ids", "directory", "query", "comment_filter", "connection_state",
                "connection_direction", "connection_kinds");
        return object(properties(
                property("filters", filters), property("select", arraySchema(stringSchema(1), 1, 7)),
                property("items", arraySchema(item, 0, MAX_LIMIT)), property("page", pageSchema()),
                property("snapshot_node_count", integerSchema(0, null)),
                property("snapshot_connection_count", integerSchema(0, null)),
                property("session_id", stringSchema(1)), property("scope_id", stringSchema(1)),
                property("revision", integerSchema(0, null)), property("surface_ref", stringSchema(0))
        ), "filters", "select", "items", "page", "snapshot_node_count", "snapshot_connection_count");
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
                property("input", stringSchema(0)), property("count", integerSchema(0, null))
        ), "input", "count");
        return object(properties(
                property("filter_type_id", stringSchema(0)), property("filter_category", stringSchema(0)),
                property("group_by", stringSchema(1)), property("total_node_count", integerSchema(0, null)),
                property("node_count", integerSchema(0, null)),
                property("total_connection_count", integerSchema(0, null)),
                property("flow_connection_count", integerSchema(0, null)),
                property("data_connection_count", integerSchema(0, null)),
                property("behavior_connection_count", integerSchema(0, null)),
                property("induced_connection_count", integerSchema(0, null)),
                property("frame_count", integerSchema(0, null)),
                property("commented_node_count", integerSchema(0, null)),
                property("unconnected_node_count", integerSchema(0, null)),
                property("groups", arraySchema(group, 0, MAX_LIMIT)), property("page", pageSchema()),
                property("session_id", stringSchema(1)), property("scope_id", stringSchema(1)),
                property("revision", integerSchema(0, null)), property("surface_ref", stringSchema(0))
        ), "filter_type_id", "filter_category", "group_by", "total_node_count", "node_count",
                "total_connection_count", "flow_connection_count", "data_connection_count",
                "behavior_connection_count",
                "induced_connection_count", "frame_count", "commented_node_count", "unconnected_node_count",
                "groups", "page");
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

    private static JsonObject pageSchema() {
        return object(properties(
                property("offset", integerSchema(0, null)), property("limit", integerSchema(1, MAX_LIMIT)),
                property("total", integerSchema(0, null)), property("has_more", booleanSchema())
        ), "offset", "limit", "total", "has_more");
    }

    private static JsonObject portSchema() {
        return object(properties(
                property("port_id", stringSchema(0)), property("direction", stringSchema(1)),
                property("type", stringSchema(1)), property("display_name", stringSchema(0)),
                property("ui_hint", stringSchema(1)), property("hidden", booleanSchema()),
                property("live_expression", booleanSchema())
        ), "port_id", "direction", "type", "display_name", "ui_hint", "hidden", "live_expression");
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

    private static JsonObject enumArraySchema(int minItems, int maxItems, String... values) {
        return arraySchema(enumStringSchema(values), minItems, maxItems);
    }

    private static JsonArray jsonArray(String... values) {
        JsonArray array = new JsonArray();
        for (String value : values) array.add(value);
        return array;
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

    private static List<String> strings(JsonObject values, String name) {
        return values.getAsJsonArray(name).asList().stream().map(JsonElement::getAsString).toList();
    }

    private static int integer(JsonObject values, String name) { return values.get(name).getAsInt(); }

    @FunctionalInterface
    private interface QueryHandler {
        CommandResult execute(GraphQueryTarget target, JsonObject values);
    }
}
