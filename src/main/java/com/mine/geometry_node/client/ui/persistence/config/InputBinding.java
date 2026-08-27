package com.mine.geometry_node.client.ui.persistence.config;

import icyllis.modernui.view.KeyEvent;
import icyllis.modernui.view.MotionEvent;

import java.util.Locale;

/** Canonical MainUI binding supporting either a keyboard input or a mouse button. */
public final class InputBinding {
    public enum Device { KEYBOARD, MOUSE }

    private final Device mDevice;
    private final KeyBinding mKeyBinding;
    private final int mMouseButton;
    private final boolean mCtrl;
    private final boolean mShift;
    private final boolean mAlt;
    private final boolean mSuper;
    private final String mText;

    private InputBinding(KeyBinding keyBinding) {
        mDevice = Device.KEYBOARD;
        mKeyBinding = keyBinding;
        mMouseButton = 0;
        mCtrl = keyBinding.ctrl;
        mShift = keyBinding.shift;
        mAlt = keyBinding.alt;
        mSuper = keyBinding.superKey;
        mText = keyBinding.text;
    }

    private InputBinding(int mouseButton, boolean ctrl, boolean shift, boolean alt, boolean superKey, String text) {
        mDevice = Device.MOUSE;
        mKeyBinding = null;
        mMouseButton = mouseButton;
        mCtrl = ctrl;
        mShift = shift;
        mAlt = alt;
        mSuper = superKey;
        mText = text;
    }

    public static InputBinding parse(String value) {
        KeyBinding keyboard = KeyBinding.parse(value);
        if (keyboard != null) return new InputBinding(keyboard);
        if (value == null || value.isBlank()) return null;

        boolean ctrl = false;
        boolean shift = false;
        boolean alt = false;
        boolean superKey = false;
        Integer mouseButton = null;
        String mouseName = null;
        for (String token : value.trim().toUpperCase(Locale.ROOT).split("\\+")) {
            String part = token.trim().replace(' ', '_').replace('-', '_');
            switch (part) {
                case "CTRL", "CONTROL" -> ctrl = true;
                case "SHIFT" -> shift = true;
                case "ALT", "OPTION" -> alt = true;
                case "SUPER", "META", "CMD", "COMMAND" -> superKey = true;
                case "LEFT_CLICK", "MOUSE_LEFT" -> { mouseButton = MotionEvent.BUTTON_PRIMARY; mouseName = "LEFT_CLICK"; }
                case "RIGHT_CLICK", "MOUSE_RIGHT" -> { mouseButton = MotionEvent.BUTTON_SECONDARY; mouseName = "RIGHT_CLICK"; }
                case "MIDDLE_CLICK", "MOUSE_MIDDLE" -> { mouseButton = MotionEvent.BUTTON_TERTIARY; mouseName = "MIDDLE_CLICK"; }
                default -> { return null; }
            }
        }
        if (mouseButton == null) return null;
        StringBuilder canonical = new StringBuilder();
        append(canonical, ctrl, "CTRL");
        append(canonical, shift, "SHIFT");
        append(canonical, alt, "ALT");
        append(canonical, superKey, "SUPER");
        append(canonical, true, mouseName);
        return new InputBinding(mouseButton, ctrl, shift, alt, superKey, canonical.toString());
    }

    public static InputBinding fromEvent(KeyEvent event) {
        KeyBinding binding = KeyBinding.fromEvent(event);
        return binding != null ? new InputBinding(binding) : null;
    }

    public static InputBinding fromEvent(MotionEvent event) {
        if (event == null) return null;
        int button = event.getActionButton();
        String name = switch (button) {
            case MotionEvent.BUTTON_PRIMARY -> "LEFT_CLICK";
            case MotionEvent.BUTTON_SECONDARY -> "RIGHT_CLICK";
            case MotionEvent.BUTTON_TERTIARY -> "MIDDLE_CLICK";
            default -> null;
        };
        if (name == null) return null;
        StringBuilder canonical = new StringBuilder();
        append(canonical, event.isCtrlPressed(), "CTRL");
        append(canonical, event.isShiftPressed(), "SHIFT");
        append(canonical, event.isAltPressed(), "ALT");
        append(canonical, event.isSuperPressed(), "SUPER");
        append(canonical, true, name);
        return new InputBinding(button, event.isCtrlPressed(), event.isShiftPressed(), event.isAltPressed(),
                event.isSuperPressed(), canonical.toString());
    }

    public Device device() { return mDevice; }
    public String text() { return mText; }

    public boolean matches(KeyEvent event) {
        return mDevice == Device.KEYBOARD && mKeyBinding.matches(event);
    }

    public boolean matches(MotionEvent event) {
        if (mDevice != Device.MOUSE || event == null) return false;
        boolean buttonMatches = event.getActionButton() == mMouseButton
                || (event.getButtonState() & mMouseButton) != 0;
        return buttonMatches
                && event.isCtrlPressed() == mCtrl
                && event.isShiftPressed() == mShift
                && event.isAltPressed() == mAlt
                && event.isSuperPressed() == mSuper;
    }

    private static void append(StringBuilder target, boolean enabled, String part) {
        if (!enabled) return;
        if (!target.isEmpty()) target.append('+');
        target.append(part);
    }
}
