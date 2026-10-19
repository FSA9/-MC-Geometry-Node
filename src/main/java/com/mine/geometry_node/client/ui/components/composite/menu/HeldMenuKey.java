package com.mine.geometry_node.client.ui.components.composite.menu;

import com.mine.geometry_node.client.ui.shortcut.UiInputBindings;

import com.mine.geometry_node.client.input.binding.KeyBinding;
import icyllis.modernui.view.KeyEvent;

/** Keeps a held menu from reopening on repeats, even after dismissal or selection. */
public final class HeldMenuKey {
    private int key = -1;
    private boolean cancelled;

    public void begin(int key) { this.key = key; cancelled = false; }
    public void reset() { key = -1; }
    public boolean isHeld() { return key != -1; }

    public boolean dispatch(KeyEvent event, KeyBinding binding, boolean canOpen, Runnable open, Runnable confirm, Runnable cancel) {
        if (isHeld()) {
            if (event.getAction() == KeyEvent.ACTION_UP && event.getKeyCode() == key) {
                boolean apply = !cancelled;
                reset();
                if (apply) confirm.run();
            } else if (event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEY_ESCAPE) {
                if (!cancelled) {
                    cancelled = true;
                    cancel.run();
                }
            }
            return true;
        }
        if (canOpen && event.getAction() == KeyEvent.ACTION_DOWN && binding != null && UiInputBindings.matches(binding, event)) {
            if (event.getRepeatCount() == 0) {
                begin(event.getKeyCode());
                open.run();
            }
            return true;
        }
        return false;
    }
}
