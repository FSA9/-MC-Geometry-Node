package com.mine.geometry_node.core.engine.blueprint.spatial.forceField;

import com.mine.geometry_node.core.engine.metadata.state.MetadataOwnerThread;

import com.mine.geometry_node.core.engine.metadata.state.RuntimeMetadata;
import com.mine.geometry_node.core.engine.metadata.value.GraphMetadataValuePolicy;
import com.mine.geometry_node.core.metadata.MetadataCapabilities;
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

/** Derived force fields addressed by dimension + ID; graph and authored scene ownership stay separate. */
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
            if (address.id().startsWith("scene/") || state.authored.containsKey(address))
                throw new IllegalArgumentException("Reserved scene force-field address");
            ForceFieldResource resource = new ForceFieldResource(address, owner, ++state.generation,
                    area, creationGameTime, strength,
                    areaResource != null ? areaResource.anchorEntityId() : null);
            state.entries.put(address, resource);
            state.snapshotsByDimension.remove(address.dimension());
            return resource;
        }
    }

    /** Only scene runtime can write this bucket; graph release/remove never touches it. */
    public synchronized void putAuthored(MinecraftServer server, ForceFieldResource field) {
        if (!field.isAuthored() || !field.address().id().startsWith("scene/")
                || !field.area().dimension().equals(field.address().dimension())
                || !field.area().id().startsWith("scene/"))
            throw new IllegalArgumentException("Expected authored scene force field and area");
        var area = AreaResourceStore.INSTANCE.get(server, field.area());
        if (area == null || !area.isAuthored() || !field.authoredObjectId().equals(area.authoredObjectId()))
            throw new IllegalArgumentException("Force field must own its authored area");
        ServerState state = servers.computeIfAbsent(server, ignored -> new ServerState());
        if (state.entries.containsKey(field.address())) throw new IllegalArgumentException("Force-field address already exists");
        state.authored.put(field.address(), field);
        state.snapshotsByDimension.remove(field.address().dimension());
    }

    public synchronized void removeAuthored(MinecraftServer server, ForceFieldAddress address) {
        ServerState state = servers.get(server);
        if (state != null && state.authored.remove(address) != null) state.snapshotsByDimension.remove(address.dimension());
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
        return state != null ? state.entries.getOrDefault(address, state.authored.get(address)) : null;
    }

    public MetadataCapabilities.Access metadata(MinecraftServer server, ForceFieldAddress address) {
        MetadataOwnerThread.requireCurrent(server);
        ForceFieldResource resource = get(server, address);
        if (resource != null && resource.isAuthored())
            return com.mine.geometry_node.core.engine.system.scene.SceneMetadataTargets.access(server,
                    address.dimension().identifier().toString(), resource.authoredObjectId());
        return metadata(server, address, GraphMetadataValuePolicy.of(server.registryAccess()),
                () -> MetadataOwnerThread.requireCurrent(server));
    }

    synchronized MetadataCapabilities.Access metadata(MinecraftServer server, ForceFieldAddress address,
            com.mine.geometry_node.core.metadata.DataValuePolicy policy, Runnable requireThread) {
        requireThread.run();
        ForceFieldResource resource = get(server, address);
        if (resource == null) throw new IllegalStateException("Force field no longer exists");
        if (resource.isAuthored()) throw new IllegalArgumentException("Authored metadata belongs to the scene owner");
        RuntimeMetadata metadata = resource.metadata;
        return metadata.access(policy, () -> {
            requireThread.run();
            ForceFieldResource current = get(server, address);
            if (current != resource)
                throw new IllegalStateException("Force field metadata handle is stale");
        });
    }

    public synchronized long metadataRevision(MinecraftServer server, ForceFieldAddress address) {
        metadata(server, address);
        ForceFieldResource resource = get(server, address);
        return resource.isAuthored() ? com.mine.geometry_node.core.engine.system.scene.SceneSavedData.get(server)
                .document(address.dimension().identifier().toString()).object(resource.authoredObjectId()).revision()
                : resource.metadata.revision();
    }

    public synchronized boolean remove(MinecraftServer server, ForceFieldAddress address) {
        ServerState state = servers.get(server);
        if (state == null || state.entries.remove(address) == null) return false;
        state.snapshotsByDimension.remove(address.dimension());
        return true;
    }

    /** Graph-owned entries only; caller stops the visitor before resolving live inputs. */
    public synchronized void visitWorldQuery(MinecraftServer server, java.util.function.Predicate<ForceFieldResource> visitor) {
        ServerState state = servers.get(server);
        if (state != null) for (var resource : state.entries.values()) if (!visitor.test(resource)) return;
    }

    public synchronized List<ForceFieldResource> snapshot(ServerLevel level) {
        ServerState state = servers.get(level.getServer());
        if (state == null || (state.entries.isEmpty() && state.authored.isEmpty())) return List.of();
        List<ForceFieldResource> cached = state.snapshotsByDimension.get(level.dimension());
        if (cached != null) return cached;
        List<ForceFieldResource> result = new ArrayList<>();
        for (ForceFieldResource resource : state.entries.values()) {
            if (resource.address().dimension().equals(level.dimension())) result.add(resource);
        }
        for (ForceFieldResource resource : state.authored.values()) {
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
                return release.matches(resource.owner()) || (resource.address().dimension().equals(entityRelease.dimension())
                        && entityRelease.entityId().equals(resource.anchorEntityId()));
            }
            return release.matches(resource.owner());
        })) {
            state.snapshotsByDimension.clear();
        }
    }

    private static final class ServerState {
        private final Map<ForceFieldAddress, ForceFieldResource> entries = new HashMap<>();
        private final Map<ForceFieldAddress, ForceFieldResource> authored = new HashMap<>();
        private final Map<ResourceKey<Level>, List<ForceFieldResource>> snapshotsByDimension = new HashMap<>();
        private long generation;
    }
}
