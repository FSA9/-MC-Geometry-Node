package com.mine.geometry_node.core.engine.system.data.library;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.core.engine.graph.value.GraphEntityReferenceResolver;
import com.mine.geometry_node.core.engine.graph.value.GraphValueSnapshot;
import com.mine.geometry_node.core.node.definition.port.PortType;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/** Server-authoritative persistence and immutable runtime access for the Data Library. */
public final class RemoteDataLibraryService {
    public static final RemoteDataLibraryService INSTANCE = new RemoteDataLibraryService();

    private final Map<MinecraftServer, CachedLibrary> caches = new ConcurrentHashMap<>();
    private final Set<MinecraftServer> stoppedServers = Collections.newSetFromMap(new WeakHashMap<>());
    private volatile boolean initialized;

    private RemoteDataLibraryService() {}

    public synchronized void init() {
        if (initialized) return;
        initialized = true;
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> {
            clearStopped(event.getServer());
            try { load(event.getServer()); }
            catch (IOException exception) { GeometryNode.LOGGER.error("Failed to load server Data Library cache", exception); }
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> {
            markStopped(event.getServer());
            caches.remove(event.getServer());
        });
    }

    /** Returns a detached copy of the current server snapshot. */
    public DataLibraryLoadResult refresh(MinecraftServer server) throws IOException {
        return ensureLoaded(server).asResult();
    }

    /** Resolves solely by globally unique entry UUID. Mutable values are detached. */
    @Nullable
    public Object resolve(MinecraftServer server, UUID entryId) {
        return resolve(server, entryId, null);
    }

    /** Resolves by UUID while enforcing the output type cached by a reference node. */
    @Nullable
    public Object resolve(MinecraftServer server, UUID entryId, @Nullable PortType expectedType) {
        if (server == null || entryId == null || isStopped(server)) return null;
        try {
            DataLibraryEntry entry = ensureLoaded(server).document().find(entryId).orElse(null);
            if (entry == null || expectedType != null && entry.type() != expectedType) return null;
            if (entry.value() instanceof DataLibraryEntityReference reference) {
                return GraphEntityReferenceResolver.resolve(reference.entityId(), server);
            }
            return GraphValueSnapshot.snapshot(entry.value());
        } catch (IOException | RuntimeException exception) {
            return null;
        }
    }

    /** Returns detached entry metadata and value, primarily for editor/read-only consumers. */
    @Nullable
    public DataLibraryEntry resolveEntry(MinecraftServer server, UUID entryId) {
        if (server == null || entryId == null || isStopped(server)) return null;
        try {
            return ensureLoaded(server).document().find(entryId)
                    .map(entry -> new DataLibraryEntry(entry.id(), entry.parentId(), entry.type(), entry.key(),
                            GraphValueSnapshot.snapshot(entry.value())))
                    .orElse(null);
        } catch (IOException | RuntimeException exception) {
            return null;
        }
    }

    /** Creates all objects in a partial document. Existing ancestor folders may be included unchanged. */
    public DataLibraryLoadResult create(MinecraftServer server, DataLibraryDocument incoming) throws IOException {
        requireObjects(incoming);
        return update(server, document -> {
            mergeFolders(document, incoming, false);
            for (DataLibraryEntry entry : incoming.entries().values()) {
                if (document.find(entry.id()).isPresent()) {
                    throw new IllegalStateException("Data Library entry already exists: " + entry.id());
                }
                document.put(copyEntry(entry));
            }
        });
    }

    /** Updates entries after checking their previous per-object fingerprints. Moves use the explicit move API. */
    public DataLibraryLoadResult update(MinecraftServer server, DataLibraryDocument incoming,
                                        Map<UUID, String> expectedFingerprints) throws IOException {
        requireObjects(incoming);
        if (expectedFingerprints == null
                || !expectedFingerprints.keySet().equals(incoming.entries().keySet())) {
            throw new IllegalArgumentException("Entry update requires one matching expected fingerprint per entry");
        }
        return update(server, document -> {
            if (incoming.entries().isEmpty()) throw new IllegalArgumentException("Entry update requires an entry");
            for (DataLibraryEntry entry : incoming.entries().values()) {
                DataLibraryEntry existing = document.find(entry.id()).orElseThrow(() -> stale(entry.id()));
                requireExpected(entry.id(), expectedFingerprints,
                        DataLibraryObjectFingerprint.entry(existing, server.overworld().registryAccess()));
                if (existing.type() != entry.type()) {
                    throw new IllegalStateException("Data Library entry type cannot be changed: " + entry.id());
                }
                if (!java.util.Objects.equals(existing.parentId(), entry.parentId())) {
                    throw new IllegalArgumentException("Data Library entry moves require the explicit move operation");
                }
                document.put(copyEntry(entry));
            }
        });
    }

