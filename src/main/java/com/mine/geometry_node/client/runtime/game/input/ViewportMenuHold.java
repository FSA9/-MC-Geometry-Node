package com.mine.geometry_node.client.runtime.game.input;

import com.mine.geometry_node.client.input.binding.KeyBinding;
import org.lwjgl.glfw.GLFW;
import java.util.function.Consumer;

/** Client-thread handoff of a held key after flight relinquishes mouse capture. */
final class ViewportMenuHold {
    enum Event { OPEN, RELEASE, CANCEL }

    private Object owner;
    private int key = -1;
    private Consumer<Event> listener;

    static boolean matches(KeyBinding binding, int key, int modifiers) {
        if (binding == null || key != binding.keyCode) return false;
        int expected = (binding.shift ? GLFW.GLFW_MOD_SHIFT : 0) | (binding.ctrl ? GLFW.GLFW_MOD_CONTROL : 0)
                | (binding.alt ? GLFW.GLFW_MOD_ALT : 0) | (binding.superKey ? GLFW.GLFW_MOD_SUPER : 0);
        int mask = GLFW.GLFW_MOD_SHIFT | GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_ALT | GLFW.GLFW_MOD_SUPER;
        return (modifiers & mask) == expected;
    }

    void begin(Object owner, int key, Consumer<Event> listener) {
        cancel();
        this.owner = owner;
        this.key = key;
        this.listener = listener;
        listener.accept(Event.OPEN);
    }

    boolean handle(int action, int key) {
        if (this.key != key || key == -1) return false;
        if (action == GLFW.GLFW_RELEASE) {
            finish(Event.RELEASE);
            this.key = -1;
        }
        return true;
    }

    boolean isOpen() { return listener != null; }
    void cancel(Object owner) { if (this.owner == owner) cancel(); }
    void cancel() { finish(Event.CANCEL); }

    private void finish(Event event) {
        Consumer<Event> callback = listener;
        listener = null;
        owner = null;
        // Keep swallowing the trigger's repeats and release even after cancellation.
        if (callback != null) callback.accept(event);
    }
}
