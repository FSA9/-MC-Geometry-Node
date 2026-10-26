package com.mine.geometry_node.core.engine.system.entity.index;

import com.mine.geometry_node.core.engine.lifecycle.ServerEngine;
import com.mine.geometry_node.core.engine.system.worldquery.model.WorldObjectEntry;
import com.mine.geometry_node.core.node.value.entity.EntityTemplateValue;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

/** Server-thread instance directory. No chunk loads, respawning, TTLs or cache-clear registration. */
public final class ServerEntityIndexService implements ServerEngine {
    public static final ServerEntityIndexService INSTANCE = new ServerEntityIndexService();
    private static final System.Logger LOGGER = System.getLogger(ServerEntityIndexService.class.getName());
    private static final int RECONCILE_BUDGET = 32, REFRESH_BUDGET = 32;
    private final Map<MinecraftServer, State> servers = new IdentityHashMap<>();
    private boolean initialized;
    private ServerEntityIndexService() {}
    @Override public String id() { return "geometry_node:entity_index"; }
    // Snapshot before graph resource/binding runtimes tear down during ServerStopping.
    @Override public int tickOrder() { return -1000; }
    @Override public synchronized void init() {
        if (initialized) return;
        initialized = true;
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> bootstrap(event.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> servers.remove(event.getServer()));
        NeoForge.EVENT_BUS.addListener((LevelEvent.Save event) -> {
            if (event.getLevel() instanceof ServerLevel level && level == level.getServer().overworld()) {
                State state = state(level.getServer());
                try {
                    refreshAll(level.getServer(), state, true);
                    checkpoint(level.getServer(), state);
                } catch (RuntimeException failure) { reportStorageFailure(state, failure); }
            }
        });
    }

    /** One seed per level; all later updates use UUID lookups and tracking/removal callbacks. */
    public void bootstrap(MinecraftServer server) { seed(server, state(server)); }
    private void seed(MinecraftServer server, State state) {
        for (ServerLevel level : server.getAllLevels()) if (!state.closedLevels.contains(level) && state.seeded.add(level))
            for (Entity entity : level.getAllEntities()) observe(state, level, entity, false);
    }

