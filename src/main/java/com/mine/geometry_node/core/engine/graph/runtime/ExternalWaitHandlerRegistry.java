package com.mine.geometry_node.core.engine.graph.runtime;

import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Registry for asynchronous services addressed by execution-result service ID. */
public final class ExternalWaitHandlerRegistry {
    public static final ExternalWaitHandlerRegistry INSTANCE = new ExternalWaitHandlerRegistry();

    private final Map<String, ExternalWaitHandler> handlers = new HashMap<>();

    private ExternalWaitHandlerRegistry() {
    }

    public synchronized void register(ExternalWaitHandler handler) {
        Objects.requireNonNull(handler, "handler");
        String id = Objects.requireNonNull(handler.externalWaitId(), "handler.externalWaitId()").trim();
        if (id.isEmpty()) throw new IllegalArgumentException("External wait handler id cannot be empty");
        ExternalWaitHandler existing = handlers.putIfAbsent(id, handler);
        if (existing != null && existing != handler) {
            throw new IllegalStateException("Duplicate external wait handler: " + id);
        }
    }

    @Nullable
    public synchronized ExternalWaitHandler get(String id) {
        return handlers.get(id);
    }
}
