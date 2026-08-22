package com.mine.geometry_node.client.ai.command;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mine.geometry_node.client.ai.protocol.ToolSchemaValidator;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Deterministic positional CLI parser with quoting, escaping, and typed conversion. */
public final class CliCommandParser {
    private CliCommandParser() {}

    public record ParsedInvocation(CommandSpec spec, JsonObject arguments) {
        public ParsedInvocation { arguments = arguments.deepCopy(); }
        @Override public JsonObject arguments() { return arguments.deepCopy(); }
    }

    public record PartialLine(List<String> tokens, String currentToken, int replacementStart,
                              boolean trailingSeparator) {
        public PartialLine { tokens = List.copyOf(tokens); }
    }

    public static final class ParseException extends Exception {
        private final String code;
        private final int position;

        public ParseException(String code, String message, int position) {
            super(message);
            this.code = code;
            this.position = position;
        }

        public String code() { return code; }
        public int position() { return position; }
    }

    public static ParsedInvocation parse(String line, CommandRegistry registry) throws ParseException {
        List<String> tokens = tokenize(line, false).tokens();
        if (tokens.isEmpty()) throw new ParseException("CLI_EMPTY_COMMAND", "指令不能为空", 0);
        CommandSpec spec = registry.find(tokens.getFirst()).orElseThrow(() ->
                new ParseException("CLI_UNKNOWN_COMMAND", "未知的指令 '" + tokens.getFirst() + "'", 0));
        List<String> rawArguments = tokens.subList(1, tokens.size());
        if (rawArguments.size() > spec.arguments().size()) {
            throw new ParseException("CLI_TOO_MANY_ARGUMENTS", "参数过多。用法: " + spec.usage(), line.length());
        }

        JsonObject arguments = new JsonObject();
        for (int index = 0; index < spec.arguments().size(); index++) {
            CommandArgumentSpec argument = spec.arguments().get(index);
            if (index < rawArguments.size()) {
                arguments.add(argument.name(), convertArgument(rawArguments.get(index), argument, line.length()));
            } else if (argument.defaultValue() != null) {
                arguments.add(argument.name(), argument.defaultValue());
            } else if (argument.required()) {
                throw new ParseException("CLI_MISSING_ARGUMENT", "缺少参数。用法: " + spec.usage(), line.length());
            }
        }

        List<ToolSchemaValidator.Violation> violations = ToolSchemaValidator.validateArguments(spec.inputSchema(), arguments);
        if (!violations.isEmpty()) {
            throw new ParseException("CLI_ARGUMENT_INVALID", violations.getFirst().message(), line.length());
        }
        return new ParsedInvocation(spec, arguments);
    }

    public static PartialLine parsePartial(String line) {
        try {
            return tokenize(line == null ? "" : line, true);
        } catch (ParseException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static PartialLine tokenize(String line, boolean partial) throws ParseException {
        if (line == null) line = "";
        List<String> tokens = new ArrayList<>();
        StringBuilder token = new StringBuilder();
        boolean tokenStarted = false;
        boolean escaping = false;
        char quote = 0;
        int tokenStart = 0;
        boolean trailingSeparator = false;

        for (int index = 0; index < line.length(); index++) {
            char character = line.charAt(index);
            if (escaping) {
                token.append(character);
                escaping = false;
                trailingSeparator = false;
                continue;
            }
            if (character == '\\') {
                if (!tokenStarted) tokenStart = index;
                tokenStarted = true;
                escaping = true;
                trailingSeparator = false;
                continue;
            }
            if (quote != 0) {
                if (character == quote) quote = 0;
                else token.append(character);
                trailingSeparator = false;
                continue;
            }
            if (character == '\'' || character == '"') {
                if (!tokenStarted) tokenStart = index;
                tokenStarted = true;
                quote = character;
                trailingSeparator = false;
                continue;
            }
            if (Character.isWhitespace(character)) {
                if (tokenStarted) {
                    tokens.add(token.toString());
                    token.setLength(0);
                    tokenStarted = false;
                }
                trailingSeparator = true;
                continue;
            }
            if (!tokenStarted) tokenStart = index;
            tokenStarted = true;
            token.append(character);
            trailingSeparator = false;
        }

        if (!partial && escaping) throw new ParseException("CLI_DANGLING_ESCAPE", "指令末尾存在悬空转义符", line.length() - 1);
        if (!partial && quote != 0) throw new ParseException("CLI_UNCLOSED_QUOTE", "引号未闭合", line.length());
        if (tokenStarted) tokens.add(token.toString());
        String current = tokenStarted ? token.toString() : "";
        return new PartialLine(tokens, current, tokenStarted ? tokenStart : line.length(), trailingSeparator);
    }

    static JsonElement convertArgument(String raw, CommandArgumentSpec argument, int errorPosition) throws ParseException {
        String type = argument.schema().get("type").getAsString();
        try {
            return switch (type) {
                case "string" -> new JsonPrimitive(raw);
                case "number" -> {
                    double value = Double.parseDouble(raw);
                    if (!Double.isFinite(value)) throw new NumberFormatException("non-finite");
                    yield new JsonPrimitive(value);
                }
                case "integer" -> new JsonPrimitive(Long.parseLong(raw));
                case "boolean" -> {
                    String value = raw.toLowerCase(Locale.ROOT);
                    if (!value.equals("true") && !value.equals("false")) throw new IllegalArgumentException("boolean");
                    yield new JsonPrimitive(Boolean.parseBoolean(value));
                }
                default -> throw new IllegalStateException("unsupported argument type: " + type);
            };
        } catch (IllegalArgumentException exception) {
            throw new ParseException("CLI_TYPE_MISMATCH", "参数 '" + argument.name() + "' 类型错误", errorPosition);
        }
    }
}
