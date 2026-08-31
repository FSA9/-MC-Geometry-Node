package com.mine.geometry_node.core.engine.system.asset;

import net.minecraft.server.MinecraftServer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Dispatches committed file changes only to managers that own the affected asset types. */
public final class AssetLifecycleRegistry {
    public static final AssetLifecycleRegistry INSTANCE = new AssetLifecycleRegistry();

    private final Map<String, Handler> handlers = new LinkedHashMap<>();

    private AssetLifecycleRegistry() {
    }

    public synchronized void register(String typeId, Handler handler) {
        String normalized = typeId == null ? "" : typeId.trim().toLowerCase(java.util.Locale.ROOT);
        if (normalized.isEmpty()) throw new IllegalArgumentException("asset type id must not be empty");
        if (handler == null) throw new IllegalArgumentException("asset lifecycle handler must not be null");
        if (handlers.putIfAbsent(normalized, handler) != null) {
            throw new IllegalArgumentException("duplicate asset lifecycle handler: " + normalized);
        }
    }

    public void refresh(MinecraftServer server, Set<String> affectedTypeIds) {
        if (server == null || affectedTypeIds == null || affectedTypeIds.isEmpty()) return;
        Map<String, Handler> snapshot;
        synchronized (this) {
            snapshot = Map.copyOf(handlers);
        }
        for (String typeId : affectedTypeIds) {
            Handler handler = snapshot.get(typeId);
            if (handler != null) handler.refresh(server);
        }
    }

    public void refreshAll(MinecraftServer server) {
        Map<String, Handler> snapshot;
        synchronized (this) {
            snapshot = Map.copyOf(handlers);
        }
        for (Handler handler : snapshot.values()) handler.refresh(server);
    }

    @FunctionalInterface
    public interface Handler {
        void refresh(MinecraftServer server);
    }
}
