package com.mine.geometry_node.client.ai.graph;

/** Cheap pre-parse limits for the string-encoded GraphPatch MCP argument. */
public final class GraphPatchJsonLimits {
    public static final int MAX_DEPTH = 64;
    public static final int MAX_STRING_LENGTH = 65_536;

    private GraphPatchJsonLimits() {}

    public static void validate(String json) {
        if (json == null) throw new IllegalArgumentException("patch_json cannot be null");
        int depth = 0;
        int stringLength = 0;
        boolean quoted = false;
        boolean escaped = false;
        for (int index = 0; index < json.length(); index++) {
            char value = json.charAt(index);
            if (quoted) {
                if (escaped) {
                    escaped = false;
                } else if (value == '\\') {
                    escaped = true;
                } else if (value == '"') {
                    quoted = false;
                    stringLength = 0;
                } else if (++stringLength > MAX_STRING_LENGTH) {
                    throw new IllegalArgumentException("GraphPatch string value is too long");
                }
                continue;
            }
            if (value == '"') {
                quoted = true;
                stringLength = 0;
            } else if (value == '{' || value == '[') {
                if (++depth > MAX_DEPTH) throw new IllegalArgumentException("GraphPatch JSON is too deeply nested");
            } else if (value == '}' || value == ']') {
                if (--depth < 0) throw new IllegalArgumentException("GraphPatch JSON delimiters are unbalanced");
            }
        }
        if (quoted || depth != 0) throw new IllegalArgumentException("GraphPatch JSON is incomplete");
    }
}
