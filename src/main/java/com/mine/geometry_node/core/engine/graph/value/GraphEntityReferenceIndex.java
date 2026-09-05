package com.mine.geometry_node.core.engine.graph.value;

import com.mine.geometry_node.core.engine.runtime.ServerEngine;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/** Server-scoped index for resolving loaded entities by UUID without scanning dimensions. */
public final class GraphEntityReferenceIndex implements ServerEngine {
    public static final GraphEntityReferenceIndex INSTANCE = new GraphEntityReferenceIndex();

    private final Map<MinecraftServer, ServerIndex> entitiesByServer =
            Collections.synchronizedMap(new WeakHashMap<>());

    private GraphEntityReferenceIndex() {
    }

    @Override
    public String id() {
        return "geometry_node:graph_entity_reference_index";
    }

    @Override
    public void entityJoined(ServerLevel level, Entity entity) {
        remember(entity);
    }

    @Override
    public void entityLeft(ServerLevel level, Entity entity) {
        forget(level, entity);
    }

    @Override
    public void levelUnloaded(ServerLevel level) {
        ServerIndex index = getServerIndex(level.getServer());
        if (index == null) return;
        index.discardCollected();
        index.entities.entrySet().removeIf(entry -> {
            Entity entity = entry.getValue().get();
            return entity == null || entity.level() == level;
        });
    }

    @Override
    public void tickLevel(ServerLevel level) {
        ServerIndex index = getServerIndex(level.getServer());
        if (index != null) index.discardCollected();
    }

    @Override
    public void shutdown(MinecraftServer server) {
        entitiesByServer.remove(server);
    }

    @Nullable
    Entity resolve(MinecraftServer server, UUID entityId) {
        if (server == null || entityId == null) return null;
        ServerIndex index = getServerIndex(server);
        if (index == null) return null;
        index.discardCollected();

        IndexedEntityReference reference = index.entities.get(entityId);
        Entity entity = reference != null ? reference.get() : null;
        if (isAvailableOn(entity, server)) return entity;
        if (reference != null) index.entities.remove(entityId, reference);
        return null;
    }

    void remember(@Nullable Entity entity) {
        if (entity == null || entity.isRemoved()) return;
        if (!(entity.level() instanceof ServerLevel level)) return;
        ServerIndex index = serverIndex(level.getServer());
        index.discardCollected();
        index.entities.put(entity.getUUID(), new IndexedEntityReference(entity, index.collected));
    }

    private void forget(ServerLevel level, Entity entity) {
        if (entity == null || entity.level() != level) return;
        ServerIndex index = getServerIndex(level.getServer());
        if (index == null) return;
        index.discardCollected();
        UUID entityId = entity.getUUID();
        index.entities.computeIfPresent(entityId, (ignored, reference) ->
                reference.get() == entity ? null : reference);
    }

    @Nullable
    private ServerIndex getServerIndex(MinecraftServer server) {
        synchronized (entitiesByServer) {
            return entitiesByServer.get(server);
        }
    }

    private ServerIndex serverIndex(MinecraftServer server) {
        synchronized (entitiesByServer) {
            return entitiesByServer.computeIfAbsent(server, ignored -> new ServerIndex());
        }
    }

    private static boolean isAvailableOn(@Nullable Entity entity, MinecraftServer server) {
        return entity != null && !entity.isRemoved()
                && entity.level() instanceof ServerLevel level
                && level.getServer() == server;
    }

    private static final class ServerIndex {
        private final Map<UUID, IndexedEntityReference> entities = new ConcurrentHashMap<>();
        private final ReferenceQueue<Entity> collected = new ReferenceQueue<>();

        private void discardCollected() {
            IndexedEntityReference reference;
            while ((reference = (IndexedEntityReference) collected.poll()) != null) {
                entities.remove(reference.entityId, reference);
            }
        }
    }

    private static final class IndexedEntityReference extends WeakReference<Entity> {
        private final UUID entityId;

        private IndexedEntityReference(Entity entity, ReferenceQueue<Entity> collected) {
            super(entity, collected);
            this.entityId = entity.getUUID();
        }
    }
}
