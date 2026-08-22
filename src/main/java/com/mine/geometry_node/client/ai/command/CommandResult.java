package com.mine.geometry_node.client.ai.command;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.List;

/** Structured command output. UI adapters decide how to render it. */
public record CommandResult(
        boolean ok,
        String code,
        String message,
        JsonObject data,
        List<Diagnostic> diagnostics,
        Long revision,
        String changeId,
        ClientAction clientAction
) {
    public enum ClientAction { NONE, CLEAR_OUTPUT }

    public record Diagnostic(String code, String message, String path) {
        public Diagnostic {
            code = requireNonBlank(code, "diagnostic.code");
            message = requireNonBlank(message, "diagnostic.message");
            path = normalizeOptional(path);
        }
    }

    public CommandResult {
        code = requireNonBlank(code, "code");
        message = message == null ? "" : message;
        data = data == null ? new JsonObject() : data.deepCopy();
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        changeId = normalizeOptional(changeId);
        clientAction = clientAction == null ? ClientAction.NONE : clientAction;
    }

    @Override public JsonObject data() { return data.deepCopy(); }

    public static CommandResult success(String code, String message, JsonObject data) {
        return new CommandResult(true, code, message, data, List.of(), null, null, ClientAction.NONE);
    }

    public static CommandResult failure(String code, String message) {
        return new CommandResult(false, code, message, new JsonObject(), List.of(), null, null, ClientAction.NONE);
    }

    public static CommandResult clearOutput() {
        return new CommandResult(true, "CONSOLE_CLEARED", "", new JsonObject(), List.of(), null, null,
                ClientAction.CLEAR_OUTPUT);
    }

    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("ok", ok);
        json.addProperty("code", code);
        json.addProperty("message", message);
        json.add("data", data());
        json.add("diagnostics", com.google.gson.JsonParser.parseString(
                new com.google.gson.Gson().toJson(diagnostics)));
        if (revision != null) json.addProperty("revision", revision);
        if (changeId != null) json.addProperty("change_id", changeId);
        return json;
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " cannot be blank");
        return value;
    }

    private static String normalizeOptional(String value) { return value == null || value.isBlank() ? null : value; }
}
