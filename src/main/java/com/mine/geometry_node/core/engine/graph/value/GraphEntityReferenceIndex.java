package com.mine.geometry_node.core.engine.graph.value;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/** Server-scoped index for resolving loaded entities by UUID without scanning dimensions. */
public final class GraphEntityReferenceIndex {
    public static final GraphEntityReferenceIndex INSTANCE = new GraphEntityReferenceIndex();

    private final Map<MinecraftServer, Map<UUID, WeakReference<Entity>>> entitiesByServer =
            Collections.synchronizedMap(new WeakHashMap<>());
    private boolean initialized;

    private GraphEntityReferenceIndex() {
    }

    public synchronized void init() {
        if (initialized) return;
        initialized = true;
        var bus = NeoForge.EVENT_BUS;
        bus.addListener((EntityJoinLevelEvent event) -> {
            if (event.getLevel() instanceof ServerLevel) remember(event.getEntity());
        });
        bus.addListener((EntityLeaveLevelEvent event) -> {
            if (event.getLevel() instanceof ServerLevel level) {
                forget(level.getServer(), event.getEntity());
            }
        });
        bus.addListener((ServerStoppedEvent event) -> entitiesByServer.remove(event.getServer()));
    }

    @Nullable
    Entity resolve(MinecraftServer server, UUID entityId) {
        if (server == null || entityId == null) return null;
        Map<UUID, WeakReference<Entity>> entities = getServerIndex(server);
        if (entities == null) return null;

        WeakReference<Entity> reference = entities.get(entityId);
        Entity entity = reference != null ? reference.get() : null;
        if (isAvailableOn(entity, server)) return entity;
        if (reference != null) entities.remove(entityId, reference);
        return null;
    }

    void remember(@Nullable Entity entity) {
        if (entity == null || entity.isRemoved()) return;
        if (!(entity.level() instanceof ServerLevel level)) return;
        serverIndex(level.getServer()).put(entity.getUUID(), new WeakReference<>(entity));
    }

    private void forget(MinecraftServer server, Entity entity) {
        Map<UUID, WeakReference<Entity>> entities = getServerIndex(server);
        if (entities == null) return;
        UUID entityId = entity.getUUID();
        entities.computeIfPresent(entityId, (ignored, reference) ->
                reference.get() == entity ? null : reference);
    }

    @Nullable
    private Map<UUID, WeakReference<Entity>> getServerIndex(MinecraftServer server) {
        synchronized (entitiesByServer) {
            return entitiesByServer.get(server);
        }
    }

    private Map<UUID, WeakReference<Entity>> serverIndex(MinecraftServer server) {
        synchronized (entitiesByServer) {
            return entitiesByServer.computeIfAbsent(server, ignored -> new ConcurrentHashMap<>());
        }
    }

    private static boolean isAvailableOn(@Nullable Entity entity, MinecraftServer server) {
        return entity != null && !entity.isRemoved()
                && entity.level() instanceof ServerLevel level
                && level.getServer() == server;
    }
}
