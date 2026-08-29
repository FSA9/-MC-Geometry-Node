package com.mine.geometry_node.client.ai.mcp;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mine.geometry_node.client.ai.command.CommandResult;

/** Maps domain results to MCP tool results while retaining a model-readable error code. */
final class McpResultMapper {
    private static final Gson GSON = new Gson();

    private McpResultMapper() { }

    static JsonObject map(CommandResult commandResult) {
        JsonObject envelope = new JsonObject();
        envelope.addProperty("ok", commandResult.ok());
        envelope.addProperty("code", commandResult.code());
        envelope.addProperty("message", commandResult.message());
        envelope.add("data", commandResult.data());
        JsonArray diagnostics = new JsonArray();
        for (CommandResult.Diagnostic diagnostic : commandResult.diagnostics()) {
            JsonObject item = new JsonObject();
            item.addProperty("code", diagnostic.code());
            item.addProperty("message", diagnostic.message());
            item.addProperty("path", diagnostic.path());
            diagnostics.add(item);
        }
        envelope.add("diagnostics", diagnostics);

        JsonObject result = new JsonObject();
        result.addProperty("resultType", "complete");
        JsonArray content = new JsonArray();
        JsonObject text = new JsonObject();
        text.addProperty("type", "text");
        text.addProperty("text", GSON.toJson(envelope));
        content.add(text);
        result.add("content", content);
        result.add("structuredContent", envelope);
        result.addProperty("isError", !commandResult.ok());
        return result;
    }
}
