package com.mine.geometry_node.client.input.binding;

import org.lwjgl.glfw.GLFW;

import java.util.Locale;
import java.util.Map;

public final class KeyBinding {
    private static final Map<String, KeyDef> KEY_CODES = createKeyCodes();

    public final int keyCode;
    public final boolean ctrl;
    public final boolean shift;
    public final boolean alt;
    public final boolean superKey;
    public final String text;

    private KeyBinding(int keyCode, boolean ctrl, boolean shift, boolean alt, boolean superKey, String text) {
        this.keyCode = keyCode;
        this.ctrl = ctrl;
        this.shift = shift;
        this.alt = alt;
        this.superKey = superKey;
        this.text = text;
    }

    public static KeyBinding parse(String value) {
        if (value == null || value.isBlank()) return null;

        boolean ctrl = false;
        boolean shift = false;
        boolean alt = false;
        boolean superKey = false;
        KeyDef keyDef = null;
        String keyName = null;

        String[] tokens = value.trim().toUpperCase(Locale.ROOT).split("\\+");
        for (String token : tokens) {
            String part = normalizeToken(token);
            if (part.isEmpty()) return null;

            switch (part) {
                case "CTRL", "CONTROL" -> ctrl = true;
                case "SHIFT" -> shift = true;
                case "ALT", "OPTION" -> alt = true;
                case "SUPER", "META", "CMD", "COMMAND" -> superKey = true;
                default -> {
                    if (keyDef != null) return null;
                    keyDef = KEY_CODES.get(part);
                    if (keyDef == null) return null;
                    keyName = keyDef.name;
                }
            }
        }

        if (keyDef == null) return null;
        return new KeyBinding(keyDef.keyCode, ctrl, shift, alt, superKey, canonicalText(ctrl, shift, alt, superKey, keyName));
    }

    /** Creates a binding from GLFW key codes without depending on a UI event type. */
    public static KeyBinding fromKey(int keyCode, boolean ctrl, boolean shift, boolean alt, boolean superKey) {
        KeyDef keyDef = KEY_CODES.values().stream()
                .filter(candidate -> candidate.keyCode == keyCode)
                .findFirst()
                .orElse(null);
        if (keyDef == null) return null;
        return new KeyBinding(keyDef.keyCode, ctrl, shift, alt, superKey,
                canonicalText(ctrl, shift, alt, superKey, keyDef.name));
    }

    public boolean matches(int keyCode, boolean ctrl, boolean shift, boolean alt, boolean superKey) {
        return this.keyCode == keyCode && this.ctrl == ctrl && this.shift == shift
                && this.alt == alt && this.superKey == superKey;
    }

    private static String canonicalText(boolean ctrl, boolean shift, boolean alt, boolean superKey, String keyName) {
        StringBuilder builder = new StringBuilder();
        appendPart(builder, ctrl, "CTRL");
        appendPart(builder, shift, "SHIFT");
        appendPart(builder, alt, "ALT");
        appendPart(builder, superKey, "SUPER");
        appendPart(builder, true, keyName);
        return builder.toString();
    }

    private static void appendPart(StringBuilder builder, boolean enabled, String text) {
        if (!enabled) return;
        if (builder.length() > 0) builder.append('+');
        builder.append(text);
    }

    private static String normalizeToken(String token) {
        String normalized = token.trim();
        if (normalized.length() == 1) return normalized;
        return normalized
                .replace(" ", "_")
                .replace("-", "_");
    }

