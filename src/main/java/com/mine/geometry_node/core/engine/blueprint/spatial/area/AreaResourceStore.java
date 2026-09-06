package com.mine.geometry_node.core.engine.blueprint.spatial.area;

import com.mine.geometry_node.core.engine.graph.resource.GraphResourceId;
import com.mine.geometry_node.core.engine.graph.expression.LiveValue;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceLifecycleManager;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceRelease;
import com.mine.geometry_node.core.engine.blueprint.spatial.forceField.ForceFieldResourceStore;
import com.mine.geometry_node.core.engine.graph.debug.DebugRenderChannel;
import com.mine.geometry_node.core.engine.graph.debug.DebugRenderShape;
import com.mine.geometry_node.core.engine.graph.debug.DebugRendererSessionManager;
import com.mine.geometry_node.core.engine.graph.debug.DebugSourceId;
import com.mine.geometry_node.core.engine.graph.debug.DebugSourceIdCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/** Runtime store for public Areas. Entries are non-persistent and owned by graph bindings. */
public final class AreaResourceStore {
    public static final AreaResourceStore INSTANCE = new AreaResourceStore();

    private final Map<MinecraftServer, ServerState> servers = new WeakHashMap<>();

    private AreaResourceStore() {
        GraphResourceLifecycleManager.INSTANCE.registerStore("blueprint_area", this::removeOwned);
    }

    public AreaResource upsert(MinecraftServer server, AreaAddress address,
                               GraphResourceId owner, AreaShape shape,
                               long creationGameTime,
                               LiveValue<Vec3> center, LiveValue<Vec3> size,
                               LiveValue<Vec3> rotation, LiveValue<Float> radius,
                               LiveValue<Float> height,
                               @Nullable UUID anchorEntityId) {
        AreaResource resource;
        synchronized (this) {
            ServerState state = servers.computeIfAbsent(server, ignored -> new ServerState());
            resource = new AreaResource(address, owner, ++state.generation,
                    shape, creationGameTime, center, size, rotation, radius, height, anchorEntityId);
            state.entries.put(address, resource);
            state.snapshotsByDimension.remove(address.dimension());
        }
        ForceFieldResourceStore.INSTANCE.updateAreaAnchor(server, address, anchorEntityId);
        return resource;
    }

    @Nullable
    public synchronized AreaResource get(MinecraftServer server, AreaAddress address) {
        ServerState state = servers.get(server);
        return state != null ? state.entries.get(address) : null;
    }

    @Nullable
    public synchronized AreaResource get(MinecraftServer server, AreaRef reference) {
        AreaResource resource = get(server, reference.address());
        return resource != null && resource.reference().equals(reference) ? resource : null;
    }

    public synchronized boolean remove(MinecraftServer server, AreaAddress address) {
        ServerState state = servers.get(server);
        if (state == null || state.entries.remove(address) == null) return false;
        state.snapshotsByDimension.remove(address.dimension());
        return true;
    }

    public synchronized boolean remove(MinecraftServer server, AreaRef reference) {
        ServerState state = servers.get(server);
        if (state == null) return false;
        AreaResource resource = state.entries.get(reference.address());
        if (resource == null || !resource.reference().equals(reference)) return false;
        state.entries.remove(reference.address());
        state.snapshotsByDimension.remove(reference.address().dimension());
        return true;
    }

    public synchronized List<AreaResource> snapshot(ServerLevel level) {
        ServerState state = servers.get(level.getServer());
        if (state == null || state.entries.isEmpty()) return List.of();
        List<AreaResource> cached = state.snapshotsByDimension.get(level.dimension());
        if (cached != null) return cached;
        List<AreaResource> result = new ArrayList<>();
        for (AreaResource resource : state.entries.values()) {
            if (resource.address().dimension().equals(level.dimension())) result.add(resource);
        }
        List<AreaResource> snapshot = List.copyOf(result);
        state.snapshotsByDimension.put(level.dimension(), snapshot);
        return snapshot;
    }

    public synchronized void tickDebug(ServerLevel level) {
        ServerState state = servers.get(level.getServer());
        Set<GraphResourceId> previousOwners = state != null
                ? state.debugOwnersByDimension.getOrDefault(level.dimension(), Set.of())
                : Set.of();
        Map<GraphResourceId, List<DebugRenderShape>> shapesByOwner = new LinkedHashMap<>();
        if (state != null && DebugRendererSessionManager.hasAreaSessions(level.getServer())) {
            for (AreaResource resource : snapshot(level)) {
                AreaResource.Resolved resolved = resource.resolve(level);
                if (resolved == null) continue;
                DebugSourceId source = DebugSourceId.graph(DebugRenderChannel.AREA, resource.owner());
                DebugRenderShape shape = new DebugRenderShape(
                        DebugSourceIdCodec.element(source, resource.address().id()),
                        resource.owner().binding().graphId(), resolved.shape().id(), resolved.center(),
                        resolved.size(), resolved.rotation(), DebugRenderChannel.AREA.color());
                shapesByOwner.computeIfAbsent(resource.owner(), ignored -> new ArrayList<>()).add(shape);
            }
        }

        Set<GraphResourceId> currentOwners = new HashSet<>(shapesByOwner.keySet());
        for (GraphResourceId owner : previousOwners) {
            if (!currentOwners.contains(owner)) {
                DebugRendererSessionManager.removeSourceShapes(level,
                        DebugSourceId.graph(DebugRenderChannel.AREA, owner));
            }
        }
        long tick = level.getGameTime();
        for (Map.Entry<GraphResourceId, List<DebugRenderShape>> entry : shapesByOwner.entrySet()) {
            DebugRendererSessionManager.replaceSourceShapes(level,
                    DebugSourceId.graph(DebugRenderChannel.AREA, entry.getKey()), entry.getValue(), tick);
        }
        if (state != null) {
            if (currentOwners.isEmpty()) state.debugOwnersByDimension.remove(level.dimension());
            else state.debugOwnersByDimension.put(level.dimension(), currentOwners);
        }
    }

    public synchronized void shutdown(MinecraftServer server) {
        servers.remove(server);
    }

    private synchronized void removeOwned(MinecraftServer server, GraphResourceRelease release) {
        ServerState state = servers.get(server);
        if (state == null) return;
        if (state.entries.entrySet().removeIf(entry -> release.matches(entry.getValue().owner()))) {
            state.snapshotsByDimension.clear();
        }
    }

    private static final class ServerState {
        private final Map<AreaAddress, AreaResource> entries = new LinkedHashMap<>();
        private final Map<ResourceKey<Level>, List<AreaResource>> snapshotsByDimension = new HashMap<>();
        private final Map<ResourceKey<Level>, Set<GraphResourceId>> debugOwnersByDimension = new HashMap<>();
        private long generation;
    }
}
