package com.mine.geometry_node.client.ai.graph;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Explicit discriminator codec; it avoids relying on Gson sealed-interface reflection. */
public final class GraphPatchCodec {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private GraphPatchCodec() {}

    public static String toJson(GraphPatch patch) { return GSON.toJson(toJsonTree(patch)); }

    public static JsonObject toJsonTree(GraphPatch patch) {
        JsonObject root = new JsonObject();
        root.addProperty("session_id", patch.session().id());
        root.addProperty("scope_id", patch.scope().id());
        root.addProperty("expected_revision", patch.expectedRevision().value());
        root.addProperty("idempotency_key", patch.idempotencyKey());
        JsonArray operations = new JsonArray();
        for (GraphPatch.Operation operation : patch.operations()) operations.add(writeOperation(operation));
        root.add("operations", operations);
        return root;
    }

    public static GraphPatch fromJson(String json) {
        try {
            return fromJsonTree(JsonParser.parseString(json).getAsJsonObject());
        } catch (IllegalStateException | UnsupportedOperationException | IllegalArgumentException exception) {
            throw new JsonParseException("invalid GraphPatch JSON", exception);
        }
    }

    public static GraphPatch fromJsonTree(JsonObject root) {
        requireOnly(root, "session_id", "scope_id", "expected_revision", "idempotency_key", "operations");
        JsonArray array = array(root, "operations");
        List<GraphPatch.Operation> operations = new ArrayList<>(array.size());
        for (int index = 0; index < array.size(); index++) {
            JsonElement element = array.get(index);
            if (!element.isJsonObject()) throw new JsonParseException("operations[" + index + "] must be an object");
            operations.add(readOperation(element.getAsJsonObject()));
        }
        return new GraphPatch(
                new GraphPatch.SessionRef(string(root, "session_id")),
                new GraphPatch.ScopeRef(string(root, "scope_id")),
                new GraphPatch.GraphRevision(longInteger(root, "expected_revision")),
                string(root, "idempotency_key"), operations);
    }

    private static JsonObject writeOperation(GraphPatch.Operation operation) {
        JsonObject json = new JsonObject();
        json.addProperty("op", operation.op());
        switch (operation) {
            case GraphPatch.AddNode value -> {
                json.addProperty("alias", value.alias()); json.addProperty("type_id", value.typeId());
                json.add("position", position(value.position())); json.add("properties", GSON.toJsonTree(value.properties()));
            }
            case GraphPatch.RemoveNode value -> json.add("node", node(value.node()));
            case GraphPatch.MoveNode value -> { json.add("node", node(value.node())); json.add("position", position(value.position())); }
            case GraphPatch.Connect value -> { json.add("from", port(value.from())); json.add("to", port(value.to())); }
            case GraphPatch.Disconnect value -> { json.add("from", port(value.from())); json.add("to", port(value.to())); }
            case GraphPatch.SetPortValue value -> {
                json.add("port", port(value.port())); json.add("value", value.value());
                if (value.expectedOldValue() != null) json.add("expected_old_value", value.expectedOldValue());
            }
            case GraphPatch.SetSelectValue value -> {
                json.add("port", port(value.port())); json.addProperty("option_id", value.optionId());
                if (value.expectedOldValue() != null) json.addProperty("expected_old_value", value.expectedOldValue());
                if (value.optionContextToken() != null) json.addProperty("option_context_token", value.optionContextToken());
            }
            case GraphPatch.SetNodeProperty value -> { json.add("node", node(value.node())); json.addProperty("property", value.property()); json.add("value", value.value()); }
            case GraphPatch.AddFrame value -> {
                json.addProperty("alias", value.alias()); json.addProperty("title", value.title()); json.add("position", position(value.position()));
                json.addProperty("width", value.width()); json.addProperty("height", value.height());
            }
            case GraphPatch.RemoveFrame value -> json.add("frame", frame(value.frame()));
            case GraphPatch.SetFrameProperty value -> { json.add("frame", frame(value.frame())); json.addProperty("property", value.property()); json.add("value", value.value()); }
            case GraphPatch.AddDynamicBranch value -> { json.add("node", node(value.node())); json.addProperty("direction", value.direction()); json.addProperty("alias", value.alias()); }
            case GraphPatch.RemoveDynamicBranch value -> json.add("branch", branch(value.branch()));
            case GraphPatch.AddGroupVirtualPort value -> {
                json.add("node", node(value.node())); json.addProperty("direction", value.direction());
                json.addProperty("port_type", value.portType()); json.addProperty("alias", value.alias());
            }
            case GraphPatch.RemoveGroupVirtualPort value -> json.add("port", port(value.port()));
            case GraphPatch.RenamePort value -> {
                json.add("port", port(value.port())); json.addProperty("direction", value.direction());
                json.addProperty("name", value.name());
            }
        }
        return json;
    }

