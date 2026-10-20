package com.mine.geometry_node.client.input.binding;

import org.lwjgl.glfw.GLFW;

import java.util.Locale;
import java.util.function.IntPredicate;

/** A held control accepts extra modifiers so W + Ctrl can move forward and down together. */
public final class ControlKeyBinding {
    private final KeyBinding key;
    private final boolean ctrl, shift, alt, superKey;
    private final String text;

    private ControlKeyBinding(KeyBinding key, boolean ctrl, boolean shift, boolean alt, boolean superKey, String text) {
        this.key = key;
        this.ctrl = ctrl;
        this.shift = shift;
        this.alt = alt;
        this.superKey = superKey;
        this.text = text;
    }

    public static ControlKeyBinding parse(String value) {
        if (value == null || value.isBlank()) return null;
        for (String token : value.split("\\+", -1)) if (token.isBlank()) return null;
        KeyBinding key = KeyBinding.parse(value);
        if (key != null) return new ControlKeyBinding(key, key.ctrl, key.shift, key.alt, key.superKey, key.text);
        if (value == null || value.isBlank()) return null;
        boolean ctrl = false, shift = false, alt = false, superKey = false;
        for (String token : value.toUpperCase(Locale.ROOT).split("\\+", -1)) {
            switch (token.trim()) {
                case "CTRL", "CONTROL" -> ctrl = true;
                case "SHIFT" -> shift = true;
                case "ALT", "OPTION" -> alt = true;
                case "SUPER", "META", "CMD", "COMMAND" -> superKey = true;
                default -> { return null; }
            }
        }
        String text = (ctrl ? "CTRL+" : "") + (shift ? "SHIFT+" : "")
                + (alt ? "ALT+" : "") + (superKey ? "SUPER+" : "");
        return new ControlKeyBinding(null, ctrl, shift, alt, superKey, text.substring(0, text.length() - 1));
    }

    public String text() { return text; }

    public boolean isHeld(IntPredicate held) {
        return (key == null || held.test(key.keyCode))
                && (!ctrl || held.test(GLFW.GLFW_KEY_LEFT_CONTROL) || held.test(GLFW.GLFW_KEY_RIGHT_CONTROL))
                && (!shift || held.test(GLFW.GLFW_KEY_LEFT_SHIFT) || held.test(GLFW.GLFW_KEY_RIGHT_SHIFT))
                && (!alt || held.test(GLFW.GLFW_KEY_LEFT_ALT) || held.test(GLFW.GLFW_KEY_RIGHT_ALT))
                && (!superKey || held.test(GLFW.GLFW_KEY_LEFT_SUPER) || held.test(GLFW.GLFW_KEY_RIGHT_SUPER));
    }

    /**
     * Whether an exact keyboard chord satisfies this held control. The chord's physical primary
     * key and modifiers form its held state; extra modifiers and modifier-only controls are allowed.
     */
    public boolean isHeldBy(KeyBinding chord) {
        if (chord == null) return false;
        return isHeld(code -> code == chord.keyCode || switch (code) {
            case GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL -> chord.ctrl;
            case GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT -> chord.shift;
            case GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_KEY_RIGHT_ALT -> chord.alt;
            case GLFW.GLFW_KEY_LEFT_SUPER, GLFW.GLFW_KEY_RIGHT_SUPER -> chord.superKey;
            default -> false;
        });
    }

    /** A more specific chord with the same primary key takes precedence. */
    public boolean supersedes(ControlKeyBinding other) {
        return key != null && other.key != null && key.keyCode == other.key.keyCode
                && (!other.ctrl || ctrl) && (!other.shift || shift)
                && (!other.alt || alt) && (!other.superKey || superKey)
                && !text.equals(other.text);
    }
}
