package com.mine.geometry_node.core.engine.runtime;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Canonical registry for server engines driven by {@link ServerEngineDriver}. */
public final class ServerEngineRegistry {
    public static final ServerEngineRegistry INSTANCE = new ServerEngineRegistry();

    private final Map<String, ServerEngine> engines = new LinkedHashMap<>();

    private ServerEngineRegistry() {
    }

    public synchronized void register(ServerEngine engine) {
        Objects.requireNonNull(engine, "engine");
        String id = Objects.requireNonNull(engine.id(), "engine.id()").trim();
        if (id.isEmpty()) throw new IllegalArgumentException("Server engine id cannot be empty");
        ServerEngine existing = engines.get(id);
        if (existing == engine) return;
        if (existing != null) throw new IllegalStateException("Duplicate server engine: " + id);
        engine.init();
        engines.put(id, engine);
    }

    public synchronized Collection<ServerEngine> all() {
        return List.copyOf(engines.values());
    }
}
