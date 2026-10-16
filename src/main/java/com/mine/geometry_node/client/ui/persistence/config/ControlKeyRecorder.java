package com.mine.geometry_node.client.ui.persistence.config;

import icyllis.modernui.view.KeyEvent;

import java.util.HashSet;
import java.util.Set;

/** Delay modifier-only capture until release, leaving Ctrl+W available as a chord. */
public final class ControlKeyRecorder {
    private final Set<Integer> modifiers = new HashSet<>();
    private String pending;

    public void reset() { modifiers.clear(); pending = null; }

    public String accept(int key, boolean pressed, String ordinaryBinding) {
        if (!KeyEvent.isModifierKey(key)) {
            if (!pressed) return null;
            reset();
            return ordinaryBinding;
        }
        if (!pressed) {
            if (!modifiers.remove(key)) return null;
            String result = pending;
            reset();
            return result;
        }
        modifiers.add(key);
        StringBuilder text = new StringBuilder();
        append(text, "CTRL", KeyEvent.KEY_LEFT_CONTROL, KeyEvent.KEY_RIGHT_CONTROL);
        append(text, "SHIFT", KeyEvent.KEY_LEFT_SHIFT, KeyEvent.KEY_RIGHT_SHIFT);
        append(text, "ALT", KeyEvent.KEY_LEFT_ALT, KeyEvent.KEY_RIGHT_ALT);
        append(text, "SUPER", KeyEvent.KEY_LEFT_SUPER, KeyEvent.KEY_RIGHT_SUPER);
        pending = text.toString();
        return null;
    }

    private void append(StringBuilder text, String name, int left, int right) {
        if (!modifiers.contains(left) && !modifiers.contains(right)) return;
        if (!text.isEmpty()) text.append('+');
        text.append(name);
    }
}
