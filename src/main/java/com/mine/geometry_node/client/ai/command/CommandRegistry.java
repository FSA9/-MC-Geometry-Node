package com.mine.geometry_node.client.ai.command;

import com.google.gson.JsonObject;
import com.mine.geometry_node.client.ai.protocol.ToolContract;
import com.mine.geometry_node.client.ai.protocol.ToolSchemaValidator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Ordered command catalog shared by CLI and model tool adapters. */
public final class CommandRegistry {
    public record Suggestion(String displayText, String completedInput) {}

    private final Map<String, CommandSpec> commands = new LinkedHashMap<>();
    private final Map<String, CommandSpec> lookup = new LinkedHashMap<>();
    private final List<ToolContract.ToolSpec> modelTools = new ArrayList<>();

    public synchronized void register(CommandSpec spec) {
        Objects.requireNonNull(spec, "spec");
        List<String> names = new ArrayList<>();
        names.add(spec.name());
        names.addAll(spec.aliases());
        for (String name : names) {
            if (lookup.containsKey(name)) throw new IllegalArgumentException("command name or alias already registered: " + name);
        }
        ToolContract.ToolSpec modelTool = spec.exposure() == CommandSpec.Exposure.MODEL_VISIBLE
                ? spec.toToolSpec() : null;
        commands.put(spec.name(), spec);
        for (String name : names) lookup.put(name, spec);
        if (modelTool != null) modelTools.add(modelTool);
    }

    public synchronized Optional<CommandSpec> find(String name) {
        if (name == null) return Optional.empty();
        return Optional.ofNullable(lookup.get(name.toLowerCase(Locale.ROOT)));
    }

    public synchronized List<CommandSpec> commands() { return List.copyOf(commands.values()); }

    public synchronized List<ToolContract.ToolSpec> modelTools() {
        return List.copyOf(modelTools);
    }

    public CommandResult execute(CommandSpec spec, JsonObject arguments, CommandInvocationContext context) {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(arguments, "arguments");
        Objects.requireNonNull(context, "context");
        synchronized (this) {
            if (commands.get(spec.name()) != spec) {
                return CommandResult.failure("COMMAND_NOT_REGISTERED", "指令未在当前 Registry 注册");
            }
        }
        if (context.origin() == CommandInvocationContext.CommandOrigin.AGENT
                && spec.exposure() == CommandSpec.Exposure.CLI_ONLY) {
            return CommandResult.failure("COMMAND_NOT_AVAILABLE", "该指令不允许由 Agent 调用");
        }
        if (context.cancellation().isCancelled()) return CommandResult.failure("CANCELLED", "指令已取消");
        if (spec.requiresGraph() && !context.hasGraph()) {
            return CommandResult.failure("GRAPH_SESSION_REQUIRED", "执行失败: 当前没有打开且活跃的蓝图会话");
        }
        JsonObject normalizedArguments = applyDefaults(spec, arguments);
        List<ToolSchemaValidator.Violation> violations = ToolSchemaValidator.validateArguments(
                spec.inputSchema(), normalizedArguments);
        if (!violations.isEmpty()) {
            List<CommandResult.Diagnostic> diagnostics = violations.stream()
                    .map(value -> new CommandResult.Diagnostic(value.code(), value.message(), value.path()))
                    .toList();
            return new CommandResult(false, "ARGUMENT_INVALID", "指令参数校验失败", new JsonObject(), diagnostics,
                    null, null, CommandResult.ClientAction.NONE);
        }
        try {
            CommandResult result = spec.handler().execute(context, normalizedArguments.deepCopy());
            Objects.requireNonNull(result, "command handler result");
            if (result.ok()) {
                List<ToolSchemaValidator.Violation> outputViolations = ToolSchemaValidator.validateArguments(
                        spec.outputSchema(), result.data());
                if (!outputViolations.isEmpty()) {
                    return CommandResult.failure("COMMAND_RESULT_INVALID", "指令返回了不符合契约的数据");
                }
            }
            return result;
        } catch (RuntimeException exception) {
            return CommandResult.failure("COMMAND_INTERNAL_ERROR", "指令执行时发生内部错误");
        }
    }

    private static JsonObject applyDefaults(CommandSpec spec, JsonObject arguments) {
        JsonObject normalized = arguments.deepCopy();
        for (CommandArgumentSpec argument : spec.arguments()) {
            if (!normalized.has(argument.name()) && argument.defaultValue() != null) {
                normalized.add(argument.name(), argument.defaultValue());
            }
        }
        return normalized;
    }

    public List<Suggestion> suggest(String input, CommandInvocationContext context) {
        String line = input == null ? "" : input;
        CliCommandParser.PartialLine partial = CliCommandParser.parsePartial(line);
        List<String> tokens = partial.tokens();
        if (tokens.isEmpty() || (tokens.size() == 1 && !partial.trailingSeparator())) {
            String prefix = tokens.isEmpty() ? "" : tokens.getFirst().toLowerCase(Locale.ROOT);
            return lookupNames().stream().filter(name -> name.startsWith(prefix)).distinct()
                    .map(name -> new Suggestion(name, name + " ")).toList();
        }

        CommandSpec spec = find(tokens.getFirst()).orElse(null);
        if (spec == null) return List.of();
        int argumentIndex = partial.trailingSeparator() ? tokens.size() - 1 : tokens.size() - 2;
        if (argumentIndex < 0 || argumentIndex >= spec.arguments().size()) return List.of();
        String prefix = partial.trailingSeparator() ? "" : partial.currentToken();
        JsonObject parsed = parseCompletedArguments(spec, tokens, argumentIndex);
        Collection<String> values = spec.arguments().get(argumentIndex).completionProvider()
                .complete(prefix, parsed, context);
        if (values == null) return List.of();
        String before = line.substring(0, partial.trailingSeparator() ? line.length() : partial.replacementStart());
        return values.stream().filter(Objects::nonNull).filter(value -> !value.isBlank())
                .filter(value -> value.toLowerCase(Locale.ROOT).contains(prefix.toLowerCase(Locale.ROOT)))
                .sorted(String.CASE_INSENSITIVE_ORDER).distinct()
                .map(value -> new Suggestion(value, before + quoteIfNeeded(value) + " ")).toList();
    }

    private synchronized List<String> lookupNames() {
        return List.copyOf(lookup.keySet());
    }

    private static JsonObject parseCompletedArguments(CommandSpec spec, List<String> tokens, int argumentIndex) {
        JsonObject parsed = new JsonObject();
        for (int index = 0; index < argumentIndex && index + 1 < tokens.size(); index++) {
            CommandArgumentSpec argument = spec.arguments().get(index);
            try {
                parsed.add(argument.name(), CliCommandParser.convertArgument(tokens.get(index + 1), argument, 0));
            } catch (CliCommandParser.ParseException ignored) {
                return parsed;
            }
        }
        return parsed;
    }

    private static String quoteIfNeeded(String value) {
        if (value.chars().noneMatch(Character::isWhitespace) && value.indexOf('"') < 0 && value.indexOf('\\') < 0) return value;
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

}
