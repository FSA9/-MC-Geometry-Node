package com.mine.geometry_node.core.engine.graph;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.util.Objects;

/** Strict, shared parser for the required {@code graph_kind} document field. */
public final class GraphDocumentType {
    public static final String FIELD = "graph_kind";

    private GraphDocumentType() {}

    public static GraphType require(JsonObject document) {
        Objects.requireNonNull(document, "graph document");
        JsonElement value = document.get(FIELD);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new JsonParseException("Graph document requires a string graph_kind");
        }

        String typeId = GraphType.normalizeId(value.getAsString());
        if (typeId.isEmpty()) {
            throw new JsonParseException("Graph document graph_kind cannot be empty");
        }
        GraphType type = GraphTypeRegistry.INSTANCE.get(typeId);
        if (type == null) {
            throw new JsonParseException("Unknown graph_kind: " + typeId);
        }
        return type;
    }

    public static String requireId(JsonObject document) {
        return require(document).id();
    }
}