    private static Map<String, KeyDef> createKeyCodes() {
        Map<String, KeyDef> keys = new java.util.HashMap<>();
        put(keys, "SPACE", GLFW.GLFW_KEY_SPACE);
        put(keys, "APOSTROPHE", GLFW.GLFW_KEY_APOSTROPHE);
        put(keys, "COMMA", GLFW.GLFW_KEY_COMMA);
        put(keys, "MINUS", GLFW.GLFW_KEY_MINUS);
        put(keys, "PERIOD", GLFW.GLFW_KEY_PERIOD);
        put(keys, "SLASH", GLFW.GLFW_KEY_SLASH);
        put(keys, "SEMICOLON", GLFW.GLFW_KEY_SEMICOLON);
        put(keys, "EQUAL", GLFW.GLFW_KEY_EQUAL);
        put(keys, "LEFT_BRACKET", GLFW.GLFW_KEY_LEFT_BRACKET);
        put(keys, "BACKSLASH", GLFW.GLFW_KEY_BACKSLASH);
        put(keys, "RIGHT_BRACKET", GLFW.GLFW_KEY_RIGHT_BRACKET);
        put(keys, "GRAVE_ACCENT", GLFW.GLFW_KEY_GRAVE_ACCENT);
        put(keys, "ESCAPE", GLFW.GLFW_KEY_ESCAPE);
        put(keys, "ENTER", GLFW.GLFW_KEY_ENTER);
        put(keys, "TAB", GLFW.GLFW_KEY_TAB);
        put(keys, "BACKSPACE", GLFW.GLFW_KEY_BACKSPACE);
        put(keys, "INSERT", GLFW.GLFW_KEY_INSERT);
        put(keys, "DELETE", GLFW.GLFW_KEY_DELETE);
        put(keys, "RIGHT", GLFW.GLFW_KEY_RIGHT);
        put(keys, "LEFT", GLFW.GLFW_KEY_LEFT);
        put(keys, "DOWN", GLFW.GLFW_KEY_DOWN);
        put(keys, "UP", GLFW.GLFW_KEY_UP);
        put(keys, "PAGE_UP", GLFW.GLFW_KEY_PAGE_UP);
        put(keys, "PAGE_DOWN", GLFW.GLFW_KEY_PAGE_DOWN);
        put(keys, "HOME", GLFW.GLFW_KEY_HOME);
        put(keys, "END", GLFW.GLFW_KEY_END);
        put(keys, "PRINT_SCREEN", GLFW.GLFW_KEY_PRINT_SCREEN);
        put(keys, "PAUSE", GLFW.GLFW_KEY_PAUSE);

        for (char c = 'A'; c <= 'Z'; c++) {
            put(keys, String.valueOf(c), GLFW.GLFW_KEY_A + (c - 'A'));
        }
        for (char c = '0'; c <= '9'; c++) {
            put(keys, String.valueOf(c), GLFW.GLFW_KEY_0 + (c - '0'));
        }
        for (int i = 1; i <= 25; i++) {
            put(keys, "F" + i, GLFW.GLFW_KEY_F1 + (i - 1));
        }
        for (int i = 0; i <= 9; i++) {
            put(keys, "KP_" + i, GLFW.GLFW_KEY_KP_0 + i);
        }
        put(keys, "KP_DECIMAL", GLFW.GLFW_KEY_KP_DECIMAL);
        put(keys, "KP_DIVIDE", GLFW.GLFW_KEY_KP_DIVIDE);
        put(keys, "KP_MULTIPLY", GLFW.GLFW_KEY_KP_MULTIPLY);
        put(keys, "KP_SUBTRACT", GLFW.GLFW_KEY_KP_SUBTRACT);
        put(keys, "KP_ADD", GLFW.GLFW_KEY_KP_ADD);
        put(keys, "KP_ENTER", GLFW.GLFW_KEY_KP_ENTER);
        put(keys, "KP_EQUAL", GLFW.GLFW_KEY_KP_EQUAL);

        putAlias(keys, "DEL", "DELETE");
        putAlias(keys, "RETURN", "ENTER");
        putAlias(keys, "ESC", "ESCAPE");
        putAlias(keys, "PGUP", "PAGE_UP");
        putAlias(keys, "PGDN", "PAGE_DOWN");
        putAlias(keys, "`", "GRAVE_ACCENT");
        putAlias(keys, "-", "MINUS");
        putAlias(keys, "=", "EQUAL");
        putAlias(keys, "[", "LEFT_BRACKET");
        putAlias(keys, "]", "RIGHT_BRACKET");
        putAlias(keys, "\\", "BACKSLASH");
        putAlias(keys, ";", "SEMICOLON");
        putAlias(keys, "'", "APOSTROPHE");
        putAlias(keys, ",", "COMMA");
        putAlias(keys, ".", "PERIOD");
        putAlias(keys, "/", "SLASH");
        return keys;
    }

    private static void put(Map<String, KeyDef> keys, String name, int keyCode) {
        keys.put(name, new KeyDef(keyCode, name));
    }

    private static void putAlias(Map<String, KeyDef> keys, String alias, String canonicalName) {
        KeyDef keyDef = keys.get(canonicalName);
        if (keyDef != null) keys.put(alias, keyDef);
    }

    private record KeyDef(int keyCode, String name) {
    }
}
