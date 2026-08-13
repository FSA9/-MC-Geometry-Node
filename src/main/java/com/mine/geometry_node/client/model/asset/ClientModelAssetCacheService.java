package com.mine.geometry_node.client.model.asset;

import com.mine.geometry_node.client.asset.preview.*;
import com.mine.geometry_node.client.asset.transfer.*;
import com.mine.geometry_node.core.engine.system.asset.transfer.io.AssetTransferIoExecutor;
import com.mine.geometry_node.core.engine.system.asset.transfer.model.*;
import icyllis.modernui.mc.MuiModApi;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Exact-subscription materializer for remote GLB bytes. It owns no importer or GPU state. */
public final class ClientModelAssetCacheService implements AutoCloseable {
    public static final ClientModelAssetCacheService INSTANCE = new ClientModelAssetCacheService();

    private final ClientModelAssetCache cache = new ClientModelAssetCache();
    private final AssetTransferIoExecutor io = new AssetTransferIoExecutor("GeometryNode-ModelAsset-Cache", 1, 32);
    private final Map<Key, Entry> entries = new LinkedHashMap<>();
    private final Set<String> initializedServers = new HashSet<>();

    private ClientModelAssetCacheService() {}

    public synchronized Subscription subscribe(RemoteModelAssetRevision revision, Listener listener) {
        Objects.requireNonNull(revision, "revision");
        Objects.requireNonNull(listener, "listener");
        Key key = new Key(ClientAssetPreviewServerIdentity.current(), revision);
        initializeServer(key.serverIdentity);
        Entry entry = entries.computeIfAbsent(key, Entry::new);
        UUID subscriber = UUID.randomUUID();
        entry.listeners.put(subscriber, listener);
        if (!entry.started) {
            entry.started = true;
            resolve(entry);
        }
        return () -> unsubscribe(key, subscriber);
    }

    public synchronized void resetConnection() {
        for (Entry entry : entries.values()) cancel(entry);
        entries.clear();
        initializedServers.clear();
    }

    public CompletableFuture<Void> invalidateCurrent(Collection<String> remotePaths) {
        return invalidate(ClientAssetPreviewServerIdentity.current(), remotePaths);
    }

    public CompletableFuture<Void> invalidate(String serverIdentity, Collection<String> remotePaths) {
        String server = Objects.requireNonNull(serverIdentity, "serverIdentity");
        List<String> paths = remotePaths == null ? List.of() : List.copyOf(remotePaths);
        synchronized (this) {
            entries.entrySet().removeIf(item -> {
                boolean matches = paths.stream().anyMatch(path -> contains(path, item.getKey().revision.remotePath()));
                if (matches) cancel(item.getValue());
                return matches;
            });
        }
        return io.run(() -> cache.invalidate(server, paths));
    }

    public CompletableFuture<Void> clearCurrentServerCache() {
        String server = ClientAssetPreviewServerIdentity.current();
        synchronized (this) {
            for (Entry entry : entries.values()) if (entry.key.serverIdentity.equals(server)) cancel(entry);
            entries.entrySet().removeIf(item -> item.getKey().serverIdentity.equals(server));
            initializedServers.remove(server);
        }
        return io.run(() -> {
            cache.clear(server);
            cache.clearStaging(server);
        });
    }

    public CompletableFuture<Void> clearAllCaches() {
        resetConnection();
        return io.run(cache::clearAll);
    }

    private void resolve(Entry entry) {
        io.submit(() -> cache.find(entry.key.serverIdentity, entry.key.revision))
                .whenComplete((cached, error) -> post(() -> handleLookup(entry, cached, error)));
    }

    private void initializeServer(String serverIdentity) {
        if (!initializedServers.add(serverIdentity)) return;
        io.run(() -> cache.clearStaging(serverIdentity));
    }