    /** Creates or replaces an entry at path/type/key, preserving UUID when it already exists. */
    public synchronized DataLibraryEntry upsert(MinecraftServer server, String path, PortType type,
                                                String key, @Nullable Object value) throws IOException {
        if (!DataLibraryTypes.supports(type)) throw new IllegalArgumentException("Unsupported Data Library type: " + type);
        Object storedValue = roundTripValue(server, type, value);
        DataLibraryDocument cached = ensureLoaded(server).document();
        String pathValue = path == null ? "" : path.trim();
        boolean root = pathValue.isEmpty()
                || pathValue.chars().allMatch(character -> character == '/' || character == '\\');
        UUID cachedParent = root ? null : cached.findFolderByPath(path).map(DataLibraryFolder::id).orElse(null);
        if (root || cachedParent != null) {
            DataLibraryEntry existing = cached.findByLocation(cachedParent, type, key).orElse(null);
            if (existing != null && valuesEqual(server, type, existing.value(), storedValue)) {
                return copyEntry(existing);
            }
        }
        final DataLibraryEntry[] resultEntry = new DataLibraryEntry[1];
        update(server, document -> {
            UUID parentId = document.ensureFolderPath(path);
            DataLibraryEntry existing = document.findByLocation(parentId, type, key).orElse(null);
            DataLibraryEntry replacement = new DataLibraryEntry(
                    existing != null ? existing.id() : UUID.randomUUID(), parentId, type, key, storedValue);
            if (existing == null || !valuesEqual(server, type, existing.value(), storedValue)) document.put(replacement);
            resultEntry[0] = replacement;
        });
        return resultEntry[0];
    }

    public DataLibraryLoadResult createFolder(MinecraftServer server, @Nullable UUID parentId,
                                              String name) throws IOException {
        return update(server, document -> document.putFolder(
                new DataLibraryFolder(UUID.randomUUID(), parentId, name)));
    }

    public DataLibraryLoadResult updateFolder(MinecraftServer server, DataLibraryFolder folder,
                                              String expectedFingerprint) throws IOException {
        return update(server, document -> {
            DataLibraryFolder existing = document.findFolder(folder.id()).orElseThrow(() -> stale(folder.id()));
            requireExpected(folder.id(), Map.of(folder.id(), expectedFingerprint),
                    DataLibraryObjectFingerprint.folder(existing));
            if (!java.util.Objects.equals(existing.parentId(), folder.parentId())) {
                throw new IllegalArgumentException("Data Library folder moves require the explicit move operation");
            }
            document.putFolder(folder);
        });
    }

    public DataLibraryLoadResult moveEntry(MinecraftServer server, UUID entryId, @Nullable UUID parentId,
                                           String expectedFingerprint) throws IOException {
        return update(server, document -> {
            DataLibraryEntry existing = document.find(entryId).orElseThrow(() -> stale(entryId));
            requireExpected(entryId, Map.of(entryId, expectedFingerprint),
                    DataLibraryObjectFingerprint.entry(existing, server.overworld().registryAccess()));
            document.put(new DataLibraryEntry(existing.id(), parentId, existing.type(), existing.key(),
                    GraphValueSnapshot.snapshot(existing.value())));
        });
    }

    public DataLibraryLoadResult moveFolder(MinecraftServer server, UUID folderId, @Nullable UUID parentId,
                                            String expectedFingerprint) throws IOException {
        return update(server, document -> {
            DataLibraryFolder existing = document.findFolder(folderId).orElseThrow(() -> stale(folderId));
            requireExpected(folderId, Map.of(folderId, expectedFingerprint),
                    DataLibraryObjectFingerprint.folder(existing));
            document.putFolder(new DataLibraryFolder(existing.id(), parentId, existing.name()));
        });
    }

