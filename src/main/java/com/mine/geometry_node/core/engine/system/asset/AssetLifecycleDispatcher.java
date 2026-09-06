package com.mine.geometry_node.core.engine.system.asset;

import net.minecraft.server.MinecraftServer;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

/** Dispatches committed file changes to capabilities declared by the affected asset types. */
public final class AssetLifecycleDispatcher {
    public static final AssetLifecycleDispatcher INSTANCE = new AssetLifecycleDispatcher();

    private AssetLifecycleDispatcher() {
    }

    public CompletableFuture<Void> refresh(MinecraftServer server, Set<String> affectedTypeIds,
                                           Set<String> affectedPaths, boolean directoryScope) {
        if (server == null || affectedTypeIds == null || affectedTypeIds.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        Set<AssetLifecycleHandler> affectedHandlers = new LinkedHashSet<>();
        for (String typeId : affectedTypeIds) {
            AssetTypeDefinition definition = AssetTypeCatalog.definition(typeId);
            if (definition != null) definition.lifecycleHandler().ifPresent(affectedHandlers::add);
        }
        CompletableFuture<?>[] refreshes = affectedHandlers.stream()
                .map(handler -> invoke(handler, server, affectedPaths, directoryScope))
                .toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(refreshes);
    }

    public CompletableFuture<Void> refreshAll(MinecraftServer server) {
        CompletableFuture<?>[] refreshes = AssetTypeCatalog.definitions().stream()
                .flatMap(definition -> definition.lifecycleHandler().stream())
                .collect(Collectors.toCollection(LinkedHashSet::new)).stream()
                .map(handler -> invoke(handler, server, Set.of(), true))
                .toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(refreshes);
    }

    private static CompletableFuture<Void> invoke(AssetLifecycleHandler handler, MinecraftServer server,
                                                   Set<String> affectedPaths, boolean directoryScope) {
        try {
            CompletionStage<Void> stage = handler.refresh(
                    server, affectedPaths == null ? Set.of() : Set.copyOf(affectedPaths), directoryScope);
            return stage == null ? CompletableFuture.completedFuture(null) : stage.toCompletableFuture();
        } catch (Throwable error) {
            return CompletableFuture.failedFuture(error);
        }
    }
}
