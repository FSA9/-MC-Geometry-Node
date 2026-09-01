package com.mine.geometry_node.core.engine.system.data.library;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.core.engine.graph.value.GraphValueSnapshot;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

/** Server-authoritative access to the remote Data Library database. */
public final class RemoteDataLibraryService {
    public static final RemoteDataLibraryService INSTANCE = new RemoteDataLibraryService();

    private final Map<MinecraftServer, CachedLibrary> caches = new ConcurrentHashMap<>();
    private volatile boolean initialized;

    private RemoteDataLibraryService() {
    }

    public synchronized void init() {
        if (initialized) return;
        initialized = true;
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> {
            try {
                load(event.getServer());
            } catch (IOException exception) {
                GeometryNode.LOGGER.error("Failed to load server Data Library cache", exception);
            }
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> caches.remove(event.getServer()));
    }

    /** Returns the server's immutable in-memory snapshot, loading it at most once per server. */
    public DataLibraryLoadResult refresh(MinecraftServer server) throws IOException {
        return ensureLoaded(server).asResult();
    }

    /**
     * Resolves an entry through the server snapshot. Mutable values are detached for the caller;
     * entity entries remain references in the snapshot and are resolved against the current level.
     */
    @Nullable
    public Object resolve(MinecraftServer server, DataLibraryEntryKey key) {
        try {
            Object value = ensureLoaded(server).document().find(key)
                    .map(DataLibraryEntry::value).orElse(null);
            if (value instanceof DataLibraryEntityReference reference) {
                return reference.resolve(server);
            }
            return GraphValueSnapshot.snapshot(value);
        } catch (IOException | RuntimeException exception) {
            GeometryNode.LOGGER.warn("Unable to resolve Data Library entry {}", key, exception);
            return null;
        }
    }

    public DataLibraryLoadResult create(MinecraftServer server, DataLibraryDocument incoming) throws IOException {
        DataLibraryEntryKey key = requireSingle(incoming);
        return update(server, document -> {
            if (document.find(key).isPresent()) {
                throw new IllegalStateException("Data Library entry already exists: " + key);
            }
            document.put(key.type(), incoming.find(key).orElseThrow());
        });
    }

    public DataLibraryLoadResult update(MinecraftServer server, DataLibraryDocument incoming) throws IOException {
        DataLibraryEntryKey key = requireSingle(incoming);
        return update(server, document -> {
            if (document.find(key).isEmpty()) {
                throw new IllegalStateException("Data Library entry does not exist: " + key);
            }
            document.put(key.type(), incoming.find(key).orElseThrow());
        });
    }

    public DataLibraryLoadResult delete(MinecraftServer server, Set<DataLibraryEntryKey> keys) throws IOException {
        return update(server, document -> document.removeAll(keys));
    }

    private synchronized DataLibraryLoadResult update(MinecraftServer server, DocumentMutation mutation) throws IOException {
        DataLibraryLoadResult result = DataLibraryFileStore.updateAtomic(
                ServerDataLibraryPaths.file(server), server.overworld().registryAccess(), document -> {
                    mutation.apply(document);
                    return document;
                });
        // Publish only after updateAtomic has completed its atomic move successfully.
        CachedLibrary published = freeze(result);
        caches.put(server, published);
        return published.asResult();
    }

    private CachedLibrary ensureLoaded(MinecraftServer server) throws IOException {
        CachedLibrary cached = caches.get(server);
        if (cached != null) return cached;
        return load(server);
    }

    private synchronized CachedLibrary load(MinecraftServer server) throws IOException {
        CachedLibrary cached = caches.get(server);
        if (cached != null) return cached;
        DataLibraryLoadResult loaded = DataLibraryFileStore.read(
                ServerDataLibraryPaths.file(server), server.overworld().registryAccess());
        CachedLibrary snapshot = freeze(loaded);
        caches.put(server, snapshot);
        return snapshot;
    }

    private static CachedLibrary freeze(DataLibraryLoadResult result) {
        DataLibraryDocument frozen = new DataLibraryDocument();
        result.document().entriesByType().forEach((type, entries) -> entries.values().forEach(entry ->
                frozen.put(type, new DataLibraryEntry(entry.id(), entry.name(),
                        GraphValueSnapshot.snapshot(entry.value())))));
        return new CachedLibrary(frozen, result.diagnostics());
    }

    private record CachedLibrary(DataLibraryDocument document, List<DataLibraryDiagnostic> diagnostics) {
        private DataLibraryLoadResult asResult() {
            // Never expose the mutable document holder used by the cache itself.
            DataLibraryDocument copy = new DataLibraryDocument();
            document.entriesByType().forEach((type, entries) -> entries.values().forEach(entry ->
                    copy.put(type, new DataLibraryEntry(entry.id(), entry.name(),
                            GraphValueSnapshot.snapshot(entry.value())))));
            return new DataLibraryLoadResult(copy, diagnostics);
        }
    }

    private static DataLibraryEntryKey requireSingle(DataLibraryDocument document) {
        if (document.size() != 1) throw new IllegalArgumentException("Operation requires exactly one Data Library entry");
        return document.entriesByType().entrySet().stream()
                .flatMap(group -> group.getValue().keySet().stream()
                        .map(id -> new DataLibraryEntryKey(group.getKey(), id)))
                .findFirst().orElseThrow();
    }

    @FunctionalInterface private interface DocumentMutation { void apply(DataLibraryDocument document); }
}
