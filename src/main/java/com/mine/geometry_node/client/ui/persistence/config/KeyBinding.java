package com.mine.geometry_node.client.ui.persistence.config;

import icyllis.modernui.view.KeyEvent;

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

        if (keyDef == null || KeyEvent.isModifierKey(keyDef.keyCode)) return null;
        return new KeyBinding(keyDef.keyCode, ctrl, shift, alt, superKey, canonicalText(ctrl, shift, alt, superKey, keyName));
    }

    public boolean matches(KeyEvent event) {
        return event != null
                && event.getKeyCode() == keyCode
                && event.isCtrlPressed() == ctrl
                && event.isShiftPressed() == shift
                && event.isAltPressed() == alt
                && event.isSuperPressed() == superKey;
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
        put(keys, "SPACE", KeyEvent.KEY_SPACE);
        put(keys, "APOSTROPHE", KeyEvent.KEY_APOSTROPHE);
        put(keys, "COMMA", KeyEvent.KEY_COMMA);
        put(keys, "MINUS", KeyEvent.KEY_MINUS);
        put(keys, "PERIOD", KeyEvent.KEY_PERIOD);
        put(keys, "SLASH", KeyEvent.KEY_SLASH);
        put(keys, "SEMICOLON", KeyEvent.KEY_SEMICOLON);
        put(keys, "EQUAL", KeyEvent.KEY_EQUAL);
        put(keys, "LEFT_BRACKET", KeyEvent.KEY_LEFT_BRACKET);
        put(keys, "BACKSLASH", KeyEvent.KEY_BACKSLASH);
        put(keys, "RIGHT_BRACKET", KeyEvent.KEY_RIGHT_BRACKET);
        put(keys, "GRAVE_ACCENT", KeyEvent.KEY_GRAVE_ACCENT);
        put(keys, "ESCAPE", KeyEvent.KEY_ESCAPE);
        put(keys, "ENTER", KeyEvent.KEY_ENTER);
        put(keys, "TAB", KeyEvent.KEY_TAB);
        put(keys, "BACKSPACE", KeyEvent.KEY_BACKSPACE);
        put(keys, "INSERT", KeyEvent.KEY_INSERT);
        put(keys, "DELETE", KeyEvent.KEY_DELETE);
        put(keys, "RIGHT", KeyEvent.KEY_RIGHT);
        put(keys, "LEFT", KeyEvent.KEY_LEFT);
        put(keys, "DOWN", KeyEvent.KEY_DOWN);
        put(keys, "UP", KeyEvent.KEY_UP);
        put(keys, "PAGE_UP", KeyEvent.KEY_PAGE_UP);
        put(keys, "PAGE_DOWN", KeyEvent.KEY_PAGE_DOWN);
        put(keys, "HOME", KeyEvent.KEY_HOME);
        put(keys, "END", KeyEvent.KEY_END);
        put(keys, "PRINT_SCREEN", KeyEvent.KEY_PRINT_SCREEN);
        put(keys, "PAUSE", KeyEvent.KEY_PAUSE);

        for (char c = 'A'; c <= 'Z'; c++) {
            put(keys, String.valueOf(c), KeyEvent.KEY_A + (c - 'A'));
        }
        for (char c = '0'; c <= '9'; c++) {
            put(keys, String.valueOf(c), KeyEvent.KEY_0 + (c - '0'));
        }
        for (int i = 1; i <= 25; i++) {
            put(keys, "F" + i, KeyEvent.KEY_F1 + (i - 1));
        }
        for (int i = 0; i <= 9; i++) {
            put(keys, "KP_" + i, KeyEvent.KEY_KP_0 + i);
        }
        put(keys, "KP_DECIMAL", KeyEvent.KEY_KP_DECIMAL);
        put(keys, "KP_DIVIDE", KeyEvent.KEY_KP_DIVIDE);
        put(keys, "KP_MULTIPLY", KeyEvent.KEY_KP_MULTIPLY);
        put(keys, "KP_SUBTRACT", KeyEvent.KEY_KP_SUBTRACT);
        put(keys, "KP_ADD", KeyEvent.KEY_KP_ADD);
        put(keys, "KP_ENTER", KeyEvent.KEY_KP_ENTER);
        put(keys, "KP_EQUAL", KeyEvent.KEY_KP_EQUAL);

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
