package com.mine.geometry_node.core.engine.system.faction.service;

import com.mine.geometry_node.core.cache.*;
import com.mine.geometry_node.core.engine.metadata.OwnerMetadata;
import com.mine.geometry_node.core.engine.system.faction.model.*;
import com.mine.geometry_node.core.engine.system.faction.storage.*;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/** Trusted server-thread API. Remote callers check permissions and rate limits at their protocol boundary. */
public final class ServerFactionService {
    public static final ServerFactionService INSTANCE = new ServerFactionService();
    private final Map<MinecraftServer, State> worlds = Collections.synchronizedMap(new IdentityHashMap<>());
    private boolean initialized;
    private ServerFactionService() {}

    public synchronized void init() {
        if (initialized) return;
        initialized = true;
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> {
            State state = worlds.remove(event.getServer());
            if (state != null) state.close();
        });
    }

    public FactionCatalog snapshot(MinecraftServer server) throws IOException {
        return state(server).catalog;
    }

    public OwnerMetadata metadata(MinecraftServer server, UUID id) throws IOException {
        return state(server).catalog.find(id).orElseThrow(() -> new IllegalStateException("faction_unavailable")).metadata();
    }

    /** Commits the current owner atomically; stale handles never recreate deleted factions. */
    public boolean updateMetadata(MinecraftServer server, UUID id, UnaryOperator<OwnerMetadata> transformation) throws IOException {
        State state = state(server);
        return state.mutations.run(() -> {
            FactionCatalog next = FactionEdits.updateMetadata(state.catalog, id, transformation);
            if (next == state.catalog) return false;
            FactionFileStore.write(FactionFileStore.file(server), next);
            state.catalog = next;
            state.encoded = null;
            ServerFactionRequests.metadataChanged(server);
            return true;
        });
    }

    public FactionCatalog execute(MinecraftServer server, long expectedRevision, String operation, Map<String, Object> payload) throws IOException {
        State state = state(server);
        if ("READ".equals(operation)) {
            FactionEdits.apply(state.catalog, operation, payload, server.registryAccess());
            return state.catalog;
        }
        return state.mutations.run(() -> {
            if (state.catalog.revision() != expectedRevision) throw new IllegalStateException("conflict");
            FactionCatalog next = FactionEdits.apply(state.catalog, operation, payload, server.registryAccess());
            if (next == state.catalog) return state.catalog;
            // A failed validation/write never publishes a partial catalogue or a new revision.
            FactionFileStore.write(FactionFileStore.file(server), next);
            state.catalog = next;
            state.encoded = null;
            return next;
        });
    }

    /** Derived representation only; clearing it cannot reset data or optimistic revisions. */
    public String encodedSnapshot(MinecraftServer server) throws IOException {
        State state = state(server);
        if (state.encoded == null) state.encoded = FactionJsonCodec.encode(state.catalog);
        return state.encoded;
    }

    private State state(MinecraftServer server) throws IOException {
        requireThread(server);
        State state = worlds.get(server);
        if (state == null) {
            FactionCatalog loaded = FactionFileStore.read(FactionFileStore.file(server));
            state = new State(server, loaded);
            worlds.put(server, state);
        }
        return state;
    }

    private static void requireThread(MinecraftServer server) {
        if (server == null || !server.isSameThread() || server.isStopped())
            throw new IllegalStateException("Faction access requires a live server owner thread");
    }

    @FunctionalInterface
    interface Mutation<T> { T apply() throws IOException; }

    /** Server-thread callbacks may read the catalogue, but cannot start another write transaction. */
    static final class MutationGuard {
        private boolean active;
        <T> T run(Mutation<T> mutation) throws IOException {
            if (active) throw new IllegalStateException("Faction mutation already in progress");
            active = true;
            try { return mutation.apply(); }
            finally { active = false; }
        }
    }

    private static final class State implements AutoCloseable {
        private final MinecraftServer server;
        private final CacheRegistry.Registration registration;
        private final MutationGuard mutations = new MutationGuard();
        private FactionCatalog catalog;
        private String encoded;
        private volatile boolean closed;
        State(MinecraftServer server, FactionCatalog catalog) {
            this.server = server; this.catalog = catalog;
            registration = CacheRegistry.GLOBAL.register(new CacheDescriptor("geometry_node:faction.server.snapshot",
                    "geometry_node.cache.faction.server_snapshot", CacheDescriptor.Domain.SERVER,
                    CacheDescriptor.Storage.MEMORY, CacheDescriptor.Lifetime.WORLD), new ManagedCache() {
                @Override public CompletionStage<CacheStats> stats() {
                    return onServer(() -> CacheStats.memory(encoded == null ? 0 : 1,
                            encoded == null ? 0 : 2L * encoded.length()));
                }
                @Override public CompletionStage<Void> clear(CacheClearRequest request) {
                    if (!supports(request)) return CompletableFuture.failedFuture(new IllegalArgumentException("Unsupported faction cache clear scope"));
                    return onServer(() -> { encoded = null; return null; });
                }
            });
        }
        private <T> CompletableFuture<T> onServer(Supplier<T> action) {
            CompletableFuture<T> result = new CompletableFuture<>();
            if (closed || server.isStopped()) return CompletableFuture.failedFuture(new IllegalStateException("Server stopped"));
            server.schedule(server.wrapRunnable(() -> {
                if (closed || server.isStopped()) { result.completeExceptionally(new IllegalStateException("Server stopped")); return; }
                try { result.complete(action.get()); }
                catch (RuntimeException failure) { result.completeExceptionally(failure); }
            }));
            return result;
        }
        @Override public void close() { closed = true; registration.close(); encoded = null; }
    }
}
