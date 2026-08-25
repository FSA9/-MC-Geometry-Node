package com.mine.geometry_node.client.ai.command;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mine.geometry_node.client.ai.protocol.ToolContract;

import java.util.Collection;
import java.util.List;

/** Authoritative built-in catalog shared by CLI and Agent adapters. */
public final class CommandCatalog {
    private static final CommandRegistry REGISTRY = createRegistry();

    private CommandCatalog() {}

    /** The authoritative production registry shared by CLI and Agent adapters. */
    public static CommandRegistry registry() { return REGISTRY; }

    static CommandRegistry createRegistry() {
        CommandRegistry registry = new CommandRegistry();
        registry.register(clear());
        registry.register(addNode());
        registry.register(delete());
        registry.register(connect());
        QueryCommandCatalog.registerInto(registry);
        registry.register(applyGraphPatch());
        registry.register(help(registry));
        return registry;
    }

    private static CommandSpec clear() {
        return spec("clear", "清空终端输出", "clear", List.of(), emptySchema(),
                ToolContract.CommandEffect.READ_ONLY, ToolContract.RiskLevel.READ_ONLY, false,
                CommandSpec.Exposure.CLI_ONLY, (context, arguments) -> CommandResult.clearOutput());
    }

    private static CommandSpec addNode() {
        List<CommandArgumentSpec> arguments = List.of(
                argument("type_id", "已注册的节点类型 ID", true, null, stringSchema(1), CommandCatalog::completeNodeTypes),
                argument("x", "节点 X 坐标", false, new JsonPrimitive(0), numberSchema(), null),
                argument("y", "节点 Y 坐标", false, new JsonPrimitive(0), numberSchema(), null),
                argument("node_id", "可选的节点实例 ID", false, null, stringSchema(1), null)
        );
        JsonObject output = properties(
                property("type_id", stringSchema(1)),
                property("node_id", stringSchema(1))
        );
        return spec("addnode", "向当前蓝图添加一个节点", "addnode <类型ID> [x] [y] [自定义ID]", arguments,
                CommandSpec.objectSchema(output, "type_id", "node_id"), ToolContract.CommandEffect.GRAPH_WRITE,
                ToolContract.RiskLevel.REVERSIBLE_EDIT, true, CommandSpec.Exposure.MODEL_VISIBLE,
                CommandCatalog::executeAddNode);
    }

    private static CommandSpec delete() {
        List<CommandArgumentSpec> arguments = List.of(
                argument("node_id", "要删除的节点实例 ID", true, null, stringSchema(1), CommandCatalog::completeNodeIds)
        );
        return spec("delete", "从当前蓝图删除一个节点", "delete <节点ID>", arguments,
                CommandSpec.objectSchema(properties(property("node_id", stringSchema(1))), "node_id"),
                ToolContract.CommandEffect.GRAPH_WRITE, ToolContract.RiskLevel.REVERSIBLE_EDIT, true,
                CommandSpec.Exposure.MODEL_VISIBLE, CommandCatalog::executeDelete);
    }

    private static CommandSpec connect() {
        List<CommandArgumentSpec> arguments = List.of(
                argument("output_node_id", "输出节点实例 ID", true, null, stringSchema(1), CommandCatalog::completeNodeIds),
                argument("output_port_id", "输出端口 ID", true, null, stringSchema(1), CommandCatalog::completeOutputPorts),
                argument("input_node_id", "输入节点实例 ID", true, null, stringSchema(1), CommandCatalog::completeNodeIds),
                argument("input_port_id", "输入端口 ID", true, null, stringSchema(1), CommandCatalog::completeInputPorts)
        );
        JsonObject output = properties(
                property("output_node_id", stringSchema(1)), property("output_port_id", stringSchema(1)),
                property("input_node_id", stringSchema(1)), property("input_port_id", stringSchema(1))
        );
        return spec("connect", "连接两个节点端口", "connect <输出节点ID> <输出端口> <输入节点ID> <输入端口>", arguments,
                CommandSpec.objectSchema(output, "output_node_id", "output_port_id", "input_node_id", "input_port_id"),
                ToolContract.CommandEffect.GRAPH_WRITE, ToolContract.RiskLevel.REVERSIBLE_EDIT, true,
                CommandSpec.Exposure.MODEL_VISIBLE, CommandCatalog::executeConnect);
    }

