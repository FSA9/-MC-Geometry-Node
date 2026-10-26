package com.mine.geometry_node.core.engine.system.image;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.core.cache.CacheClearRequest;
import com.mine.geometry_node.core.cache.CacheClearOutcome;
import com.mine.geometry_node.core.cache.CacheDescriptor;
import com.mine.geometry_node.core.cache.CacheExecutors;
import com.mine.geometry_node.core.cache.CacheRegistry;
import com.mine.geometry_node.core.cache.CacheStats;
import com.mine.geometry_node.core.cache.ManagedCache;
import com.mine.geometry_node.core.engine.blueprint.runtime.wait.BlueprintExecutionHandle;
import com.mine.geometry_node.core.engine.blueprint.runtime.wait.BlueprintExternalWaitHandler;
import com.mine.geometry_node.core.engine.blueprint.runtime.wait.BlueprintExternalWaitRequest;
import com.mine.geometry_node.core.engine.runtime.ServerEngine;
import com.mine.geometry_node.core.engine.service.GraphEngineServices;
import com.mine.geometry_node.core.engine.system.asset.repository.ServerAssetPaths;
import com.mine.geometry_node.core.utils.io.BoundedIoExecutor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** Loads server-owned image assets without blocking the server thread. */
public final class ServerImageAssetService implements ServerEngine, BlueprintExternalWaitHandler {
    public static final String ID = "geometry_node:server_image_asset";
    public static final ServerImageAssetService INSTANCE = new ServerImageAssetService();

    private static final int IO_QUEUE_CAPACITY = 64;
    private static final int MAX_FAILURE_LOG_KEYS = 256;
    private static final long FAILURE_LOG_INTERVAL_NANOS = Duration.ofMinutes(1).toNanos();

    private final Map<MinecraftServer, ServerState> states = new ConcurrentHashMap<>();
    private final Map<BlueprintExecutionHandle, PendingVisual> pendingVisuals = new ConcurrentHashMap<>();
    private final Set<MinecraftServer> stoppedServers = Collections.newSetFromMap(new WeakHashMap<>());

    private ServerImageAssetService() {
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String externalWaitId() {
        return ID;
    }

    @Override
    public void init() {
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> {
            synchronized (stoppedServers) {
                stoppedServers.remove(event.getServer());
            }
        });
    }

    public CompletableFuture<GraphEngineServices.VisualAsset> loadAsync(
            MinecraftServer server, String relativePath) {
        if (server == null) return CompletableFuture.failedFuture(new IOException("Missing server"));
        ServerState state;
        synchronized (stoppedServers) {
            if (stoppedServers.contains(server)) {
                return CompletableFuture.failedFuture(new IOException("Server is stopping"));
            }
            // State creation and shutdown admission share one lock: no owner can register after stop.
            state = states.computeIfAbsent(server, ServerState::new);
        }
        long generation = state.generation;
        long cacheEpoch = state.cacheEpoch();
        String key = relativePath == null ? "" : relativePath.trim();
        CompletableFuture<GraphEngineServices.VisualAsset> existing = state.inFlight.get(key);
        if (existing != null) return existing;
        CompletableFuture<GraphEngineServices.VisualAsset> promise = new CompletableFuture<>();
        existing = state.inFlight.putIfAbsent(key, promise);
        if (existing != null) return existing;

        CompletableFuture<GraphEngineServices.VisualAsset> worker = state.io.submit(() ->
                loadAndCache(state, generation, cacheEpoch, key));
        worker.whenComplete((asset, error) -> {
            if (error != null) promise.completeExceptionally(error);
            else promise.complete(asset);
        });
        promise.whenComplete((asset, error) -> {
            state.inFlight.remove(key, promise);
            if (promise.isCancelled()) worker.cancel(false);
        });
        return promise;
    }

    @Override
    public boolean beginExternalWait(BlueprintExecutionHandle handle, BlueprintExternalWaitRequest request) {
        if (!(request instanceof ImageVisualRequest visualRequest)) return false;
        ServerLevel level = handle.level();
        if (level == null) return false;
        MinecraftServer server = level.getServer();
        CompletableFuture<GraphEngineServices.VisualAsset> future = loadAsync(server, visualRequest.relativePath());
        ServerState state = states.get(server);
        if (state == null) return false;
        PendingVisual pending = new PendingVisual(server, state, state.generation, visualRequest);
        pendingVisuals.put(handle, pending);
        future.whenComplete((asset, error) -> completeAsync(handle, pending, asset, error));
        return true;
    }

