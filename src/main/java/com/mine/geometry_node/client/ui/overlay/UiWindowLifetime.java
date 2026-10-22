package com.mine.geometry_node.client.ui.overlay;

import icyllis.modernui.view.View;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/** Logical window lifetime. View detachment and layout reconstruction do not close it. UI-thread only. */
public final class UiWindowLifetime implements AutoCloseable {
    public enum State { HIDDEN, VISIBLE, CLOSED }
    public interface Owner { UiWindowLifetime windowLifetime(); }
    private final Set<Consumer<State>> listeners = new LinkedHashSet<>();
    private State state = State.HIDDEN;

    public State state() { return state; }
    public void show() { transition(State.VISIBLE); }
    public void hide() { transition(State.HIDDEN); }
    @Override public void close() { transition(State.CLOSED); }

    /** Does not deliver an initial event; callers reconcile current state after installing the listener. */
    public AutoCloseable observe(Consumer<State> listener) {
        Objects.requireNonNull(listener);
        if (state != State.CLOSED) listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    public static UiWindowLifetime find(View anchor) {
        for (View current = anchor; current != null;
             current = current.getParent() instanceof View parent ? parent : null) {
            if (current instanceof Owner owner) return owner.windowLifetime();
        }
        return null;
    }

    private void transition(State next) {
        if (state == State.CLOSED || state == next) return;
        state = next;
        try {
            for (Consumer<State> listener : new ArrayList<>(listeners)) {
                if (state != next) break; // A callback may close the owner.
                if (!listeners.contains(listener)) continue;
                try { listener.accept(next); }
                catch (RuntimeException failure) {
                    System.getLogger(UiWindowLifetime.class.getName()).log(System.Logger.Level.WARNING,
                            "Window lifetime listener failed", failure);
                }
            }
        } finally {
            if (state == State.CLOSED) listeners.clear();
        }
    }
}