    private static CommandSpec help(CommandRegistry registry) {
        List<CommandArgumentSpec> arguments = List.of(
                argument("command", "可选的指令名称", false, null, stringSchema(1),
                        (prefix, parsed, context) -> registry.commands().stream().map(CommandSpec::name).toList())
        );
        JsonObject commandItem = CommandSpec.objectSchema(properties(
                property("name", stringSchema(1)), property("description", stringSchema(1)),
                property("usage", stringSchema(1))), "name", "description", "usage");
        JsonObject commands = new JsonObject();
        commands.addProperty("type", "array");
        commands.add("items", commandItem);
        JsonObject output = properties(property("commands", commands));
        return spec("help", "显示 Registry 中的指令帮助", "help [指令]", arguments,
                CommandSpec.objectSchema(output, "commands"), ToolContract.CommandEffect.READ_ONLY,
                ToolContract.RiskLevel.READ_ONLY, false, CommandSpec.Exposure.CLI_ONLY,
                (context, values) -> executeHelp(registry, values));
    }

    private static CommandSpec applyGraphPatch() {
        JsonObject patchSchema = stringSchema(2);
        patchSchema.addProperty("maxLength", 262_144);
        List<CommandArgumentSpec> arguments = List.of(argument("patch_json",
                "GraphPatch v1 JSON；session_id、scope_id、expected_revision 必须来自当前图上下文",
                true, null, patchSchema, null));
        JsonObject output = CommandSpec.objectSchema(properties(
                property("approval_id", stringSchema(1)), property("patch_hash", stringSchema(64)),
                property("change_id", stringSchema(1)), property("revision", integerSchema(0)),
                property("operation_count", integerSchema(1))),
                "approval_id", "patch_hash", "change_id", "revision", "operation_count");
        return new CommandSpec("apply_graph_patch", 1, List.of(),
                "提交 GraphPatch v1 JSON 字符串。根字段: protocol_version=1, session_id, scope_id, "
                        + "expected_revision, idempotency_key, operations。当前支持 add_node(alias,type_id,position,properties={}), "
                        + "move_node(node,position), set_port_value(port,value,expected_old_value), "
                        + "set_select_value(port,option_id,expected_old_value,option_context_token), "
                        + "connect(from,to)。position 必须是 {x:number,y:number}；node 使用 {id:string} 或此前 "
                        + "add_node 的 {alias:string}；port 使用 {node:{id|alias},port_id:string}。connect.from 必须是输出端口，"
                        + "connect.to 必须是输入端口；不要猜端口 ID。"
                        + "session/scope/revision 来自 get_graph_stats 或 get_graph_context；创建前 SELECT token 来自 "
                        + "get_node_type_port_options，已有实例 token 来自 get_port_options。"
                        + "服务端先 dry-run，GeometryNode 原生 Diff 批准后原子提交为一次 Undo；不要提供 approval_id",
                "apply_graph_patch <patch_json>", arguments, output,
                ToolContract.CommandEffect.GRAPH_WRITE, ToolContract.RiskLevel.REVERSIBLE_EDIT,
                true, true, CommandSpec.Exposure.MODEL_VISIBLE, (context, values) -> {
                    if (!(context.target() instanceof GraphPatchCommandTarget target)) {
                        return CommandResult.failure("GRAPH_PATCH_UNAVAILABLE", "当前目标不支持 GraphPatch 事务");
                    }
                    try {
                        String patchJson = values.get("patch_json").getAsString();
                        com.mine.geometry_node.client.ai.graph.GraphPatchJsonLimits.validate(patchJson);
                        var patch = com.mine.geometry_node.client.ai.graph.GraphPatchCodec.fromJson(
                                patchJson);
                        return target.applyGraphPatch(patch, context.cancellation());
                    } catch (com.google.gson.JsonParseException | IllegalArgumentException failure) {
                        return CommandResult.failure("GRAPH_PATCH_INVALID", "GraphPatch JSON 无效: " + failure.getMessage());
                    }
                });
    }

    private static CommandResult executeAddNode(CommandInvocationContext context, JsonObject arguments) {
        return graphTarget(context).addNode(arguments.get("type_id").getAsString(),
                arguments.get("x").getAsDouble(), arguments.get("y").getAsDouble(),
                arguments.has("node_id") ? arguments.get("node_id").getAsString() : null);
    }

    private static CommandResult executeDelete(CommandInvocationContext context, JsonObject arguments) {
        return graphTarget(context).deleteNode(arguments.get("node_id").getAsString());
    }

    private static CommandResult executeConnect(CommandInvocationContext context, JsonObject arguments) {
        return graphTarget(context).connect(arguments.get("output_node_id").getAsString(),
                arguments.get("output_port_id").getAsString(), arguments.get("input_node_id").getAsString(),
                arguments.get("input_port_id").getAsString());
    }