    /** Deletes entries or complete folder subtrees after a per-selection CAS check. */
    public DataLibraryLoadResult delete(MinecraftServer server, Set<DataLibraryObjectKey> keys,
                                        String expectedFingerprint) throws IOException {
        return update(server, document -> {
            String actual = DataLibraryObjectFingerprint.deletion(
                    document, keys, server.overworld().registryAccess());
            if (!DataLibraryObjectFingerprint.isValid(expectedFingerprint)
                    || !actual.equals(expectedFingerprint)) {
                throw new IllegalStateException("STALE_OBJECT: delete selection");
            }
            for (DataLibraryObjectKey key : keys) {
                if (!document.remove(key.id())) document.removeFolder(key.id());
            }
        });
    }

    private synchronized DataLibraryLoadResult update(MinecraftServer server, DocumentMutation mutation) throws IOException {
        if (server == null || isStopped(server)) throw new IOException("Server is stopping");
        DataLibraryLoadResult result = DataLibraryFileStore.updateAtomic(
                ServerDataLibraryPaths.file(server), server.overworld().registryAccess(), document -> {
                    mutation.apply(document);
                    return document;
                });
        CachedLibrary published = freeze(result);
        if (!isStopped(server)) caches.put(server, published);
        return published.asResult();
    }

    private CachedLibrary ensureLoaded(MinecraftServer server) throws IOException {
        CachedLibrary cached = caches.get(server);
        return cached != null ? cached : load(server);
    }

    private synchronized CachedLibrary load(MinecraftServer server) throws IOException {
        if (server == null || isStopped(server)) throw new IOException("Server is stopping");
        CachedLibrary cached = caches.get(server);
        if (cached != null) return cached;
        CachedLibrary snapshot = freeze(DataLibraryFileStore.read(
                ServerDataLibraryPaths.file(server), server.overworld().registryAccess()));
        if (!isStopped(server)) caches.put(server, snapshot);
        return snapshot;
    }

    private static CachedLibrary freeze(DataLibraryLoadResult result) {
        return new CachedLibrary(result.document().copy(), result.diagnostics());
    }

    private record CachedLibrary(DataLibraryDocument document, List<DataLibraryDiagnostic> diagnostics) {
        private DataLibraryLoadResult asResult() {
            return new DataLibraryLoadResult(document.copy(), diagnostics);
        }
    }

    private static void mergeFolders(DataLibraryDocument target, DataLibraryDocument incoming, boolean updateExisting) {
        for (DataLibraryFolder folder : incoming.folders().values()) {
            DataLibraryFolder current = target.findFolder(folder.id()).orElse(null);
            if (current == null || updateExisting) target.putFolder(folder);
            else if (!current.equals(folder)) {
                throw new IllegalStateException("Data Library folder UUID already exists: " + folder.id());
            }
        }
    }

    private static void requireObjects(DataLibraryDocument document) {
        if (document == null || document.entries().isEmpty() && document.folders().isEmpty()) {
            throw new IllegalArgumentException("Operation requires at least one Data Library object");
        }
    }

    private static DataLibraryEntry copyEntry(DataLibraryEntry entry) {
        return new DataLibraryEntry(entry.id(), entry.parentId(), entry.type(), entry.key(),
                GraphValueSnapshot.snapshot(entry.value()));
    }

    private static void requireExpected(UUID id, Map<UUID, String> expectedFingerprints, String actual) {
        String expected = expectedFingerprints == null ? null : expectedFingerprints.get(id);
        if (!DataLibraryObjectFingerprint.isValid(expected) || !actual.equals(expected)) throw stale(id);
    }

    private static IllegalStateException stale(UUID id) {
        return new IllegalStateException("STALE_OBJECT: " + id);
    }

    private static Object roundTripValue(MinecraftServer server, PortType type, @Nullable Object value) {
        return DataLibraryValueCodec.decode(type,
                DataLibraryValueCodec.encode(type, value, server.overworld().registryAccess()),
                server.overworld().registryAccess());
    }

    private static boolean valuesEqual(MinecraftServer server, PortType type,
                                       @Nullable Object first, @Nullable Object second) {
        return DataLibraryValueCodec.encode(type, first, server.overworld().registryAccess())
                .equals(DataLibraryValueCodec.encode(type, second, server.overworld().registryAccess()));
    }

    @FunctionalInterface private interface DocumentMutation { void apply(DataLibraryDocument document); }

    private boolean isStopped(MinecraftServer server) {
        synchronized (stoppedServers) { return stoppedServers.contains(server); }
    }

    private void markStopped(MinecraftServer server) {
        synchronized (stoppedServers) { stoppedServers.add(server); }
    }

    private void clearStopped(MinecraftServer server) {
        synchronized (stoppedServers) { stoppedServers.remove(server); }
    }
}