    private synchronized void handleLookup(Entry entry, Optional<MaterializedModelAsset> cached, Throwable error) {
        if (entries.get(entry.key) != entry || entry.listeners.isEmpty()) return;
        if (error == null && cached.isPresent()) {
            complete(entry, cached.get());
            return;
        }
        final Path target;
        try { target = cache.staging(entry.key.serverIdentity, UUID.randomUUID()); }
        catch (Exception exception) { fail(entry, rootMessage(exception)); return; }
        entry.staging = target;
        entry.jobId = ClientAssetTransferService.INSTANCE.submit(List.of(ClientAssetTransferRequest.download(
                entry.key.revision.remotePath(), target, AssetTransferConflictPolicy.OVERWRITE)));
        ClientAssetTransferService.INSTANCE.completion(entry.jobId).whenComplete((snapshot, transferError) -> {
            if (transferError != null || snapshot == null || snapshot.files().stream()
                    .anyMatch(file -> file.state() != AssetTransferState.COMPLETED)) {
                post(() -> fail(entry, transferError == null ? "download_failed" : rootMessage(transferError)));
                return;
            }
            synchronized (this) {
                if (!current(entry)) { discard(entry); return; }
            }
            io.submit(() -> {
                        String hash = cache.validate(target, entry.key.revision);
                        synchronized (ClientModelAssetCacheService.this) {
                            if (!current(entry)) {
                                cache.discard(target);
                                return null;
                            }
                            return cache.publish(target, entry.key.serverIdentity, entry.key.revision, hash);
                        }
                    })
                    .whenComplete((published, commitError) -> post(() -> {
                        if (!current(entry)) discard(entry);
                        else if (commitError != null) fail(entry, rootMessage(commitError));
                        else complete(entry, published);
                    }));
        });
    }

    private synchronized void complete(Entry entry, MaterializedModelAsset asset) {
        if (entries.remove(entry.key) != entry) return;
        for (Listener listener : entry.listeners.values()) listener.available(asset);
        entry.listeners.clear();
    }

    private synchronized void fail(Entry entry, String detail) {
        if (entries.remove(entry.key) != entry) return;
        for (Listener listener : entry.listeners.values()) listener.unavailable(detail);
        entry.listeners.clear();
    }

    private synchronized void unsubscribe(Key key, UUID subscriber) {
        Entry entry = entries.get(key);
        if (entry == null) return;
        entry.listeners.remove(subscriber);
        if (!entry.listeners.isEmpty()) return;
        entries.remove(key, entry);
        cancel(entry);
    }

    private void cancel(Entry entry) {
        if (entry.jobId != null) ClientAssetTransferService.INSTANCE.cancel(entry.jobId);
        entry.listeners.clear();
        discard(entry);
    }

    private synchronized boolean current(Entry entry) {
        return entries.get(entry.key) == entry && !entry.listeners.isEmpty();
    }

    private void discard(Entry entry) { io.run(() -> cache.discard(entry.staging)); }

    private static boolean contains(String root, String candidate) {
        String normalized;
        try { normalized = com.mine.geometry_node.core.engine.graph.storage.GraphPathMapper
                .normalizeRelativePath(root == null ? "" : root, false); }
        catch (RuntimeException ignored) { return false; }
        return candidate.equals(normalized) || candidate.startsWith(normalized + "/");
    }
    private static void post(Runnable task) { MuiModApi.postToUiThread(task); }
    private static String rootMessage(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null) root = root.getCause();
        return root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
    }

    @Override public void close() { resetConnection(); io.close(); }

    public interface Listener {
        void available(MaterializedModelAsset asset);
        void unavailable(String detail);
    }
    @FunctionalInterface public interface Subscription extends AutoCloseable { @Override void close(); }
    private record Key(String serverIdentity, RemoteModelAssetRevision revision) {}
    private static final class Entry {
        private final Key key;
        private final Map<UUID, Listener> listeners = new LinkedHashMap<>();
        private boolean started;
        private UUID jobId;
        private Path staging;
        private Entry(Key key) { this.key = key; }
    }
}