    private static CommandResult executeHelp(CommandRegistry registry, JsonObject arguments) {
        List<CommandSpec> specs;
        if (arguments.has("command")) {
            CommandSpec spec = registry.find(arguments.get("command").getAsString()).orElse(null);
            if (spec == null) return CommandResult.failure("COMMAND_NOT_FOUND", "帮助中找不到该指令");
            specs = List.of(spec);
        } else {
            specs = registry.commands();
        }
        JsonArray array = new JsonArray();
        StringBuilder message = new StringBuilder("可用指令:");
        for (CommandSpec spec : specs) {
            JsonObject item = new JsonObject();
            item.addProperty("name", spec.name());
            item.addProperty("description", spec.description());
            item.addProperty("usage", spec.usage());
            array.add(item);
            message.append("\n").append(spec.usage()).append(" - ").append(spec.description());
        }
        JsonObject data = new JsonObject();
        data.add("commands", array);
        return CommandResult.success("HELP", message.toString(), data);
    }

    private static Collection<String> completeNodeTypes(String prefix, JsonObject parsed,
                                                        CommandInvocationContext context) {
        GraphCommandTarget target = optionalGraphTarget(context);
        return target == null ? List.of() : target.registeredNodeTypeIds();
    }

    private static Collection<String> completeNodeIds(String prefix, JsonObject parsed,
                                                      CommandInvocationContext context) {
        GraphCommandTarget target = optionalGraphTarget(context);
        return target == null ? List.of() : target.nodeIds();
    }

    private static Collection<String> completeOutputPorts(String prefix, JsonObject parsed,
                                                          CommandInvocationContext context) {
        return completePorts(context, parsed, "output_node_id", GraphCommandTarget.PortDirection.OUTPUT);
    }

    private static Collection<String> completeInputPorts(String prefix, JsonObject parsed,
                                                         CommandInvocationContext context) {
        return completePorts(context, parsed, "input_node_id", GraphCommandTarget.PortDirection.INPUT);
    }

    private static Collection<String> completePorts(CommandInvocationContext context, JsonObject parsed,
                                                    String nodeArgument, GraphCommandTarget.PortDirection direction) {
        GraphCommandTarget target = optionalGraphTarget(context);
        if (target == null || !parsed.has(nodeArgument)) return List.of();
        return target.portIds(parsed.get(nodeArgument).getAsString(), direction);
    }

    private static GraphCommandTarget graphTarget(CommandInvocationContext context) {
        GraphCommandTarget target = optionalGraphTarget(context);
        if (target == null) throw new IllegalStateException("graph command target is required");
        return target;
    }

    private static GraphCommandTarget optionalGraphTarget(CommandInvocationContext context) {
        return context.target() instanceof GraphCommandTarget target ? target : null;
    }

    private static CommandArgumentSpec argument(String name, String description, boolean required,
                                                JsonPrimitive defaultValue, JsonObject schema,
                                                CommandArgumentSpec.CompletionProvider completionProvider) {
        return new CommandArgumentSpec(name, description, required, defaultValue, schema, completionProvider);
    }

    private static CommandSpec spec(String name, String description, String usage,
                                    List<CommandArgumentSpec> arguments, JsonObject outputSchema,
                                    ToolContract.CommandEffect effect, ToolContract.RiskLevel risk,
                                    boolean requiresGraph, CommandSpec.Exposure exposure,
                                    CommandSpec.CommandHandler handler) {
        return new CommandSpec(name, 1, List.of(), description, usage, arguments, outputSchema, effect, risk,
                requiresGraph, false, exposure, handler);
    }

    private static JsonObject stringSchema(int minLength) {
        JsonObject schema = CommandSpec.scalarSchema("string");
        schema.addProperty("minLength", minLength);
        return schema;
    }

    private static JsonObject numberSchema() { return CommandSpec.scalarSchema("number"); }
    private static JsonObject integerSchema(int minimum) {
        JsonObject schema = CommandSpec.scalarSchema("integer");
        schema.addProperty("minimum", minimum);
        return schema;
    }
    private static JsonObject emptySchema() { return CommandSpec.objectSchema(new JsonObject()); }

    private static java.util.Map.Entry<String, JsonObject> property(String name, JsonObject schema) {
        return java.util.Map.entry(name, schema);
    }

    @SafeVarargs
    private static JsonObject properties(java.util.Map.Entry<String, JsonObject>... values) {
        JsonObject properties = new JsonObject();
        for (var value : values) properties.add(value.getKey(), value.getValue());
        return properties;
    }
}