    private static GraphPatch.Operation readOperation(JsonObject json) {
        String op = string(json, "op");
        return switch (op) {
            case "add_node" -> {
                requireOnly(json, "op", "alias", "type_id", "position", "properties");
                yield new GraphPatch.AddNode(string(json, "alias"), string(json, "type_id"), position(json, "position"), jsonMap(json, "properties"));
            }
            case "remove_node" -> {
                requireOnly(json, "op", "node"); yield new GraphPatch.RemoveNode(node(json, "node"));
            }
            case "move_node" -> {
                requireOnly(json, "op", "node", "position"); yield new GraphPatch.MoveNode(node(json, "node"), position(json, "position"));
            }
            case "connect" -> {
                requireOnly(json, "op", "from", "to"); yield new GraphPatch.Connect(port(json, "from"), port(json, "to"));
            }
            case "disconnect" -> {
                requireOnly(json, "op", "from", "to"); yield new GraphPatch.Disconnect(port(json, "from"), port(json, "to"));
            }
            case "set_port_value" -> {
                requireOnly(json, "op", "port", "value", "expected_old_value");
                yield new GraphPatch.SetPortValue(port(json, "port"), present(json, "value"), optional(json, "expected_old_value"));
            }
            case "set_select_value" -> {
                requireOnly(json, "op", "port", "option_id", "expected_old_value", "option_context_token");
                yield new GraphPatch.SetSelectValue(port(json, "port"), string(json, "option_id"),
                        optionalString(json, "expected_old_value"), optionalString(json, "option_context_token"));
            }
            case "set_node_property" -> {
                requireOnly(json, "op", "node", "property", "value");
                yield new GraphPatch.SetNodeProperty(node(json, "node"), string(json, "property"), present(json, "value"));
            }
            case "add_frame" -> {
                requireOnly(json, "op", "alias", "title", "position", "width", "height");
                yield new GraphPatch.AddFrame(string(json, "alias"), string(json, "title"), position(json, "position"), number(json, "width"), number(json, "height"));
            }
            case "remove_frame" -> {
                requireOnly(json, "op", "frame");
                yield new GraphPatch.RemoveFrame(frame(json, "frame"));
            }
            case "set_frame_property" -> {
                requireOnly(json, "op", "frame", "property", "value");
                yield new GraphPatch.SetFrameProperty(frame(json, "frame"), string(json, "property"), present(json, "value"));
            }
            case "add_dynamic_branch" -> {
                requireOnly(json, "op", "node", "direction", "alias");
                yield new GraphPatch.AddDynamicBranch(node(json, "node"), string(json, "direction"), string(json, "alias"));
            }
            case "remove_dynamic_branch" -> {
                requireOnly(json, "op", "branch");
                yield new GraphPatch.RemoveDynamicBranch(branch(json, "branch"));
            }
            case "add_group_virtual_port" -> {
                requireOnly(json, "op", "node", "direction", "port_type", "alias");
                yield new GraphPatch.AddGroupVirtualPort(node(json, "node"), string(json, "direction"), string(json, "port_type"), string(json, "alias"));
            }
            case "remove_group_virtual_port" -> {
                requireOnly(json, "op", "port");
                yield new GraphPatch.RemoveGroupVirtualPort(port(json, "port"));
            }
            case "rename_port" -> {
                requireOnly(json, "op", "port", "direction", "name");
                yield new GraphPatch.RenamePort(port(json, "port"), string(json, "direction"), string(json, "name"));
            }
            default -> throw new JsonParseException("unknown GraphPatch operation: " + op);
        };
    }

    private static JsonObject node(GraphPatch.NodeRef ref) {
        JsonObject json = new JsonObject();
        if (ref.id() != null) json.addProperty("id", ref.id()); else json.addProperty("alias", ref.alias());
        return json;
    }

    private static GraphPatch.NodeRef node(JsonObject parent, String name) {
        JsonObject json = object(parent, name);
        requireOnly(json, "id", "alias");
        return new GraphPatch.NodeRef(optionalString(json, "id"), optionalString(json, "alias"));
    }

    private static JsonObject port(GraphPatch.PortRef ref) {
        JsonObject json = new JsonObject();
        if (ref.alias() != null) {
            json.addProperty("alias", ref.alias());
        } else {
            json.add("node", node(ref.node()));
            json.addProperty("port_id", ref.portId());
        }
        return json;
    }

    private static JsonObject frame(GraphPatch.FrameRef ref) {
        JsonObject json = new JsonObject();
        if (ref.id() != null) json.addProperty("id", ref.id()); else json.addProperty("alias", ref.alias());
        return json;
    }