    public Optional<EntityIndexRecord> record(MinecraftServer server, UUID uuid) {
        return Optional.ofNullable(storage(server, state(server)).catalog().get(Objects.requireNonNull(uuid)));
    }
    public boolean isRegistered(MinecraftServer server, UUID uuid) { return record(server, uuid).isPresent(); }
    /** Alias for callers which expose the directory as a registered flag. */
    public boolean registered(MinecraftServer server, UUID uuid) { return isRegistered(server, uuid); }
    public Optional<EntityTemplateValue> preview(MinecraftServer server, UUID uuid) {
        return record(server, uuid).map(EntityIndexRecord::appearance).filter(value -> !value.isEmpty());
    }
    public Optional<Entity> findLoaded(MinecraftServer server, UUID uuid) {
        State state = state(server); seed(server, state);
        return Optional.ofNullable(loaded(state, Objects.requireNonNull(uuid)));
    }
    public void visitLoaded(MinecraftServer server, Predicate<Entity> visitor) {
        State state = state(server); seed(server, state); Objects.requireNonNull(visitor);
        for (UUID id : state.index.ids()) {
            Entity entity = loaded(state, id);
            if (entity != null && !visitor.test(entity)) return;
        }
    }
    public Optional<EntityIndexEntry> find(MinecraftServer server, UUID uuid) {
        State state = state(server); seed(server, state);
        EntityIndexRecord registration = storage(server, state).catalog().get(uuid);
        Entity entity = loaded(state, uuid);
        if (entity != null && EntityIndexSnapshots.ordinary(entity)) return Optional.of(liveRow(entity, registration));
        return registration == null ? Optional.empty() : Optional.of(registration.asEntry());
    }
    /** Deduplicates across ALL dimensions before the caller applies filters, ordering or limits. */
    public void visit(MinecraftServer server, Predicate<EntityIndexEntry> visitor) {
        State state = state(server); seed(server, state); Objects.requireNonNull(visitor);
        EntityIndexCatalog catalog = storage(server, state).catalog();
        Set<UUID> ids = new LinkedHashSet<>(state.index.ids());
        catalog.visit(record -> { ids.add(record.uuid()); return true; });
        for (UUID id : ids) {
            Entity entity = loaded(state, id);
            EntityIndexRecord record = catalog.get(id);
            if (entity != null && EntityIndexSnapshots.ordinary(entity)) {
                if (!visitor.test(liveRow(entity, record))) return;
            } else if (record != null && !visitor.test(record.asEntry())) return;
        }
    }
    public EntityIndexRegistrationResult setRegistered(MinecraftServer server, UUID uuid, boolean registered, long expectedRevision) {
        State state = state(server);
        if (state.stopping || server.isStopped()) throw new IllegalStateException("unavailable");
        seed(server, state); Objects.requireNonNull(uuid);
        EntityIndexSavedData data = storage(server, state);
        EntityIndexCatalog catalog = data.catalog(); catalog.expect(uuid, expectedRevision);
        boolean changed;
        if (registered) {
            if (catalog.get(uuid) != null)
                return new EntityIndexRegistrationResult(catalog.revision(uuid), false, find(server, uuid));
            Entity entity = loaded(state, uuid);
            if (entity == null || !entity.isAlive()) throw new IllegalStateException("entity_unavailable");
            if (!EntityIndexSnapshots.ordinary(entity)) throw new IllegalArgumentException("unsupported_entity");
            try { changed = catalog.register(uuid, expectedRevision, revision -> EntityIndexSnapshots.capture(entity, revision, null)); }
            catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("invalid", invalid); }
            if (changed) state.owners.put(uuid, new WeakReference<>(entity));
        } else {
            changed = catalog.unregister(uuid, expectedRevision);
            if (changed) { state.owners.remove(uuid); state.failedCaptures.remove(uuid); }
        }
        if (changed) data.changed();
        return new EntityIndexRegistrationResult(catalog.revision(uuid), changed, find(server, uuid));
    }
    /** Explicit editor-success refresh; updates only an existing registration. */
    public boolean refresh(MinecraftServer server, UUID uuid) {
        State state = state(server);
        if (state.stopping || server.isStopped()) return false;
        seed(server, state);
        Entity entity = loaded(state, uuid);
        return entity != null && refreshEntity(server, state, entity, true);
    }

    /** Pre-join is only a candidate: it may be canceled or fail duplicate UUID admission. */
    @Override public void entityJoined(ServerLevel level, Entity entity) {
        State state = state(level.getServer()); observe(state, level, entity, true);
    }
    /** Wire to the successful ServerLevel entity callback, including same-object tracking restart. */
    public void entityTrackingStarted(ServerLevel level, Entity entity) {
        State state = state(level.getServer()); observe(state, level, entity, false);
        if (loaded(state, entity.getUUID()) == entity) refreshEntity(level.getServer(), state, entity, true);
    }
    @Override public void entityLeft(ServerLevel level, Entity entity) {
        State state = state(level.getServer());
        // Tracking end is not destruction; capture while the object can still be read.
        if (!entity.isRemoved() && loaded(state, entity.getUUID()) == entity)
            refreshEntity(level.getServer(), state, entity, true);
        state.index.leave(level, entity.getUUID(), entity);
    }
    /** setRemoved HEAD: metadata adapters and attachments may be unavailable after removal. */
    public void entityRemoving(ServerLevel level, Entity entity, Entity.RemovalReason reason) {
        State state = state(level.getServer());
        Entity current = loaded(state, entity.getUUID());
        if (current == entity) refreshEntity(level.getServer(), state, entity, true);
    }
    /** setRemoved TAIL: actual reason, independent of tracking visibility and event cancellation. */
    public void entityRemoved(ServerLevel level, Entity entity, Entity.RemovalReason reason) {
        State state = state(level.getServer());
        UUID id = entity.getUUID();
        Entity successor = loaded(state, id);
        state.index.remove(level, entity.getUUID(), entity);
        if (reason == null || !reason.shouldDestroy()) return;
        WeakReference<Entity> owner = state.owners.get(id);
        boolean successorOwnsIdentity = successor != null && successor != entity
                || owner != null && owner.get() != null && owner.get() != entity;
        EntityIndexSavedData data = storage(level.getServer(), state);
        if (data.catalog().departure(id, reason, successorOwnsIdentity)) data.changed();
        if (successorOwnsIdentity) return;
        state.owners.remove(id);
        state.failedCaptures.remove(id);
    }
    @Override public void levelUnloaded(ServerLevel level) {
        State state = state(level.getServer());
        for (UUID id : state.index.ids()) {
            Entity entity = loaded(state, id);
            if (entity != null && entity.level() == level) refreshEntity(level.getServer(), state, entity, true);
        }
        state.index.removeLevel(level);
        state.seeded.remove(level);
        state.closedLevels.add(level);
    }
    @Override public void tickLevel(ServerLevel level) {
        try { tick(level); }
        catch (RuntimeException failure) { reportStorageFailure(state(level.getServer()), failure); }
    }
    private void tick(ServerLevel level) {
        MinecraftServer server = level.getServer();
        if (level != server.overworld()) return;
        State state = state(server);
        state.index.reconcile(RECONCILE_BUDGET, entity -> refreshEntity(server, state, entity, false));
        if (++state.ticks % 20 == 0) {
            EntityIndexSavedData data = storage(server, state);
            if (state.refreshQueue.isEmpty()) for (EntityIndexRecord record : data.catalog().records()) state.refreshQueue.addLast(record.uuid());
            for (int i = 0; i < REFRESH_BUDGET && !state.refreshQueue.isEmpty(); i++) {
                Entity entity = loaded(state, state.refreshQueue.removeFirst());
                if (entity != null) refreshEntity(server, state, entity, false);
            }
        }
        if (state.failedSave) {
            state.failedSave = false;
            storage(server, state).setDirty();
        }
    }
    /** Runs before Minecraft's final Save; retain persistent state through all shutdown callbacks. */
    @Override public void shutdown(MinecraftServer server) {
        State state = state(server); state.stopping = true;
        refreshAll(server, state, true);
        // SavedDataStorage logs write failures without failing its future; verify our own file.
        try { flush(server); }
        catch (RuntimeException failure) { LOGGER.log(System.Logger.Level.ERROR, "Entity directory shutdown save failed; retaining dirty data for final save", failure); }
    }
    public void flush(MinecraftServer server) {
        State state = state(server); EntityIndexSavedData data = storage(server, state);
        long revision = data.writeRevision();
        if (revision == 0 && Files.notExists(EntityIndexSavedData.file(server))) return;
        try {
            server.getDataStorage().saveAndJoin();
            EntityIndexSavedData.verifyWritten(EntityIndexSavedData.file(server), revision);
        } catch (IOException | RuntimeException failure) {
            data.setDirty();
            throw new IllegalStateException("storage_unavailable", failure);
        }
    }

    private static EntityIndexEntry liveRow(Entity entity, EntityIndexRecord registration) {
        return new EntityIndexEntry(EntityIndexSnapshots.row(entity, true), true, registration != null,
                entity.isAlive(), System.currentTimeMillis(), registration == null ? 0 : registration.revision(),
                com.mine.geometry_node.core.engine.system.faction.service.EntityFactions.read(entity).memberships().keySet());
    }
    private boolean refreshEntity(MinecraftServer server, State state, Entity entity, boolean observation) {
        EntityIndexSavedData data;
        try { data = storage(server, state); }
        catch (RuntimeException failure) { reportStorageFailure(state, failure); return false; }
        EntityIndexRecord previous = data.catalog().get(entity.getUUID());
        if (previous == null || !EntityIndexSnapshots.ordinary(entity)) return false;
        state.owners.put(entity.getUUID(), new WeakReference<>(entity));
        try {
            boolean changed = data.catalog().refresh(EntityIndexSnapshots.capture(entity, previous.revision(), previous), observation);
            if (changed) data.changed();
            state.failedCaptures.remove(entity.getUUID());
            return changed;
        } catch (RuntimeException failure) {
            if (state.failedCaptures.add(entity.getUUID()))
                LOGGER.log(System.Logger.Level.WARNING, "Retaining previous entity directory snapshot after capture failure: " + entity.getUUID(), failure);
            return false;
        }
    }
    private void refreshAll(MinecraftServer server, State state, boolean observation) {
        EntityIndexSavedData data = storage(server, state);
        for (EntityIndexRecord record : data.catalog().records()) {
            Entity entity = loaded(state, record.uuid());
            if (entity != null) refreshEntity(server, state, entity, observation);
        }
    }
    private void checkpoint(MinecraftServer server, State state) {
        EntityIndexSavedData data = storage(server, state);
        if (!data.isDirty() || !state.verification.isDone()) return;
        long revision = data.writeRevision(); Path file = EntityIndexSavedData.file(server);
        state.verification = server.getDataStorage().scheduleSave().thenRunAsync(() -> {
            try { EntityIndexSavedData.verifyWritten(file, revision); }
            catch (IOException | RuntimeException failure) {
                state.failedSave = true;
                LOGGER.log(System.Logger.Level.ERROR, "Entity directory write verification failed; will retry", failure);
            }
        });
    }
    private State state(MinecraftServer server) {
        if (server == null || !server.isSameThread()) throw new IllegalStateException("unavailable");
        State state = servers.get(server);
        if (state == null) {
            if (server.isStopped()) throw new IllegalStateException("unavailable");
            state = new State(); servers.put(server, state);
        }
        return state;
    }
    private static EntityIndexSavedData storage(MinecraftServer server, State state) {
        if (state.data == null) state.data = EntityIndexSavedData.get(server);
        return state.data;
    }
    private static void reportStorageFailure(State state, RuntimeException failure) {
        if (!state.storageFailureReported) {
            state.storageFailureReported = true;
            LOGGER.log(System.Logger.Level.ERROR, "Entity directory unavailable; preserving its file and vanilla lifecycle", failure);
        }
    }
    private static void observe(State state, ServerLevel level, Entity entity, boolean pending) {
        if (entity == null || entity.isRemoved() || state.closedLevels.contains(level)) return;
        state.index.join(level, entity.getUUID(), entity, pending);
    }
    private static Entity loaded(State state, UUID id) {
        return state.index.current(id);
    }
    private static final class State {
        EntityIndexSavedData data;
        final EntityIndexLoadedIndex<ServerLevel, Entity> index = new EntityIndexLoadedIndex<>((level, entity) ->
                !entity.isRemoved() && entity.level() == level && level.getEntity(entity.getUUID()) == entity);
        final Map<UUID, WeakReference<Entity>> owners = new HashMap<>();
        final Set<ServerLevel> seeded = Collections.newSetFromMap(new IdentityHashMap<>());
        final Set<ServerLevel> closedLevels = Collections.newSetFromMap(new IdentityHashMap<>());
        final Set<UUID> failedCaptures = new HashSet<>();
        final ArrayDeque<UUID> refreshQueue = new ArrayDeque<>();
        CompletableFuture<?> verification = CompletableFuture.completedFuture(null);
        volatile boolean failedSave;
        boolean stopping, storageFailureReported;
        int ticks;
    }
}
