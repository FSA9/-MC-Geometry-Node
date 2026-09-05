package com.mine.geometry_node.core.engine.system.visual.image;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.core.engine.blueprint.runtime.wait.BlueprintExecutionHandle;
import com.mine.geometry_node.core.engine.blueprint.runtime.wait.BlueprintExternalWaitHandler;
import com.mine.geometry_node.core.engine.blueprint.runtime.wait.BlueprintExternalWaitRequest;
import com.mine.geometry_node.core.engine.runtime.ServerEngine;
import com.mine.geometry_node.core.engine.service.GraphEngineServices;
import com.mine.geometry_node.core.engine.system.asset.ServerAssetPaths;
import com.mine.geometry_node.core.engine.system.asset.transfer.io.AssetTransferIoExecutor;
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

    private static final int MAX_CACHE_ENTRIES = 128;
    private static final long MAX_CACHE_BYTES = 64L * 1024L * 1024L;
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
        synchronized (stoppedServers) {
            if (stoppedServers.contains(server)) {
                return CompletableFuture.failedFuture(new IOException("Server is stopping"));
            }
        }
        ServerState state = states.computeIfAbsent(server, ServerState::new);
        long generation = state.generation;
        String key = relativePath == null ? "" : relativePath.trim();
        CompletableFuture<GraphEngineServices.VisualAsset> existing = state.inFlight.get(key);
        if (existing != null) return existing;
        CompletableFuture<GraphEngineServices.VisualAsset> promise = new CompletableFuture<>();
        existing = state.inFlight.putIfAbsent(key, promise);
        if (existing != null) return existing;

        CompletableFuture<GraphEngineServices.VisualAsset> worker = state.io.submit(() ->
                loadAndCache(state, generation, key));
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
            ServerState state, long generation, String relativePath) throws IOException {
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
        long size = Files.size(realPath);
        if (size <= 0 || size > ImageAssetValidator.MAX_ENCODED_BYTES) {
            throw new IOException("Server image has an unsupported file size: " + size);
        }
        FileTime modified = Files.getLastModifiedTime(realPath);
        GraphEngineServices.VisualAsset cached = state.cached(realPath, size, modified);
        if (cached != null) return cached;

        byte[] data = Files.readAllBytes(realPath);
        ImageAssetValidator.validateImage(data);
        GraphEngineServices.VisualAsset asset = new GraphEngineServices.VisualAsset(
                ImageAssetValidator.contentId(data), data);
        if (state.generation != generation) throw new IOException("Server image load was cancelled");
        state.cache(realPath, new CachedAsset(size, modified, asset));
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
        private final AssetTransferIoExecutor io;
        private final Map<String, CompletableFuture<GraphEngineServices.VisualAsset>> inFlight =
                new ConcurrentHashMap<>();
        private final LinkedHashMap<Path, CachedAsset> cache = new LinkedHashMap<>(16, 0.75f, true);
        private final LinkedHashMap<String, Long> failureLogs = new LinkedHashMap<>(16, 0.75f, true);
        private volatile long generation;
        private long cachedBytes;

        private ServerState(MinecraftServer server) {
            this.server = server;
            this.io = new AssetTransferIoExecutor("GeometryNode-ServerImage-IO", 1, IO_QUEUE_CAPACITY);
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

        private synchronized void cache(Path path, CachedAsset value) {
            CachedAsset previous = cache.put(path, value);
            if (previous != null) cachedBytes -= previous.bytes();
            cachedBytes += value.bytes();
            Iterator<Map.Entry<Path, CachedAsset>> iterator = cache.entrySet().iterator();
            while ((cache.size() > MAX_CACHE_ENTRIES || cachedBytes > MAX_CACHE_BYTES) && iterator.hasNext()) {
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
            generation++;
            inFlight.values().forEach(future -> future.cancel(false));
            inFlight.clear();
            io.close();
            synchronized (this) {
                cache.clear();
                failureLogs.clear();
                cachedBytes = 0L;
            }
        }
    }
}
