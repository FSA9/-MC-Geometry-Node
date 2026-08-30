package com.mine.geometry_node.core.engine.graph.resource;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.core.engine.graph.binding.GraphBindingKey;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/** Dispatches lifecycle release requests to resource-specific stores. */
public final class GraphResourceLifecycleManager {
    public static final GraphResourceLifecycleManager INSTANCE = new GraphResourceLifecycleManager();

    private final Map<String, Store> stores = new LinkedHashMap<>();
    private boolean initialized;

    private GraphResourceLifecycleManager() {
    }

    public synchronized void init() {
        if (initialized) return;
        initialized = true;
        var bus = NeoForge.EVENT_BUS;
        bus.addListener((EntityLeaveLevelEvent event) -> {
            if (event.getLevel() instanceof ServerLevel level) {
                releaseEntity(level.getServer(), level.dimension(), event.getEntity().getUUID());
            }
        });
        bus.addListener((LevelEvent.Unload event) -> {
            if (event.getLevel() instanceof ServerLevel level) releaseLevel(level.getServer(), level.dimension());
        });
        bus.addListener((ServerStoppedEvent event) -> releaseServer(event.getServer()));
    }

    public synchronized void registerStore(String id, Store store) {
        if (id == null || id.isBlank() || store == null) throw new IllegalArgumentException("Invalid graph resource store");
        Store previous = stores.putIfAbsent(id, store);
        if (previous != null && previous != store) throw new IllegalArgumentException("Duplicate graph resource store: " + id);
    }

    public void releaseBinding(MinecraftServer server, GraphResourceScope scope, GraphBindingKey binding) {
        release(server, id -> id.type().lifetime() == GraphResourceLifetime.BINDING
                && id.scope().equals(scope) && id.binding().equals(binding));
    }

    public void releaseProcess(MinecraftServer server, UUID processId) {
        if (processId != null) release(server, id -> processId.equals(id.processInstanceId()));
    }

    public void releaseOwner(MinecraftServer server, GraphResourceScope scope) {
        release(server, id -> id.scope().equals(scope));
    }

    public void releaseEntity(MinecraftServer server, ResourceKey<Level> dimension, UUID entityId) {
        if (dimension == null || entityId == null) return;
        release(server, id -> id.scope().dimension().equals(dimension)
                && ((id.scope() instanceof GraphResourceScope.EntityScope entityScope
                && entityId.equals(entityScope.ownerId())) || entityId.equals(id.targetEntityId())));
    }

    public void releaseLevel(MinecraftServer server, ResourceKey<Level> dimension) {
        release(server, id -> id.scope().dimension().equals(dimension));
    }

    public void releaseServer(MinecraftServer server) {
        release(server, ignored -> true);
    }

    private void release(MinecraftServer server, Predicate<GraphResourceId> predicate) {
        if (server == null) return;
        Store[] snapshot;
        synchronized (this) {
            snapshot = stores.values().toArray(Store[]::new);
        }
        for (Store store : snapshot) {
            try {
                store.remove(server, predicate);
            } catch (RuntimeException exception) {
                GeometryNode.LOGGER.error("Graph resource store cleanup failed", exception);
            }
        }
    }

    @FunctionalInterface
    public interface Store {
        void remove(MinecraftServer server, Predicate<GraphResourceId> predicate);
    }
}
