package com.mine.geometry_node.core.engine.system.asset;

import net.minecraft.server.MinecraftServer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

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

    public CompletableFuture<Void> refresh(MinecraftServer server, Set<String> affectedTypeIds,
                                           Set<String> affectedPaths, boolean directoryScope) {
        if (server == null || affectedTypeIds == null || affectedTypeIds.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        Map<String, Handler> snapshot;
        synchronized (this) {
            snapshot = Map.copyOf(handlers);
        }
        java.util.LinkedHashSet<Handler> affectedHandlers = new java.util.LinkedHashSet<>();
        for (String typeId : affectedTypeIds) {
            Handler handler = snapshot.get(typeId);
            if (handler != null) affectedHandlers.add(handler);
        }
        CompletableFuture<?>[] refreshes = affectedHandlers.stream()
                .map(handler -> invoke(handler, server, affectedPaths, directoryScope))
                .toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(refreshes);
    }

    public CompletableFuture<Void> refreshAll(MinecraftServer server) {
        Map<String, Handler> snapshot;
        synchronized (this) {
            snapshot = Map.copyOf(handlers);
        }
        CompletableFuture<?>[] refreshes = new java.util.LinkedHashSet<>(snapshot.values()).stream()
                .map(handler -> invoke(handler, server, Set.of(), true))
                .toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(refreshes);
    }

    private static CompletableFuture<Void> invoke(Handler handler, MinecraftServer server,
                                                   Set<String> affectedPaths, boolean directoryScope) {
        try {
            CompletionStage<Void> stage = handler.refresh(
                    server, affectedPaths == null ? Set.of() : Set.copyOf(affectedPaths), directoryScope);
            return stage == null ? CompletableFuture.completedFuture(null) : stage.toCompletableFuture();
        } catch (Throwable error) {
            return CompletableFuture.failedFuture(error);
        }
    }

    @FunctionalInterface
    public interface Handler {
        CompletionStage<Void> refresh(MinecraftServer server, Set<String> affectedPaths,
                                      boolean directoryScope);
    }
}
