package com.mine.geometry_node.core.engine.graph.scoped;

import com.mine.geometry_node.core.config.ScopedStateServerConfig;
import com.mine.geometry_node.core.engine.blueprint.spatial.area.AreaRef;
import com.mine.geometry_node.core.engine.blueprint.spatial.area.AreaResource;
import com.mine.geometry_node.core.engine.blueprint.spatial.area.AreaResourceStore;
import com.mine.geometry_node.core.engine.graph.runtime.GraphRuntimeContext;
import com.mine.geometry_node.core.engine.graph.scoped.storage.OwnerScopedStateStore;
import com.mine.geometry_node.core.engine.graph.scoped.storage.ServerScopedStateSavedData;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.scores.Team;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.Objects;

/** Resolves public scoped-state targets to the shared storage providers. */
public final class ScopedStateProviderResolver {
    private ScopedStateProviderResolver() {
    }

    public static ScopedStateProvider resolve(GraphRuntimeContext context,
                                              ScopedStateNamespace namespace,
                                              ScopedStateTarget target) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(target, "target");
        if (target instanceof ScopedStateTarget.OwnerTarget ownerTarget) {
            return owner(ownerTarget.entity(), namespace);
        }
        if (target instanceof ScopedStateTarget.AreaTarget areaTarget) {
            return new AreaScopedStateProvider(context.level(), areaTarget.area(), namespace);
        }
        if (target instanceof ScopedStateTarget.SharedTarget) {
            return shared(context.level(), namespace);
        }
        if (target instanceof ScopedStateTarget.GroupTarget groupTarget) {
            return group(groupTarget.entity(), namespace);
        }
        if (target instanceof ScopedStateTarget.WorldTarget world) {
            return world(context.level(), world.dimensionId(), namespace);
        }
        throw new ScopedStateAccessException(
                "Unsupported scoped state target: " + target.getClass().getSimpleName());
    }

    public static ScopedStateProvider owner(Entity entity, ScopedStateNamespace namespace) {
        requireServerLevel(entity.level(), "OWNER");
        return new OwnerScopedStateProvider(entity, namespace,
                ScopedStateServerConfig.maxEntries(namespace));
    }

    public static ScopedStateProvider shared(ServerLevel level, ScopedStateNamespace namespace) {
        return stored(level, namespace, ScopedStateScope.SHARED, "server");
    }

    public static ScopedStateProvider world(ServerLevel level, ScopedStateNamespace namespace) {
        return stored(level, namespace, ScopedStateScope.WORLD,
                level.dimension().identifier().toString());
    }

    public static ScopedStateProvider world(ServerLevel contextLevel, String dimensionId,
                                            ScopedStateNamespace namespace) {
        Identifier id = Identifier.tryParse(dimensionId);
        if (id == null) {
            throw new ScopedStateAccessException("Invalid WORLD dimension id: " + dimensionId);
        }
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, id);
        ServerLevel level = contextLevel.getServer().getLevel(key);
        if (level == null) {
            throw new ScopedStateAccessException("WORLD dimension is unavailable: " + dimensionId);
        }
        return world(level, namespace);
    }

    public static ScopedStateProvider group(Entity entity, ScopedStateNamespace namespace) {
        ServerLevel level = requireServerLevel(entity.level(), "GROUP");
        Team team = entity.getTeam();
        if (team == null) {
            throw new ScopedStateAccessException(
                    "GROUP scoped state requires an entity in a scoreboard team");
        }
        return stored(level, namespace, ScopedStateScope.GROUP,
                "scoreboard:" + team.getName());
    }

    /** Provider that follows the owner's current scoreboard team between accesses. */
    public static ScopedStateProvider currentGroup(Entity entity,
                                                   ScopedStateNamespace namespace) {
        return new CurrentGroupProvider(entity, namespace);
    }

    private static ScopedStateProvider stored(ServerLevel level,
                                              ScopedStateNamespace namespace,
                                              ScopedStateScope scope,
                                              String identity) {
        return ServerScopedStateSavedData.get(level).provider(namespace, scope, identity, level,
                ScopedStateServerConfig.maxEntries(namespace));
    }

    private static ServerLevel requireServerLevel(Level level, String scope) {
        if (level instanceof ServerLevel serverLevel) return serverLevel;
        throw new ScopedStateAccessException(scope + " scoped state is only available on the server");
    }

    /** OWNER adapter backed by the entity's serialized graph attachment. */
    private static final class OwnerScopedStateProvider implements ScopedStateProvider {
        private final String ownerId;
        private final WeakReference<Entity> owner;
        private final ScopedStateNamespace namespace;
        private final int maxEntries;

        private OwnerScopedStateProvider(Entity owner, ScopedStateNamespace namespace,
                                         int maxEntries) {
            Entity value = Objects.requireNonNull(owner, "owner");
            this.ownerId = value.getUUID().toString();
            this.owner = new WeakReference<>(value);
            this.namespace = Objects.requireNonNull(namespace, "namespace");
            if (maxEntries <= 0) throw new IllegalArgumentException("maxEntries must be positive");
            this.maxEntries = maxEntries;
        }

        @Override public ScopedStateScope scope() { return ScopedStateScope.OWNER; }
        @Override public String identity() { return ownerId; }
        @Override public boolean available() { return isAvailable(owner.get()); }
        @Override public @Nullable ScopedStateEntry get(String name) {
            return store().get(namespace, name, registries());
        }
        @Override public void put(String name, Object value) {
            store().put(namespace, name, value, maxEntries, registries(), this::notifyLimit);
        }
        @Override public boolean remove(String name) { return store().remove(namespace, name); }
        @Override public long revision() { return store().revision(namespace); }
        @Override public boolean hasRecord(String name) { return get(name) != null; }
        @Override public int size() { return store().size(namespace); }
        @Override public Map<String, ScopedStateEntry> entries(int limit) {
            return store().entries(namespace, registries(), limit);
        }

        private OwnerScopedStateStore store() {
            return requireOwner().getData(com.mine.geometry_node.GeometryNode.GRAPH_DATA_ATTACHMENT)
                    .ownerScopedState();
        }

        private net.minecraft.core.HolderLookup.Provider registries() {
            return requireOwner().registryAccess();
        }

        private Entity requireOwner() {
            Entity value = owner.get();
            if (!isAvailable(value)) {
                throw new ScopedStateAccessException("Blackboard owner is unavailable: " + ownerId);
            }
            return value;
        }

        private void notifyLimit() {
            ServerLevel level = (ServerLevel) requireOwner().level();
            ScopedStateLimitNotifier.notifyLimit(
                    level, namespace, ScopedStateScope.OWNER, ownerId, maxEntries);
        }
    }

    /** AREA adapter backed by one live Area incarnation. */
    private static final class AreaScopedStateProvider implements ScopedStateProvider {
        private final ServerLevel contextLevel;
        private final AreaRef area;
        private final ScopedStateNamespace namespace;
        private final int maxEntries;

        private AreaScopedStateProvider(ServerLevel contextLevel, AreaRef area,
                                        ScopedStateNamespace namespace) {
            this.contextLevel = Objects.requireNonNull(contextLevel, "contextLevel");
            this.area = Objects.requireNonNull(area, "area");
            this.namespace = Objects.requireNonNull(namespace, "namespace");
            this.maxEntries = ScopedStateServerConfig.maxEntries(namespace);
        }

        @Override public ScopedStateScope scope() { return ScopedStateScope.AREA; }
        @Override public String identity() {
            return area.address().dimension().identifier() + "/" + area.address().id()
                    + "@" + area.incarnation();
        }
        @Override public boolean available() { return resourceOrNull() != null; }
        @Override public @Nullable ScopedStateEntry get(String name) {
            return resource().scopedState().get(
                    namespace, name, contextLevel.registryAccess(), location(name));
        }
        @Override public void put(String name, Object value) {
            resource().scopedState().put(namespace, name, value, maxEntries,
                    contextLevel.registryAccess(), location(""), this::notifyLimit);
        }
        @Override public boolean remove(String name) {
            return resource().scopedState().remove(namespace, name);
        }
        @Override public boolean hasRecord(String name) { return get(name) != null; }
        @Override public long revision() { return resource().scopedState().revision(namespace); }
        @Override public int size() { return resource().scopedState().size(namespace); }
        @Override public Map<String, ScopedStateEntry> entries(int limit) {
            return resource().scopedState().entries(
                    namespace, contextLevel.registryAccess(), location(""), limit);
        }

        private String location(String name) {
            return "area/" + identity() + "/" + namespace.serializedName() + "/" + name;
        }

        private @Nullable AreaResource resourceOrNull() {
            return AreaResourceStore.INSTANCE.get(contextLevel.getServer(), area);
        }

        private AreaResource resource() {
            AreaResource resource = resourceOrNull();
            if (resource == null) {
                throw new ScopedStateAccessException(
                        "Area scoped-state target is no longer available: " + identity());
            }
            return resource;
        }

        private void notifyLimit() {
            ScopedStateLimitNotifier.notifyLimit(
                    contextLevel, namespace, ScopedStateScope.AREA, identity(), maxEntries);
        }
    }

    private static final class CurrentGroupProvider implements ScopedStateProvider {
        private final WeakReference<Entity> owner;
        private final String ownerId;
        private final ScopedStateNamespace namespace;

        private CurrentGroupProvider(Entity owner, ScopedStateNamespace namespace) {
            Entity value = Objects.requireNonNull(owner, "owner");
            this.owner = new WeakReference<>(value);
            this.ownerId = value.getUUID().toString();
            this.namespace = Objects.requireNonNull(namespace, "namespace");
        }

        @Override public ScopedStateScope scope() { return ScopedStateScope.GROUP; }
        @Override public String identity() {
            Team team = team();
            return team != null ? "scoreboard:" + team.getName() : "";
        }
        @Override public boolean available() {
            Entity entity = owner.get();
            return isAvailable(entity) && entity.getTeam() != null;
        }
        @Override public ScopedStateEntry get(String name) { return delegate().get(name); }
        @Override public void put(String name, Object value) { delegate().put(name, value); }
        @Override public boolean remove(String name) { return delegate().remove(name); }
        @Override public boolean hasRecord(String name) { return delegate().hasRecord(name); }
        @Override public Map<String, ScopedStateEntry> entries(int limit) {
            return delegate().entries(limit);
        }
        @Override public long revision() { return available() ? delegate().revision() : 0L; }
        @Override public int size() { return delegate().size(); }

        private ScopedStateProvider delegate() {
            Entity entity = requireOwner();
            return group(entity, namespace);
        }

        private Team team() {
            Entity entity = owner.get();
            return isAvailable(entity) ? entity.getTeam() : null;
        }

        private Entity requireOwner() {
            Entity entity = owner.get();
            if (!isAvailable(entity)) {
                throw new ScopedStateAccessException(
                        "GROUP blackboard owner is unavailable: " + ownerId);
            }
            return entity;
        }

    }

    private static boolean isAvailable(@Nullable Entity entity) {
        return entity != null && !entity.isRemoved() && entity.level() instanceof ServerLevel;
    }
}
