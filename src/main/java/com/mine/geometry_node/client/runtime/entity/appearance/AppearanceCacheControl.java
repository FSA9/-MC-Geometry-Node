package com.mine.geometry_node.client.runtime.entity.appearance;

import com.mine.geometry_node.core.cache.CacheClearOutcome;
import com.mine.geometry_node.core.cache.CacheClearRequest;
import com.mine.geometry_node.core.cache.CacheStats;
import com.mine.geometry_node.core.cache.ManagedCache;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Cache control for rebuildable sessions only. Standalone previews remain their owner's live resources. */
final class AppearanceCacheControl implements ManagedCache {
    private final Consumer<Runnable> queue;
    private final IntSupplier count;
    private final LongSupplier generation;
    private final Runnable clearSessions;

    AppearanceCacheControl(Consumer<Runnable> queue, IntSupplier count, LongSupplier generation, Runnable clearSessions) {
        this.queue = queue;
        this.count = count;
        this.generation = generation;
        this.clearSessions = clearSessions;
    }

    @Override public CompletionStage<CacheStats> stats() {
        return dispatch(() -> CacheStats.memory(count.getAsInt(), -1));
    }

    @Override public CompletionStage<Void> clear(CacheClearRequest request) {
        return clearOutcome(request).thenApply(ignored -> null);
    }

    @Override public CompletionStage<CacheClearOutcome> clearOutcome(CacheClearRequest request) {
        long expected = generation.getAsLong();
        return dispatch(() -> {
            if (!supports(request) || expected != generation.getAsLong()) return CacheClearOutcome.skipped();
            clearSessions.run();
            return CacheClearOutcome.completed(0);
        });
    }

    private <T> CompletionStage<T> dispatch(Supplier<T> action) {
        var result = new CompletableFuture<T>();
        try {
            queue.accept(() -> {
                try { result.complete(action.get()); }
                catch (RuntimeException failure) {
                    AppearanceFailures.rethrowFatal(failure);
                    result.completeExceptionally(failure);
                }
            });
        } catch (RuntimeException failure) {
            AppearanceFailures.rethrowFatal(failure);
            result.completeExceptionally(failure);
        }
        return result;
    }
}
