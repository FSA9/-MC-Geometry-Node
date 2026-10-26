package com.mine.geometry_node.client.runtime.model;

import com.mine.geometry_node.client.runtime.model.render.geometry.ModelRenderAsset;
import com.mine.geometry_node.client.runtime.model.render.lod.model.ModelLodCache;
import com.mine.geometry_node.client.runtime.model.render.pipeline.ModelStaticRenderer;
import com.mine.geometry_node.client.runtime.model.runtime.ClientModelCollisions;
import com.mine.geometry_node.client.runtime.model.runtime.ClientModelService;
import com.mine.geometry_node.core.cache.*;
import com.mine.geometry_node.core.engine.system.model.collision.generation.ModelCollisionCache;
import net.minecraft.client.Minecraft;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Supplier;

/** Model-owned control adapters. No user operation removes instances, definitions or active leases. */
public final class ModelCacheIntegration {
    private static final List<CacheRegistry.Registration> REGISTRATIONS = new ArrayList<>();
    private ModelCacheIntegration() {}

    public static synchronized void register(Path gameDirectory) {
        if (!REGISTRATIONS.isEmpty()) return;
        Path models = gameDirectory.resolve("geometry_nodes/caches/model");
        Path collisions = models.resolve("collision");
        register("model_lod", CacheDescriptor.Storage.DISK,
                () -> CacheExecutors.submit(() -> ModelLodCache.diskStats(models)),
                () -> CacheExecutors.run(() -> ModelLodCache.clearDisk(models)), true);
        register("model_collision_disk", CacheDescriptor.Storage.DISK,
                () -> CacheExecutors.submit(() -> ModelCollisionCache.diskStats(collisions)),
                () -> CacheExecutors.run(() -> ModelCollisionCache.clearDisk(collisions)), true);
        // Repository has no idle entries: release-last-lease already disposes them. Its CPU templates,
        // decoded images, BSP, LOD and per-material derived values share that ownership boundary.
        register("model_assets", CacheDescriptor.Storage.MEMORY,
                () -> render(() -> ClientModelService.INSTANCE.assetCacheStats()),
                () -> CompletableFuture.completedFuture(null), true,
                () -> render(() -> CacheClearOutcome.retained(ClientModelService.INSTANCE.assetCacheStats().retainedEntries())));
        register("model_collision_prepared", CacheDescriptor.Storage.MEMORY,
                () -> ClientModelCollisions.INSTANCE.preparedCacheStats(),
                () -> ClientModelCollisions.INSTANCE.clearPreparedCache(), true);
        register("model_render_lookups", CacheDescriptor.Storage.MIXED,
                () -> render(ModelStaticRenderer::lookupCacheStats),
                () -> render(() -> { ModelStaticRenderer.clearLookupCaches(); return null; }), true);
        register("model_gpu", CacheDescriptor.Storage.GPU,
                () -> render(() -> {
                    var usage = ModelRenderAsset.residency().snapshot();
                    // gpuBytes already includes textureBytes and fenced retirements.
                    return new CacheStats(usage.assets(), usage.gpuBytes(), 0,
                            usage.assets() - usage.evictableAssets());
                }),
                () -> render(() -> { ModelRenderAsset.residency().clearInactive(); return null; }), true,
                () -> render(() -> {
                    var ledger = ModelRenderAsset.residency();
                    var before = ledger.snapshot();
                    if (before.evictableAssets() == 0) return CacheClearOutcome.retained(before.assets());
                    ledger.clearInactive();
                    // Selected inactive assets are awaiting fence retirement, not actively pinned.
                    return CacheClearOutcome.completed(before.assets() - before.evictableAssets());
                }));
    }

    private static void register(String id, CacheDescriptor.Storage storage,
            Supplier<CompletionStage<CacheStats>> stats, Supplier<CompletionStage<Void>> clear, boolean clearable) {
        register(id, storage, stats, clear, clearable, null);
    }

    private static void register(String id, CacheDescriptor.Storage storage,
            Supplier<CompletionStage<CacheStats>> stats, Supplier<CompletionStage<Void>> clear, boolean clearable,
            Supplier<CompletionStage<CacheClearOutcome>> outcome) {
        var descriptor = new CacheDescriptor("geometry_node:" + id, "geometry_node.cache." + id,
                CacheDescriptor.Domain.CLIENT, storage, CacheDescriptor.Lifetime.APPLICATION);
        REGISTRATIONS.add(CacheRegistry.GLOBAL.register(descriptor, new ManagedCache() {
            @Override public CompletionStage<CacheStats> stats() { return stats.get(); }
            @Override public boolean supports(CacheClearRequest request) {
                // Shared content files are not server-specific. Never treat a namespace as "all".
                return clearable && request.namespace() == null && request.reason() == CacheClearRequest.Reason.USER;
            }
            @Override public CompletionStage<CacheClearOutcome> clearOutcome(CacheClearRequest request) {
                if (!supports(request)) return CompletableFuture.completedFuture(CacheClearOutcome.skipped());
                return outcome == null ? ManagedCache.super.clearOutcome(request) : outcome.get();
            }
            @Override public CompletionStage<Void> clear(CacheClearRequest request) {
                return supports(request) ? clear.get() : CompletableFuture.failedFuture(
                        new IllegalArgumentException("Unsupported model cache clear scope"));
            }
        }));
    }

    private static <T> CompletableFuture<T> render(Supplier<T> action) {
        var result = new CompletableFuture<T>();
        // Always queue, even when the registry is queried by a render-thread settings callback.
        Minecraft.getInstance().schedule(() -> {
            if (result.isCancelled()) return;
            try { result.complete(action.get()); }
            catch (Exception failure) { result.completeExceptionally(failure); }
        });
        return result;
    }

    public static synchronized void unregister() {
        REGISTRATIONS.forEach(CacheRegistry.Registration::close);
        REGISTRATIONS.clear();
    }
}
