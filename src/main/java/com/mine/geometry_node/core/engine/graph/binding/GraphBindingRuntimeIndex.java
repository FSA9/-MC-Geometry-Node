package com.mine.geometry_node.core.engine.graph.binding;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.core.engine.attachment.EntityGraphAttachment;
import com.mine.geometry_node.core.engine.runtime.ServerEngine;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/** Rebuildable server index of graph bindings on currently loaded entities. */
public final class GraphBindingRuntimeIndex implements ServerEngine {
    public static final GraphBindingRuntimeIndex INSTANCE = new GraphBindingRuntimeIndex();

    private final Map<MinecraftServer, ServerIndex> servers = new WeakHashMap<>();

    private GraphBindingRuntimeIndex() {
    }

    @Override
    public String id() {
        return "geometry_node:graph_binding_index";
    }

    @Override
    public void entityJoined(ServerLevel level, Entity entity) {
        synchronize(entity);
    }

    @Override
    public void entityLeft(ServerLevel level, Entity entity) {
        forget(level, entity);
    }

    @Override
    public void levelUnloaded(ServerLevel level) {
        ServerIndex index = servers.get(level.getServer());
        if (index == null) return;
        for (Map.Entry<UUID, WeakReference<Entity>> entry : Set.copyOf(index.entitiesById.entrySet())) {
            Entity entity = entry.getValue().get();
            if (entity == null || entity.level() == level) {
                forget(index, entry.getKey(), entry.getValue());
            }
        }
    }

    @Override
    public void shutdown(MinecraftServer server) {
        servers.remove(server);
    }

    public void synchronize(Entity entity) {
        if (entity == null || entity.isRemoved() || !(entity.level() instanceof ServerLevel level)) return;
        MinecraftServer server = level.getServer();
        ServerIndex index = servers.computeIfAbsent(server, ignored -> new ServerIndex());
        UUID entityId = entity.getUUID();
        Set<GraphBindingKey> current = Set.copyOf(
                entity.getData(GeometryNode.GRAPH_DATA_ATTACHMENT).getBindings());
        Set<GraphBindingKey> previous = index.bindingsByEntity.getOrDefault(entityId, Set.of());

        for (GraphBindingKey binding : previous) {
            if (!current.contains(binding)) removeBinding(index, entityId, binding);
        }
        for (GraphBindingKey binding : current) {
            if (!previous.contains(binding)) {
                index.entityIdsByBinding.computeIfAbsent(binding, ignored -> new HashSet<>()).add(entityId);
            }
        }
        if (current.isEmpty()) {
            index.bindingsByEntity.remove(entityId);
            index.entitiesById.remove(entityId);
        } else {
            index.bindingsByEntity.put(entityId, current);
            index.entitiesById.put(entityId, new WeakReference<>(entity));
        }
    }

    public Set<Entity> entities(MinecraftServer server, GraphBindingKey binding) {
        if (server == null || binding == null) return Set.of();
        ServerIndex index = servers.get(server);
        if (index == null) return Set.of();
        Set<UUID> entityIds = index.entityIdsByBinding.get(binding);
        if (entityIds == null || entityIds.isEmpty()) return Set.of();

        Set<Entity> result = new HashSet<>();
        for (UUID entityId : Set.copyOf(entityIds)) {
            WeakReference<Entity> reference = index.entitiesById.get(entityId);
            Entity entity = reference != null ? reference.get() : null;
            if (isLoadedOn(entity, server)) {
                result.add(entity);
            } else {
                forget(index, entityId, reference);
            }
        }
        return Set.copyOf(result);
    }

    private void forget(ServerLevel level, Entity entity) {
        if (level == null || entity == null || entity.level() != level) return;
        ServerIndex index = servers.get(level.getServer());
        if (index == null) return;
        UUID entityId = entity.getUUID();
        WeakReference<Entity> reference = index.entitiesById.get(entityId);
        if (reference != null && reference.get() != entity) return;
        forget(index, entityId, reference);
    }

    private static void forget(ServerIndex index, UUID entityId, WeakReference<Entity> reference) {
        if (reference != null) index.entitiesById.remove(entityId, reference);
        Set<GraphBindingKey> bindings = index.bindingsByEntity.remove(entityId);
        if (bindings == null) return;
        for (GraphBindingKey binding : bindings) removeBinding(index, entityId, binding);
    }

    private static void removeBinding(ServerIndex index, UUID entityId, GraphBindingKey binding) {
        Set<UUID> entityIds = index.entityIdsByBinding.get(binding);
        if (entityIds == null) return;
        entityIds.remove(entityId);
        if (entityIds.isEmpty()) index.entityIdsByBinding.remove(binding);
    }

    private static boolean isLoadedOn(Entity entity, MinecraftServer server) {
        return entity != null && !entity.isRemoved()
                && entity.level() instanceof ServerLevel level && level.getServer() == server;
    }

    private static final class ServerIndex {
        private final Map<GraphBindingKey, Set<UUID>> entityIdsByBinding = new HashMap<>();
        private final Map<UUID, Set<GraphBindingKey>> bindingsByEntity = new HashMap<>();
        private final Map<UUID, WeakReference<Entity>> entitiesById = new HashMap<>();
    }
}
