package com.mine.geometry_node.core.engine.blueprint.spatial.forceField;

import com.mine.geometry_node.core.engine.blueprint.spatial.area.AreaAddress;
import com.mine.geometry_node.core.engine.blueprint.spatial.area.AreaResource;
import com.mine.geometry_node.core.engine.blueprint.spatial.area.AreaResourceStore;
import com.mine.geometry_node.core.engine.graph.debug.DebugRenderChannel;
import com.mine.geometry_node.core.engine.graph.debug.DebugRenderShape;
import com.mine.geometry_node.core.engine.graph.debug.DebugRendererSessionManager;
import com.mine.geometry_node.core.engine.graph.debug.DebugSourceId;
import com.mine.geometry_node.core.engine.graph.debug.DebugSourceIdCodec;
import com.mine.geometry_node.core.engine.graph.expression.LiveValue;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceId;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceLifecycleManager;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Predicate;

/** Runtime store for non-persistent force fields addressed by dimension + ID. */
public final class ForceFieldResourceStore {
    public static final ForceFieldResourceStore INSTANCE = new ForceFieldResourceStore();
    private static final int ATTRACT_COLOR = 0xFF55B96B;
    private static final int REPEL_COLOR = 0xFFE05A5A;
    private static final Vec3 CENTER_MARKER_SIZE = new Vec3(0.3D, 0.3D, 0.3D);

    private final Map<MinecraftServer, ServerState> servers = new WeakHashMap<>();

    private ForceFieldResourceStore() {
        GraphResourceLifecycleManager.INSTANCE.registerStore("blueprint_force_field", this::removeOwned);
    }

    public synchronized ForceFieldResource upsert(MinecraftServer server, ForceFieldAddress address,
                                                   GraphResourceId owner, AreaAddress area,
                                                   long creationGameTime, LiveValue<Float> strength) {
        ServerState state = servers.computeIfAbsent(server, ignored -> new ServerState());
        ForceFieldResource resource = new ForceFieldResource(address, owner, ++state.generation,
                area, creationGameTime, strength);
        state.entries.put(address, resource);
        return resource;
    }

    @Nullable
    public synchronized ForceFieldResource get(MinecraftServer server, ForceFieldAddress address) {
        ServerState state = servers.get(server);
        return state != null ? state.entries.get(address) : null;
    }

    public synchronized boolean remove(MinecraftServer server, ForceFieldAddress address) {
        ServerState state = servers.get(server);
        return state != null && state.entries.remove(address) != null;
    }

    public synchronized List<ForceFieldResource> snapshot(ServerLevel level) {
        ServerState state = servers.get(level.getServer());
        if (state == null || state.entries.isEmpty()) return List.of();
        List<ForceFieldResource> result = new ArrayList<>();
        for (ForceFieldResource resource : state.entries.values()) {
            if (resource.address().dimension().equals(level.dimension())) result.add(resource);
        }
        return result;
    }

    public synchronized void tickDebug(ServerLevel level) {
        ServerState state = servers.get(level.getServer());
        Set<GraphResourceId> previousOwners = state != null
                ? state.debugOwnersByDimension.getOrDefault(level.dimension(), Set.of())
                : Set.of();
        Map<GraphResourceId, List<DebugRenderShape>> shapesByOwner = new LinkedHashMap<>();
        if (state != null && DebugRendererSessionManager.hasAreaSessions()) {
            for (ForceFieldResource field : state.entries.values()) {
                if (!field.address().dimension().equals(level.dimension())) continue;
                AreaResource areaResource = AreaResourceStore.INSTANCE.get(level.getServer(), field.area());
                AreaResource.Resolved area = areaResource != null ? areaResource.resolve(level) : null;
                if (area == null) continue;

                int color = field.currentStrength() >= 0.0F ? ATTRACT_COLOR : REPEL_COLOR;
                DebugSourceId source = DebugSourceId.graph(DebugRenderChannel.AREA, field.owner());
                List<DebugRenderShape> shapes = shapesByOwner.computeIfAbsent(
                        field.owner(), ignored -> new ArrayList<>());
                shapes.add(new DebugRenderShape(
                        DebugSourceIdCodec.element(source, field.address().id() + ":boundary"),
                        field.owner().binding().graphId(), area.shape().id(), area.center(),
                        area.size(), area.rotation(), color));
                shapes.add(new DebugRenderShape(
                        DebugSourceIdCodec.element(source, field.address().id() + ":center"),
                        field.owner().binding().graphId(), "sphere", area.center(),
                        CENTER_MARKER_SIZE, Vec3.ZERO, color));
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
        if (state != null) state.entries.entrySet().removeIf(entry -> predicate.test(entry.getValue().owner()));
    }

    private static final class ServerState {
        private final Map<ForceFieldAddress, ForceFieldResource> entries = new HashMap<>();
        private final Map<ResourceKey<Level>, Set<GraphResourceId>> debugOwnersByDimension = new HashMap<>();
        private long generation;
    }
}
