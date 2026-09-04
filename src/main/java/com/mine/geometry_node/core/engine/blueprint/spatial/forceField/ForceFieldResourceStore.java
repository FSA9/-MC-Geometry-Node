package com.mine.geometry_node.core.engine.blueprint.spatial.forceField;

import com.mine.geometry_node.core.engine.blueprint.spatial.area.AreaAddress;
import com.mine.geometry_node.core.engine.graph.expression.LiveValue;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceId;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceLifecycleManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Predicate;

/** Runtime store for non-persistent force fields addressed by dimension + ID. */
public final class ForceFieldResourceStore {
    public static final ForceFieldResourceStore INSTANCE = new ForceFieldResourceStore();
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

    public synchronized void shutdown(MinecraftServer server) {
        servers.remove(server);
    }

    private synchronized void removeOwned(MinecraftServer server, Predicate<GraphResourceId> predicate) {
        ServerState state = servers.get(server);
        if (state != null) state.entries.entrySet().removeIf(entry -> predicate.test(entry.getValue().owner()));
    }

    private static final class ServerState {
        private final Map<ForceFieldAddress, ForceFieldResource> entries = new HashMap<>();
        private long generation;
    }
}
