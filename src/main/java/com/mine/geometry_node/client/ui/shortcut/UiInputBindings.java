package com.mine.geometry_node.client.ui.shortcut;

import com.mine.geometry_node.client.input.binding.InputBinding;
import com.mine.geometry_node.client.input.binding.KeyBinding;
import icyllis.modernui.view.KeyEvent;
import icyllis.modernui.view.MotionEvent;

/** Converts ModernUI events at the UI boundary; binding semantics stay framework independent. */
public final class UiInputBindings {
    private UiInputBindings() {}

    public static InputBinding fromEvent(KeyEvent event) {
        return event == null ? null : InputBinding.fromKey(event.getKeyCode(), event.isControlPressed(),
                event.isShiftPressed(), event.isAltPressed(), event.isSuperPressed());
    }

    public static InputBinding fromEvent(MotionEvent event) {
        if (event == null) return null;
        int button = event.getActionButton();
        // Some pointer-down events only supply a button state. Capture it only if unambiguous.
        if (button == 0 && Integer.bitCount(event.getButtonState()) == 1) button = event.getButtonState();
        return InputBinding.fromMouse(mouseButton(button), event.isControlPressed(),
                event.isShiftPressed(), event.isAltPressed(), event.isSuperPressed());
    }

    public static boolean matches(KeyBinding binding, KeyEvent event) {
        return binding != null && event != null && binding.matches(event.getKeyCode(), event.isControlPressed(),
                event.isShiftPressed(), event.isAltPressed(), event.isSuperPressed());
    }

    public static boolean matches(InputBinding binding, KeyEvent event) {
        return binding != null && event != null && binding.matchesKey(event.getKeyCode(), event.isControlPressed(),
                event.isShiftPressed(), event.isAltPressed(), event.isSuperPressed());
    }

    public static boolean matches(InputBinding binding, MotionEvent event) {
        return binding != null && event != null && binding.matchesMouse(mouseButton(event.getActionButton()),
                mouseButtons(event.getButtonState()), event.isControlPressed(), event.isShiftPressed(),
                event.isAltPressed(), event.isSuperPressed());
    }

    private static int mouseButton(int button) {
        return switch (button) {
            case 0 -> 0;
            case MotionEvent.BUTTON_PRIMARY -> InputBinding.BUTTON_PRIMARY;
            case MotionEvent.BUTTON_SECONDARY -> InputBinding.BUTTON_SECONDARY;
            case MotionEvent.BUTTON_TERTIARY -> InputBinding.BUTTON_TERTIARY;
            // Preserve the difference between no action button and an unsupported action button.
            default -> -1;
        };
    }

    private static int mouseButtons(int buttons) {
        int result = 0;
        if ((buttons & MotionEvent.BUTTON_PRIMARY) != 0) result |= InputBinding.BUTTON_PRIMARY;
        if ((buttons & MotionEvent.BUTTON_SECONDARY) != 0) result |= InputBinding.BUTTON_SECONDARY;
        if ((buttons & MotionEvent.BUTTON_TERTIARY) != 0) result |= InputBinding.BUTTON_TERTIARY;
        return result;
    }
}
