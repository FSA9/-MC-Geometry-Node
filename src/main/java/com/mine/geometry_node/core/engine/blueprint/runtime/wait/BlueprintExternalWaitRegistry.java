package com.mine.geometry_node.core.engine.blueprint.runtime.wait;

import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Registry for asynchronous Blueprint services addressed by execution-result service ID. */
public final class BlueprintExternalWaitRegistry {
    public static final BlueprintExternalWaitRegistry INSTANCE = new BlueprintExternalWaitRegistry();

    private final Map<String, BlueprintExternalWaitHandler> handlers = new HashMap<>();

    private BlueprintExternalWaitRegistry() {
    }

    public synchronized void register(BlueprintExternalWaitHandler handler) {
        Objects.requireNonNull(handler, "handler");
        String id = Objects.requireNonNull(handler.externalWaitId(), "handler.externalWaitId()").trim();
        if (id.isEmpty()) {
            throw new IllegalArgumentException("Blueprint external wait handler id cannot be empty");
        }
        BlueprintExternalWaitHandler existing = handlers.putIfAbsent(id, handler);
        if (existing != null && existing != handler) {
            throw new IllegalStateException("Duplicate Blueprint external wait handler: " + id);
        }
    }

    @Nullable
    public synchronized BlueprintExternalWaitHandler get(String id) {
        return handlers.get(id);
    }
}