    private void completeAsync(BlueprintExecutionHandle handle, PendingVisual pending,
                               @Nullable GraphEngineServices.VisualAsset asset, @Nullable Throwable error) {
        if (!isCurrent(handle, pending)) return;
        pending.server.execute(() -> {
            if (!isCurrent(handle, pending) || !pendingVisuals.remove(handle, pending) || !handle.isActive()) return;
            ServerLevel level = handle.level();
            if (level == null || level.getServer() != pending.server) return;
            if (error == null && asset != null) {
                ImageVisualRequest request = pending.request;
                var extraData = request.extraData().copy();
                extraData.putString("imageRef", asset.assetId());
                GraphEngineServices.INSTANCE.visualSink().broadcast(new GraphEngineServices.VisualEffect(
                        level, "image_visual", 0xFFFFFFFF, request.durationTicks(), request.expressions(),
                        extraData, request.center(), request.radius(), java.util.List.of(asset)));
            } else {
                reportFailure(pending.server, pending.request.relativePath(), error);
            }
            handle.resume(ImageVisualRequest.NEXT_PORT);
        });
    }

    private boolean isCurrent(BlueprintExecutionHandle handle, PendingVisual pending) {
        return pendingVisuals.get(handle) == pending
                && states.get(pending.server) == pending.state
                && pending.state.generation == pending.generation;
    }

    @Override
    public void completeExternalWait(BlueprintExecutionHandle handle, String outputPortName, Completion completion) {
        pendingVisuals.remove(handle);
    }

    @Override
    public void endExternalWait(BlueprintExecutionHandle handle, @Nullable String reason) {
        pendingVisuals.remove(handle);
    }

    @Override
    public String interruptionOutputPort(BlueprintExternalWaitRequest request) {
        return request instanceof ImageVisualRequest ? ImageVisualRequest.NEXT_PORT : null;
    }

    @Override
    public void shutdown(MinecraftServer server) {
        synchronized (stoppedServers) {
            stoppedServers.add(server);
        }
        pendingVisuals.forEach((handle, pending) -> {
            if (pending.server == server) pendingVisuals.remove(handle, pending);
        });
        ServerState state = states.remove(server);
        if (state != null) state.close();
    }

    private static GraphEngineServices.VisualAsset loadAndCache(
            ServerState state, long generation, long cacheEpoch, String relativePath) throws IOException {
        if (state.generation != generation) throw new IOException("Server image load was cancelled");
        Path root = ServerAssetPaths.root(state.server);
        Path path = ServerAssetPaths.resolveUnderRoot(root, relativePath, false);
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) {
            throw new IOException("Server image does not exist: " + relativePath);
        }
        Path realRoot = root.toRealPath();
        Path realPath = path.toRealPath();
        if (!realPath.startsWith(realRoot)) {
            throw new IOException("Server image escapes geometry_nodes: " + relativePath);
        }
        int maxEncodedBytes = RuntimeImageServerConfig.maxEncodedBytes();
        long size = Files.size(realPath);
        if (size <= 0 || size > maxEncodedBytes) {
            throw new IOException("Server image has an unsupported file size: " + size);
        }
        FileTime modified = Files.getLastModifiedTime(realPath);
        GraphEngineServices.VisualAsset cached = state.cached(realPath, size, modified);
        if (cached != null) return cached;

