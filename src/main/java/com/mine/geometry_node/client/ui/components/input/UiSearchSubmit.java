package com.mine.geometry_node.client.ui.components.input;

import icyllis.modernui.view.KeyEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.EditText;

import java.util.Objects;

/** Binds explicit search submission without observing or changing any input drafts. */
public final class UiSearchSubmit {
    private UiSearchSubmit() {}

    /** Replaces the button click and input key listeners. Enter release/repeat events are consumed. */
    public static void bind(View button, Runnable action, EditText... inputs) {
        Objects.requireNonNull(action, "action");
        View.OnKeyListener submitKey = (view, key, event) -> {
            if (key != KeyEvent.KEY_ENTER && key != KeyEvent.KEY_KP_ENTER) return false;
            if (button.isEnabled() && event.getAction() == KeyEvent.ACTION_DOWN
                    && event.getRepeatCount() == 0) action.run();
            return true;
        };
        button.setOnClickListener(view -> { if (button.isEnabled()) action.run(); });
        for (EditText input : inputs) input.setOnKeyListener(submitKey);
    }
}
