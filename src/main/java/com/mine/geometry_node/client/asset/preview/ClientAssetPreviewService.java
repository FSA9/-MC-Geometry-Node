package com.mine.geometry_node.client.asset.preview;

import com.mine.geometry_node.client.asset.preview.protocol.ClientAssetPreviewProtocol;
import com.mine.geometry_node.core.engine.system.asset.preview.AssetPreviewDescriptor;
import com.mine.geometry_node.core.engine.system.asset.preview.AssetPreviewResultCode;
import com.mine.geometry_node.core.engine.system.asset.preview.AssetPreviewRevision;
import com.mine.geometry_node.core.engine.system.asset.transfer.io.AssetTransferIoExecutor;
import icyllis.modernui.mc.MuiModApi;
import com.mine.geometry_node.client.ui.persistence.config.ConfigManager;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Subscription-based resolver joining persistent cache hits and shared network requests. */
public final class ClientAssetPreviewService implements AutoCloseable {
    public static final ClientAssetPreviewService INSTANCE = new ClientAssetPreviewService();

    private final ClientAssetPreviewCache cache = new ClientAssetPreviewCache();
    private final AssetTransferIoExecutor io = new AssetTransferIoExecutor("GeometryNode-Preview-ClientIO", 2, 64);
    private final Map<CacheKey, Entry> entries = new LinkedHashMap<>();

    private ClientAssetPreviewService() {
        ConfigManager.INSTANCE.addChangeListener(config -> enforceCacheLimit());
    }

    public synchronized Subscription subscribe(AssetPreviewRevision revision, Listener listener) {
        if (revision == null) throw new IllegalArgumentException("revision cannot be null");
        if (listener == null) throw new IllegalArgumentException("listener cannot be null");
        String serverIdentity = ClientAssetPreviewServerIdentity.current();
        CacheKey key = new CacheKey(serverIdentity, revision);
        Entry entry = entries.computeIfAbsent(key, ignored -> new Entry(key));
        UUID subscriberId = UUID.randomUUID();
        entry.listeners.put(subscriberId, listener);
        if (!entry.started) {
            entry.started = true;
            resolveCached(entry);
        }
        return () -> unsubscribe(key, subscriberId);
    }

    public synchronized void resetConnection() {
        entries.clear();
        ClientAssetPreviewProtocol.reset();
    }

    public CompletableFuture<Void> clearCurrentServerCache() {
        String serverIdentity = ClientAssetPreviewServerIdentity.current();
        cancelServerEntries(serverIdentity);
        return io.run(() -> cache.clear(serverIdentity));
    }

    public CompletableFuture<Void> clearAllCaches() {
        synchronized (this) {
            for (Entry entry : entries.values()) {
                if (entry.requestId != null) ClientAssetPreviewProtocol.cancel(entry.requestId);
            }
            entries.clear();
        }
        return io.run(cache::clearAll);
    }

    public CompletableFuture<Long> cacheSizeBytes() {
        return io.submit(cache::sizeAll);
    }

    public Path cacheLocation() {
        return cache.location();
    }

    public CompletableFuture<Void> enforceCacheLimit() {
        return io.run(cache::enforceConfiguredLimit);
    }

    private void resolveCached(Entry entry) {
        io.submit(() -> cache.find(entry.key.serverIdentity, entry.key.revision))
                .whenComplete((cached, error) -> post(() -> handleCacheLookup(entry, cached, error)));
    }

    private synchronized void handleCacheLookup(Entry entry,
                                                Optional<ClientAssetPreviewCache.CachedPreview> cached,
                                                Throwable error) {
        if (entries.get(entry.key) != entry || entry.listeners.isEmpty()) return;
        if (error == null && cached != null && cached.isPresent()) {
            complete(entry, cached.get());
            return;
        }
        entry.requestId = ClientAssetPreviewProtocol.request(entry.key.revision,
                new ClientAssetPreviewProtocol.Listener() {
                    @Override
                    public void completed(AssetPreviewDescriptor descriptor, byte[] encoded) {
                        persistDownloaded(entry, descriptor, encoded);
                    }

                    @Override
                    public void failed(AssetPreviewResultCode code, String detail) {
                        post(() -> fail(entry, code, detail));
                    }
                });
    }

    private void persistDownloaded(Entry entry, AssetPreviewDescriptor descriptor, byte[] encoded) {
        io.submit(() -> cache.store(entry.key.serverIdentity, descriptor, encoded))
                .whenComplete((cached, error) -> post(() -> {
                    if (error != null) fail(entry, AssetPreviewResultCode.IO_FAILURE, "cache_write_failed");
                    else complete(entry, cached);
                }));
    }

    private synchronized void complete(Entry entry, ClientAssetPreviewCache.CachedPreview cached) {
        if (entries.remove(entry.key) != entry) return;
        for (Listener listener : entry.listeners.values()) {
            listener.available(cached.descriptor(), cached.path());
        }
        entry.listeners.clear();
    }

    private synchronized void fail(Entry entry, AssetPreviewResultCode code, String detail) {
        if (entries.remove(entry.key) != entry) return;
        for (Listener listener : entry.listeners.values()) listener.unavailable(code, detail);
        entry.listeners.clear();
    }

    private synchronized void unsubscribe(CacheKey key, UUID subscriberId) {
        Entry entry = entries.get(key);
        if (entry == null) return;
        entry.listeners.remove(subscriberId);
        if (!entry.listeners.isEmpty()) return;
        entries.remove(key, entry);
        if (entry.requestId != null) ClientAssetPreviewProtocol.cancel(entry.requestId);
    }

    private synchronized void cancelServerEntries(String serverIdentity) {
        var iterator = entries.entrySet().iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next().getValue();
            if (!entry.key.serverIdentity.equals(serverIdentity)) continue;
            iterator.remove();
            if (entry.requestId != null) ClientAssetPreviewProtocol.cancel(entry.requestId);
            entry.listeners.clear();
        }
    }

    private static void post(Runnable task) {
        MuiModApi.postToUiThread(task);
    }

    @Override
    public void close() {
        resetConnection();
        io.close();
    }

    public interface Listener {
        void available(AssetPreviewDescriptor descriptor, Path localArtifact);

        void unavailable(AssetPreviewResultCode code, String detail);
    }

    @FunctionalInterface
    public interface Subscription extends AutoCloseable {
        @Override
        void close();
    }

    private record CacheKey(String serverIdentity, AssetPreviewRevision revision) {
    }

    private static final class Entry {
        private final CacheKey key;
        private final Map<UUID, Listener> listeners = new LinkedHashMap<>();
        private boolean started;
        private UUID requestId;

        private Entry(CacheKey key) {
            this.key = key;
        }
    }
}
