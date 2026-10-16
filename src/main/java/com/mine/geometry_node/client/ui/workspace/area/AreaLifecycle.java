package com.mine.geometry_node.client.ui.workspace.area;

import icyllis.modernui.view.View;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** UI-thread lifecycle of one area, independent of the editor type hosted inside it. */
public final class AreaLifecycle {
    public enum Event { ACTIVATED, DEACTIVATED, CONTENT_CHANGED, HIDDEN, DETACHED, DISPOSED }

    public interface Owner {
        AreaLifecycle areaLifecycle();
    }

    @FunctionalInterface
    public interface Subscription extends AutoCloseable {
        Subscription NONE = () -> {};
        @Override void close();
    }

    private final List<Consumer<Event>> listeners = new ArrayList<>();
    private boolean active;
    private boolean disposed;

    public static AreaLifecycle find(View anchor) {
        for (View view = anchor; view != null;
             view = view.getParent() instanceof View parent ? parent : null) {
            if (view instanceof Owner owner) return owner.areaLifecycle();
        }
        return null;
    }

    public boolean isActive() { return active; }

    /** No initial replay. Close the subscription when its consumer is destroyed. */
    public Subscription subscribe(Consumer<Event> listener) {
        Objects.requireNonNull(listener, "listener");
        if (disposed) return Subscription.NONE;
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    void setActive(boolean value) {
        if (disposed || active == value) return;
        active = value;
        emit(value ? Event.ACTIVATED : Event.DEACTIVATED);
    }

    void emit(Event event) {
        if (disposed) return;
        if (event == Event.DISPOSED) { disposed = true; active = false; }
        for (Consumer<Event> listener : List.copyOf(listeners)) {
            if (!listeners.contains(listener)) continue;
            try { listener.accept(event); }
            catch (RuntimeException failure) {
                LoggerFactory.getLogger(AreaLifecycle.class).error("Area lifecycle callback failed: {}", event, failure);
            }
        }
        if (disposed) listeners.clear();
    }
}
