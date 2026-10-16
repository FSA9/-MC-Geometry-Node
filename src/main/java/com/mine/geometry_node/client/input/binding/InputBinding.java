package com.mine.geometry_node.client.input.binding;

import java.util.Locale;

/** Canonical input binding independent of the UI framework. */
public final class InputBinding {
    public enum Device { KEYBOARD, MOUSE }

    /** Stable button masks; UI adapters translate their event representation to these bits. */
    public static final int BUTTON_PRIMARY = 1;
    public static final int BUTTON_SECONDARY = 2;
    public static final int BUTTON_TERTIARY = 4;

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
                case "LEFT_CLICK", "MOUSE_LEFT" -> { mouseButton = BUTTON_PRIMARY; mouseName = "LEFT_CLICK"; }
                case "RIGHT_CLICK", "MOUSE_RIGHT" -> { mouseButton = BUTTON_SECONDARY; mouseName = "RIGHT_CLICK"; }
                case "MIDDLE_CLICK", "MOUSE_MIDDLE" -> { mouseButton = BUTTON_TERTIARY; mouseName = "MIDDLE_CLICK"; }
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

    public static InputBinding fromKey(int keyCode, boolean ctrl, boolean shift, boolean alt, boolean superKey) {
        KeyBinding binding = KeyBinding.fromKey(keyCode, ctrl, shift, alt, superKey);
        return binding != null ? new InputBinding(binding) : null;
    }

    public static InputBinding fromMouse(int button, boolean ctrl, boolean shift, boolean alt, boolean superKey) {
        String name = switch (button) {
            case BUTTON_PRIMARY -> "LEFT_CLICK";
            case BUTTON_SECONDARY -> "RIGHT_CLICK";
            case BUTTON_TERTIARY -> "MIDDLE_CLICK";
            default -> null;
        };
        if (name == null) return null;
        StringBuilder canonical = new StringBuilder();
        append(canonical, ctrl, "CTRL");
        append(canonical, shift, "SHIFT");
        append(canonical, alt, "ALT");
        append(canonical, superKey, "SUPER");
        append(canonical, true, name);
        return new InputBinding(button, ctrl, shift, alt, superKey, canonical.toString());
    }

    public Device device() { return mDevice; }
    public String text() { return mText; }

    public boolean matchesKey(int keyCode, boolean ctrl, boolean shift, boolean alt, boolean superKey) {
        return mDevice == Device.KEYBOARD && mKeyBinding.matches(keyCode, ctrl, shift, alt, superKey);
    }

    public boolean matchesMouse(int actionButton, int buttonState,
                                boolean ctrl, boolean shift, boolean alt, boolean superKey) {
        if (mDevice != Device.MOUSE) return false;
        boolean buttonMatches = actionButton == mMouseButton || (buttonState & mMouseButton) != 0;
        return buttonMatches && ctrl == mCtrl && shift == mShift && alt == mAlt && superKey == mSuper;
    }

    private static void append(StringBuilder target, boolean enabled, String part) {
        if (!enabled) return;
        if (!target.isEmpty()) target.append('+');
        target.append(part);
    }
}
