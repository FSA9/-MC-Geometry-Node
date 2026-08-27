package com.mine.geometry_node.client.ai.mcp;

/** Cheap structural budget check performed before Gson creates an object tree. */
final class McpJsonLimits {
    private static final int MAX_DEPTH = 64;
    private static final int MAX_TOKENS = 100_000;
    private static final int MAX_STRING_CHARS = 262_144;

    private McpJsonLimits() {}

    static boolean accepts(String json) {
        int depth = 0;
        char[] containers = new char[MAX_DEPTH];
        int tokens = 0;
        int stringChars = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < json.length(); i++) {
            char value = json.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (value == '\\') {
                    if (++stringChars > MAX_STRING_CHARS) return false;
                    escaped = true;
                } else if (value == '"') {
                    inString = false;
                    stringChars = 0;
                    if (++tokens > MAX_TOKENS) return false;
                } else if (++stringChars > MAX_STRING_CHARS) {
                    return false;
                }
                continue;
            }
            if (value == '"') {
                inString = true;
            } else if (value == '{' || value == '[') {
                if (depth == MAX_DEPTH || ++tokens > MAX_TOKENS) return false;
                containers[depth++] = value;
            } else if (value == '}' || value == ']') {
                if (depth == 0) return false;
                char opening = containers[--depth];
                if ((value == '}' && opening != '{') || (value == ']' && opening != '[')) return false;
            } else if (value == ',' || value == ':') {
                if (++tokens > MAX_TOKENS) return false;
            }
        }
        return depth == 0 && !inString;
    }
}
