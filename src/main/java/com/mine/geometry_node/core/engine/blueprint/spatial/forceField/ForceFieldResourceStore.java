package com.mine.geometry_node.core.engine.blueprint.spatial.forceField;

import com.mine.geometry_node.core.engine.blueprint.spatial.area.AreaAddress;
import com.mine.geometry_node.core.engine.blueprint.spatial.area.AreaResource;
import com.mine.geometry_node.core.engine.blueprint.spatial.area.AreaResourceStore;
import com.mine.geometry_node.core.engine.graph.expression.LiveValue;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceId;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceLifecycleManager;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceRelease;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.UUID;

/** Runtime store for non-persistent force fields addressed by dimension + ID. */
public final class ForceFieldResourceStore {
    public static final ForceFieldResourceStore INSTANCE = new ForceFieldResourceStore();
    private final Map<MinecraftServer, ServerState> servers = new WeakHashMap<>();

    private ForceFieldResourceStore() {
        GraphResourceLifecycleManager.INSTANCE.registerStore("blueprint_force_field", this::removeOwned);
    }

    public ForceFieldResource upsert(MinecraftServer server, ForceFieldAddress address,
                                     GraphResourceId owner, AreaAddress area,
                                     long creationGameTime, LiveValue<Float> strength) {
        AreaResource areaResource = AreaResourceStore.INSTANCE.get(server, area);
        synchronized (this) {
            ServerState state = servers.computeIfAbsent(server, ignored -> new ServerState());
            ForceFieldResource resource = new ForceFieldResource(address, owner, ++state.generation,
                    area, creationGameTime, strength,
                    areaResource != null ? areaResource.anchorEntityId() : null);
            state.entries.put(address, resource);
            state.snapshotsByDimension.remove(address.dimension());
            return resource;
        }
    }

    public synchronized void updateAreaAnchor(MinecraftServer server, AreaAddress area,
                                              @Nullable UUID anchorEntityId) {
        ServerState state = servers.get(server);
        if (state == null) return;
        for (ForceFieldResource resource : state.entries.values()) {
            if (resource.area().equals(area)) resource.setAnchorEntityId(anchorEntityId);
        }
    }

    @Nullable
    public synchronized ForceFieldResource get(MinecraftServer server, ForceFieldAddress address) {
        ServerState state = servers.get(server);
        return state != null ? state.entries.get(address) : null;
    }

    public synchronized boolean remove(MinecraftServer server, ForceFieldAddress address) {
        ServerState state = servers.get(server);
        if (state == null || state.entries.remove(address) == null) return false;
        state.snapshotsByDimension.remove(address.dimension());
        return true;
    }

    public synchronized List<ForceFieldResource> snapshot(ServerLevel level) {
        ServerState state = servers.get(level.getServer());
        if (state == null || state.entries.isEmpty()) return List.of();
        List<ForceFieldResource> cached = state.snapshotsByDimension.get(level.dimension());
        if (cached != null) return cached;
        List<ForceFieldResource> result = new ArrayList<>();
        for (ForceFieldResource resource : state.entries.values()) {
            if (resource.address().dimension().equals(level.dimension())) result.add(resource);
        }
        List<ForceFieldResource> snapshot = List.copyOf(result);
        state.snapshotsByDimension.put(level.dimension(), snapshot);
        return snapshot;
    }

    public synchronized void shutdown(MinecraftServer server) {
        servers.remove(server);
    }

    private synchronized void removeOwned(MinecraftServer server, GraphResourceRelease release) {
        ServerState state = servers.get(server);
        if (state == null) return;
        if (state.entries.entrySet().removeIf(entry -> {
            ForceFieldResource resource = entry.getValue();
            if (release instanceof GraphResourceRelease.Entity entityRelease) {
                return resource.address().dimension().equals(entityRelease.dimension())
                        && entityRelease.entityId().equals(resource.anchorEntityId());
            }
            return release.matches(resource.owner());
        })) {
            state.snapshotsByDimension.clear();
        }
    }

    private static final class ServerState {
        private final Map<ForceFieldAddress, ForceFieldResource> entries = new HashMap<>();
        private final Map<ResourceKey<Level>, List<ForceFieldResource>> snapshotsByDimension = new HashMap<>();
        private long generation;
    }
}