        byte[] data;
        // A file can grow after metadata inspection; bound the read itself, not only the earlier size.
        try (var stream = Files.newInputStream(realPath)) {
            data = stream.readNBytes(Math.addExact(maxEncodedBytes, 1));
        }
        ImageAssetValidator.validateImage(data,
                maxEncodedBytes,
                RuntimeImageServerConfig.maxDimension(),
                RuntimeImageServerConfig.maxPixels());
        GraphEngineServices.VisualAsset asset = new GraphEngineServices.VisualAsset(
                ImageAssetValidator.contentId(data), data);
        if (state.generation != generation) throw new IOException("Server image load was cancelled");
        state.cache(realPath, new CachedAsset(size, modified, asset), generation, cacheEpoch);
        return asset;
    }

    private void reportFailure(MinecraftServer server, String path, @Nullable Throwable error) {
        ServerState state = states.get(server);
        if (state == null || !state.shouldLog(path)) return;
        Throwable cause = error;
        while (cause != null && cause.getCause() != null) cause = cause.getCause();
        String detail = cause == null || cause.getMessage() == null
                ? "unknown error" : cause.getMessage();
        GeometryNode.LOGGER.warn("Unable to display server image '{}': {}", path, detail);
    }

    private record CachedAsset(long size, FileTime modified, GraphEngineServices.VisualAsset asset) {
        private long bytes() {
            return asset.data().length;
        }
    }

    private record PendingVisual(MinecraftServer server, ServerState state, long generation,
                                 ImageVisualRequest request) {
    }

    private static final class ServerState {
        private final MinecraftServer server;
        private final BoundedIoExecutor io;
        private final Map<String, CompletableFuture<GraphEngineServices.VisualAsset>> inFlight =
                new ConcurrentHashMap<>();
        private final LinkedHashMap<Path, CachedAsset> cache = new LinkedHashMap<>(16, 0.75f, true);
        private final LinkedHashMap<String, Long> failureLogs = new LinkedHashMap<>(16, 0.75f, true);
        private volatile long generation;
        private long cachedBytes;
        private long cacheEpoch;
        private final CacheRegistry.Registration cacheRegistration;

        private ServerState(MinecraftServer server) {
            this.server = server;
            this.io = new BoundedIoExecutor("GeometryNode-ServerImage-IO", 1, IO_QUEUE_CAPACITY);
            this.cacheRegistration = CacheRegistry.GLOBAL.register(new CacheDescriptor("geometry_node:runtime.server_images",
                    "geometry_node.cache.runtime.server_images", CacheDescriptor.Domain.SERVER,
                    CacheDescriptor.Storage.MEMORY, CacheDescriptor.Lifetime.CONNECTION), new ManagedCache() {
                @Override public java.util.concurrent.CompletionStage<CacheStats> stats() {
                    return CacheExecutors.submit(ServerState.this::cacheStats);
                }
                @Override public java.util.concurrent.CompletionStage<Void> clear(CacheClearRequest request) {
                    if (!supports(request)) return CompletableFuture.failedFuture(
                            new IllegalArgumentException("Server image cache does not support scoped clearing"));
                    return clearOutcome(request).thenApply(ignored -> null);
                }
                @Override public java.util.concurrent.CompletionStage<CacheClearOutcome> clearOutcome(CacheClearRequest request) {
                    if (!supports(request)) return CompletableFuture.failedFuture(
                            new IllegalArgumentException("Server image cache does not support scoped clearing"));
                    return CacheExecutors.submit(ServerState.this::clearCachedAssets);
                }
            });
        }

        @Nullable
        private synchronized GraphEngineServices.VisualAsset cached(
                Path path, long size, FileTime modified) {
            CachedAsset value = cache.get(path);
            if (value == null) return null;
            if (value.size == size && value.modified.equals(modified)) return value.asset;
            cache.remove(path);
            cachedBytes -= value.bytes();
            return null;
        }

        private synchronized long cacheEpoch() { return cacheEpoch; }

        private synchronized CacheStats cacheStats() {
            int pending = inFlight.size();
            return new CacheStats(cache.size() + pending, cachedBytes, 0, pending);
        }

        /** Evict reusable validated bytes only. In-flight requests and blueprint continuations stay alive. */
        private synchronized CacheClearOutcome clearCachedAssets() {
            boolean hadCachedData = !cache.isEmpty() || !failureLogs.isEmpty();
            cacheEpoch++;
            cache.clear();
            failureLogs.clear();
            cachedBytes = 0;
            long retained = inFlight.size();
            return !hadCachedData && retained > 0 ? CacheClearOutcome.retained(retained)
                    : CacheClearOutcome.completed(retained);
        }

        private synchronized void cache(Path path, CachedAsset value, long expectedGeneration, long expectedCacheEpoch) {
            if (generation != expectedGeneration || cacheEpoch != expectedCacheEpoch) return;
            CachedAsset previous = cache.put(path, value);
            if (previous != null) cachedBytes -= previous.bytes();
            cachedBytes += value.bytes();
            Iterator<Map.Entry<Path, CachedAsset>> iterator = cache.entrySet().iterator();
            while ((cache.size() > RuntimeImageServerConfig.cacheMaxEntries()
                    || cachedBytes > RuntimeImageServerConfig.cacheMaxBytes()) && iterator.hasNext()) {
                Map.Entry<Path, CachedAsset> eldest = iterator.next();
                cachedBytes -= eldest.getValue().bytes();
                iterator.remove();
            }
        }

        private synchronized boolean shouldLog(String path) {
            long now = System.nanoTime();
            Long previous = failureLogs.get(path);
            if (previous != null && now - previous < FAILURE_LOG_INTERVAL_NANOS) return false;
            failureLogs.put(path, now);
            while (failureLogs.size() > MAX_FAILURE_LOG_KEYS) {
                failureLogs.remove(failureLogs.keySet().iterator().next());
            }
            return true;
        }

        private void close() {
            cacheRegistration.close();
            synchronized (this) { generation++; clearCachedAssets(); }
            inFlight.values().forEach(future -> future.cancel(false));
            inFlight.clear();
            io.close();
        }
    }
}