    private static GraphPatch.FrameRef frame(JsonObject parent, String name) {
        JsonObject json = object(parent, name);
        requireOnly(json, "id", "alias");
        return new GraphPatch.FrameRef(optionalString(json, "id"), optionalString(json, "alias"));
    }

    private static JsonObject branch(GraphPatch.BranchRef ref) {
        JsonObject json = new JsonObject();
        if (ref.alias() != null) {
            json.addProperty("alias", ref.alias());
        } else {
            json.add("node", node(ref.node()));
            json.addProperty("direction", ref.direction());
            json.addProperty("index", ref.index());
        }
        return json;
    }

    private static GraphPatch.BranchRef branch(JsonObject parent, String name) {
        JsonObject json = object(parent, name);
        requireOnly(json, "node", "direction", "index", "alias");
        String alias = optionalString(json, "alias");
        if (alias != null) {
            if (json.has("node") || json.has("direction") || json.has("index")) {
                throw new JsonParseException("branch alias cannot be combined with node, direction, or index");
            }
            return GraphPatch.BranchRef.alias(alias);
        }
        return new GraphPatch.BranchRef(node(json, "node"), string(json, "direction"),
                integer(json, "index"), null);
    }

    private static GraphPatch.PortRef port(JsonObject parent, String name) {
        JsonObject json = object(parent, name);
        requireOnly(json, "node", "port_id", "alias");
        String alias = optionalString(json, "alias");
        if (alias != null) {
            if (json.has("node") || json.has("port_id")) {
                throw new JsonParseException("port alias cannot be combined with node or port_id");
            }
            return GraphPatch.PortRef.alias(alias);
        }
        return new GraphPatch.PortRef(node(json, "node"), string(json, "port_id"));
    }

    private static JsonObject position(GraphPatch.Position value) {
        JsonObject json = new JsonObject(); json.addProperty("x", value.x()); json.addProperty("y", value.y()); return json;
    }

    private static GraphPatch.Position position(JsonObject parent, String name) {
        JsonObject json = object(parent, name);
        requireOnly(json, "x", "y");
        return new GraphPatch.Position(number(json, "x"), number(json, "y"));
    }

    private static Map<String, JsonElement> jsonMap(JsonObject parent, String name) {
        if (!parent.has(name)) return Map.of();
        JsonObject source = object(parent, name);
        Map<String, JsonElement> values = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : source.entrySet()) {
            values.put(entry.getKey(), entry.getValue());
        }
        return values;
    }

    private static JsonElement required(JsonObject object, String name) {
        JsonElement value = object.get(name);
        if (value == null || value.isJsonNull()) throw new JsonParseException("missing field: " + name);
        return value;
    }

    private static JsonElement present(JsonObject object, String name) {
        if (!object.has(name)) throw new JsonParseException("missing field: " + name);
        return object.get(name);
    }

    private static JsonObject object(JsonObject parent, String name) {
        JsonElement value = required(parent, name);
        if (!value.isJsonObject()) throw new JsonParseException(name + " must be an object");
        return value.getAsJsonObject();
    }

    private static JsonArray array(JsonObject parent, String name) {
        JsonElement value = required(parent, name);
        if (!value.isJsonArray()) throw new JsonParseException(name + " must be an array");
        return value.getAsJsonArray();
    }

    private static JsonElement optional(JsonObject object, String name) {
        return object.get(name);
    }

    private static String string(JsonObject object, String name) {
        JsonElement value = required(object, name);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new JsonParseException(name + " must be a string");
        return value.getAsString();
    }

    private static String optionalString(JsonObject object, String name) {
        JsonElement value = object.get(name);
        if (value == null || value.isJsonNull()) return null;
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new JsonParseException(name + " must be a string");
        }
        return value.getAsString();
    }

    private static double number(JsonObject object, String name) {
        JsonElement value = required(object, name);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber() || !Double.isFinite(value.getAsDouble())) {
            throw new JsonParseException(name + " must be a finite number");
        }
        return value.getAsDouble();
    }

    private static int integer(JsonObject object, String name) {
        long value = longInteger(object, name);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) throw new JsonParseException(name + " is out of integer range");
        return (int) value;
    }

    private static long longInteger(JsonObject object, String name) {
        JsonElement value = required(object, name);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new JsonParseException(name + " must be an integer");
        }
        try {
            return value.getAsBigDecimal().longValueExact();
        } catch (ArithmeticException exception) {
            throw new JsonParseException(name + " must be an in-range integer", exception);
        }
    }

    private static void requireOnly(JsonObject object, String... allowed) {
        keys: for (String key : object.keySet()) {
            for (String name : allowed) {
                if (name.equals(key)) continue keys;
            }
            throw new JsonParseException("unknown field: " + key);
        }
    }
}
