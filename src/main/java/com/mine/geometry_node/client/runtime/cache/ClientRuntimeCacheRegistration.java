package com.mine.geometry_node.client.runtime.cache;

import com.mine.geometry_node.core.cache.CacheClearRequest;
import com.mine.geometry_node.core.cache.CacheClearOutcome;
import com.mine.geometry_node.core.cache.CacheDescriptor;
import com.mine.geometry_node.core.cache.CacheRegistry;
import com.mine.geometry_node.core.cache.CacheStats;
import com.mine.geometry_node.core.cache.ManagedCache;
import net.minecraft.client.Minecraft;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/** Adapts application-owned runtime caches; all inspection/mutation runs between client tasks. */
public final class ClientRuntimeCacheRegistration {
    private ClientRuntimeCacheRegistration() {}

    public static CacheRegistry.Registration register(String id, CacheDescriptor.Storage storage,
                                                       Supplier<CacheStats> stats, Runnable clear) {
        return CacheRegistry.GLOBAL.register(new CacheDescriptor("geometry_node:" + id, "geometry_node.cache." + id,
                CacheDescriptor.Domain.CLIENT, storage, CacheDescriptor.Lifetime.APPLICATION), new ManagedCache() {
            @Override public CompletionStage<CacheStats> stats() { return onClient(stats); }
            @Override public CompletionStage<Void> clear(CacheClearRequest request) {
                return clearOutcome(request).thenApply(ignored -> null);
            }
            @Override public CompletionStage<CacheClearOutcome> clearOutcome(CacheClearRequest request) {
                if (!supports(request)) return CompletableFuture.failedFuture(
                        new IllegalArgumentException("Runtime cache does not support scoped clearing"));
                return onClient(() -> {
                    CacheStats before = stats.get();
                    clear.run();
                    long retained = stats.get().retainedEntries();
                    return before.retainedEntries() > 0 && before.entries() == before.retainedEntries()
                            ? CacheClearOutcome.retained(retained) : CacheClearOutcome.completed(retained);
                });
            }
        });
    }

    private static <T> CompletableFuture<T> onClient(Supplier<T> task) {
        CompletableFuture<T> result = new CompletableFuture<>();
        try {
            // schedule always queues, including when invoked on the render thread. Never evict mid-draw.
            Minecraft.getInstance().schedule(() -> {
                if (result.isCancelled()) return;
                try { result.complete(task.get()); }
                catch (Exception failure) { result.completeExceptionally(failure); }
            });
        } catch (Exception failure) { result.completeExceptionally(failure); }
        return result;
    }
}
