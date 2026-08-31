package com.mine.geometry_node.core.engine.blueprint.spatial.area;

import com.mine.geometry_node.core.engine.graph.resource.GraphResourceId;
import com.mine.geometry_node.core.engine.graph.expression.LiveValue;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceLifecycleManager;
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
import java.util.function.Predicate;

/** Runtime store for public Areas. Entries are non-persistent and owned by graph bindings. */
public final class AreaResourceStore {
    public static final AreaResourceStore INSTANCE = new AreaResourceStore();

    private final Map<MinecraftServer, ServerState> servers = new WeakHashMap<>();

    private AreaResourceStore() {
        GraphResourceLifecycleManager.INSTANCE.registerStore("blueprint_area", this::removeOwned);
    }

    public synchronized AreaResource upsert(MinecraftServer server, AreaAddress address,
                                            GraphResourceId owner, AreaShape shape,
                                            long creationGameTime,
                                            LiveValue<Vec3> center, LiveValue<Vec3> size,
                                            LiveValue<Vec3> rotation, LiveValue<Float> radius,
                                            LiveValue<Float> height,
                                            @Nullable UUID anchorEntityId) {
        ServerState state = servers.computeIfAbsent(server, ignored -> new ServerState());
        AreaResource resource = new AreaResource(address, owner, ++state.generation,
                shape, creationGameTime, center, size, rotation, radius, height, anchorEntityId);
        state.entries.put(address, resource);
        return resource;
    }

    @Nullable
    public synchronized AreaResource get(MinecraftServer server, AreaAddress address) {
        ServerState state = servers.get(server);
        return state != null ? state.entries.get(address) : null;
    }

    public synchronized boolean remove(MinecraftServer server, AreaAddress address) {
        ServerState state = servers.get(server);
        return state != null && state.entries.remove(address) != null;
    }

    public synchronized void tickDebug(ServerLevel level) {
        ServerState state = servers.get(level.getServer());
        Set<GraphResourceId> previousOwners = state != null
                ? state.debugOwnersByDimension.getOrDefault(level.dimension(), Set.of())
                : Set.of();
        Map<GraphResourceId, List<DebugRenderShape>> shapesByOwner = new LinkedHashMap<>();
        if (state != null && DebugRendererSessionManager.hasAreaSessions()) {
            for (AreaResource resource : state.entries.values()) {
                if (!resource.address().dimension().equals(level.dimension())) continue;
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

    private synchronized void removeOwned(MinecraftServer server, Predicate<GraphResourceId> predicate) {
        ServerState state = servers.get(server);
        if (state == null) return;
        state.entries.entrySet().removeIf(entry -> predicate.test(entry.getValue().owner()));
    }

    private static final class ServerState {
        private final Map<AreaAddress, AreaResource> entries = new HashMap<>();
        private final Map<ResourceKey<Level>, Set<GraphResourceId>> debugOwnersByDimension = new HashMap<>();
        private long generation;
    }
}
