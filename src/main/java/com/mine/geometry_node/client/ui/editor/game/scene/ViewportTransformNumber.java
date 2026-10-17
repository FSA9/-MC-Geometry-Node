package com.mine.geometry_node.client.ui.editor.game.scene;

import icyllis.modernui.view.KeyEvent;

/** Modal number text, not another shortcut binding. Main keyboard and numpad share semantics. */
final class ViewportTransformNumber {
    private String text = "";
    String text() { return text; }
    boolean active() { return !text.isEmpty(); }
    Double value() {
        try { double value = Double.parseDouble(text); return Double.isFinite(value) ? value : null; }
        catch (NumberFormatException invalid) { return null; }
    }
    boolean key(int key) {
        if (key == KeyEvent.KEY_BACKSPACE) {
            if (!text.isEmpty()) text = text.substring(0, text.length()-1);
            return true;
        }
        if (key == KeyEvent.KEY_MINUS || key == KeyEvent.KEY_KP_SUBTRACT) {
            text = text.startsWith("-") ? text.substring(1) : "-" + text;
            return true;
        }
        if (key == KeyEvent.KEY_PERIOD || key == KeyEvent.KEY_KP_DECIMAL) {
            if (!text.contains(".") && text.length() < 24) text += ".";
            return true;
        }
        int digit = key >= KeyEvent.KEY_0 && key <= KeyEvent.KEY_9 ? key - KeyEvent.KEY_0
                : key >= KeyEvent.KEY_KP_0 && key <= KeyEvent.KEY_KP_9 ? key - KeyEvent.KEY_KP_0 : -1;
        if (digit < 0) return false;
        if (text.length() < 24) text += (char)('0' + digit);
        return true;
    }
}
