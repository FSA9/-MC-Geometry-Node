package com.mine.geometry_node.core.engine.graph.resource;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.core.engine.graph.binding.GraphBindingKey;
import com.mine.geometry_node.core.engine.runtime.ServerEngine;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Dispatches lifecycle release requests to resource-specific stores. */
public final class GraphResourceLifecycleManager implements ServerEngine {
    public static final GraphResourceLifecycleManager INSTANCE = new GraphResourceLifecycleManager();

    private final Map<String, Store> stores = new LinkedHashMap<>();

    private GraphResourceLifecycleManager() {
    }

    @Override
    public String id() {
        return "geometry_node:graph_resource_lifecycle";
    }

    @Override
    public int tickOrder() {
        return 900;
    }

    @Override
    public void entityLeft(ServerLevel level, Entity entity) {
        releaseEntity(level.getServer(), level.dimension(), entity.getUUID());
    }

    @Override
    public void levelUnloaded(ServerLevel level) {
        releaseLevel(level.getServer(), level.dimension());
    }

    @Override
    public void shutdown(MinecraftServer server) {
        releaseServer(server);
    }

    public synchronized void registerStore(String id, Store store) {
        if (id == null || id.isBlank() || store == null) throw new IllegalArgumentException("Invalid graph resource store");
        Store previous = stores.putIfAbsent(id, store);
        if (previous != null && previous != store) throw new IllegalArgumentException("Duplicate graph resource store: " + id);
    }

    public void releaseBinding(MinecraftServer server, GraphResourceScope scope, GraphBindingKey binding) {
        if (scope != null && binding != null) {
            release(server, new GraphResourceRelease.Binding(scope, binding));
        }
    }

    public void releaseProcess(MinecraftServer server, UUID processId) {
        if (processId != null) release(server, new GraphResourceRelease.Process(processId));
    }

    public void releaseOwner(MinecraftServer server, GraphResourceScope scope) {
        if (scope != null) release(server, new GraphResourceRelease.Owner(scope));
    }

    public void releaseEntity(MinecraftServer server, ResourceKey<Level> dimension, UUID entityId) {
        if (dimension == null || entityId == null) return;
        release(server, new GraphResourceRelease.Entity(dimension, entityId));
    }

    public void releaseLevel(MinecraftServer server, ResourceKey<Level> dimension) {
        if (dimension != null) release(server, new GraphResourceRelease.LevelScope(dimension));
    }

    public void releaseServer(MinecraftServer server) {
        release(server, GraphResourceRelease.Server.INSTANCE);
    }

    private void release(MinecraftServer server, GraphResourceRelease release) {
        if (server == null) return;
        Store[] snapshot;
        synchronized (this) {
            snapshot = stores.values().toArray(Store[]::new);
        }
        for (Store store : snapshot) {
            try {
                store.remove(server, release);
            } catch (RuntimeException exception) {
                GeometryNode.LOGGER.error("Graph resource store cleanup failed", exception);
            }
        }
    }

    @FunctionalInterface
    public interface Store {
        void remove(MinecraftServer server, GraphResourceRelease release);
    }
}
