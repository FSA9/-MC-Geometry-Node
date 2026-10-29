package com.mine.geometry_node.core.engine.graph.scoped;

import com.mine.geometry_node.core.engine.metadata.value.GraphMetadataValuePolicy;
import com.mine.geometry_node.core.engine.blueprint.event.dispatcher.CustomDataDispatcher;

import com.mine.geometry_node.core.config.ScopedStateServerConfig;
import com.mine.geometry_node.core.engine.blueprint.spatial.area.AreaRef;
import com.mine.geometry_node.core.engine.blueprint.spatial.area.AreaResource;
import com.mine.geometry_node.core.engine.blueprint.spatial.area.AreaResourceStore;
import com.mine.geometry_node.core.engine.graph.runtime.GraphRuntimeContext;
import com.mine.geometry_node.core.engine.graph.scoped.storage.OwnerScopedStateStore;
import com.mine.geometry_node.core.engine.graph.scoped.storage.ServerScopedStateSavedData;
import com.mine.geometry_node.core.engine.system.scene.SceneMetadataTargets;
import com.mine.geometry_node.core.metadata.DataAccess;
import com.mine.geometry_node.core.node.definition.port.PortType;
import com.mine.geometry_node.core.engine.system.scene.SceneSavedData;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.scores.Team;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;

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
            return global(context.level(), namespace);
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
        requireThread(requireServerLevel(entity.level(), "OWNER"));
        return new OwnerScopedStateProvider(entity, namespace,
                ScopedStateServerConfig.maxEntries(namespace));
    }

    public static ScopedStateProvider global(ServerLevel level, ScopedStateNamespace namespace) {
        return stored(level, namespace, ScopedStateScope.GLOBAL, "server");
    }

    @Deprecated
    public static ScopedStateProvider shared(ServerLevel level, ScopedStateNamespace namespace) {
        return global(level, namespace);
    }

    public static ScopedStateProvider area(ServerLevel level, AreaRef area, ScopedStateNamespace namespace) {
        requireThread(level);
        return new AreaScopedStateProvider(level, area, namespace);
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
        requireThread(level);
        return ServerScopedStateSavedData.get(level).provider(namespace, scope, identity, level,
                ScopedStateServerConfig.maxEntries(namespace));
    }

    private static void requireThread(ServerLevel level) {
        if (!level.getServer().isSameThread() || level.getServer().isStopped())
            throw new ScopedStateAccessException("Scoped state must be accessed on the server owner thread");
    }

    private static ServerLevel requireServerLevel(Level level, String scope) {
        if (level instanceof ServerLevel serverLevel) return serverLevel;
        throw new ScopedStateAccessException(scope + " scoped state is only available on the server");
    }

    /** Choose authority only after resolving this exact live reference; absence is never runtime storage. */
    static DataAccess bindAreaData(AreaRef reference, ScopedStateNamespace namespace,
                                   Supplier<AreaResource> resolve, Function<AreaResource, DataAccess> sceneData) {
        AreaResource resource = resolve.get();
        if (resource == null || !resource.reference().equals(reference))
            throw new ScopedStateAccessException("Area scoped-state target is no longer available: " + reference);
        return resource.isAuthored() && namespace == ScopedStateNamespace.PUBLIC
                ? Objects.requireNonNull(sceneData.apply(resource), "authored Area data") : null;
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
            update(current -> {
                Map<String, Object> next = new java.util.LinkedHashMap<>(current);
                next.put(name, value);
                return next;
            });
        }
        @Override public boolean remove(String name) { return store().remove(namespace, name); }
        @Override public long revision() { return store().revision(namespace); }
        @Override public boolean hasRecord(String name) { return get(name) != null; }
        @Override public int size() { return store().size(namespace); }
        @Override public Map<String, ScopedStateEntry> entries(int limit) {
            return store().entries(namespace, registries(), limit);
        }
        @Override public Map<String, Object> read() { return store().readValues(namespace, registries()); }
        @Override public com.mine.geometry_node.core.metadata.DataValuePolicy valuePolicy() {
            return com.mine.geometry_node.core.engine.metadata.value.GraphMetadataValuePolicy.scoped(registries());
        }
        @Override public boolean update(UnaryOperator<Map<String, Object>> transformation) {
            Entity entity = requireOwner();
            ServerPlayer observer = namespace == ScopedStateNamespace.PUBLIC && entity instanceof ServerPlayer player
                    && CustomDataDispatcher.observes(player) ? player : null;
            java.util.List<CustomDataDispatcher.Change> changes = new java.util.ArrayList<>();
            boolean changed = entity.getData(com.mine.geometry_node.GeometryNode.GRAPH_DATA_ATTACHMENT)
                    .ownerScopedState().update(namespace, current -> {
                        Map<String, Object> before = observer != null ? new java.util.LinkedHashMap<>(current) : Map.of();
                        Map<String, Object> candidate = transformation.apply(current);
                        requireOwner();
                        if (observer != null) changes.addAll(CustomDataDispatcher.changes(before, candidate));
                        return candidate;
                    }, maxEntries,
                            entity.registryAccess(), this::notifyLimit);
            if (changed && observer != null) CustomDataDispatcher.publish(observer, changes);
            return changed;
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
            requireThread((ServerLevel) value.level());
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
        private final DataAccess authoredData;

        private AreaScopedStateProvider(ServerLevel contextLevel, AreaRef area,
                                        ScopedStateNamespace namespace) {
            this.contextLevel = Objects.requireNonNull(contextLevel, "contextLevel");
            this.area = Objects.requireNonNull(area, "area");
            this.namespace = Objects.requireNonNull(namespace, "namespace");
            requireThread(contextLevel);
            this.authoredData = bindAreaData(area, namespace, this::resourceOrNull,
                    resource -> SceneMetadataTargets.access(contextLevel.getServer(),
                            area.address().dimension().identifier().toString(),
                            Objects.requireNonNull(resource.authoredObjectId(), "authored Area scene object")).requireData());
            this.maxEntries = ScopedStateServerConfig.maxEntries(namespace);
        }

        @Override public ScopedStateScope scope() { return ScopedStateScope.AREA; }
        @Override public String identity() {
            return area.address().dimension().identifier() + "/" + area.address().id()
                    + "@" + area.incarnation();
        }
        @Override public boolean available() { return resourceOrNull() != null; }
        @Override public @Nullable ScopedStateEntry get(String name) {
            resource();
            if (authoredData != null) {
                Object value = authoredData.get(name);
                return value == null ? null : new ScopedStateEntry(value, PortType.getTypeOf(value));
            }
            return resource().scopedState().get(
                    namespace, name, contextLevel.registryAccess(), location(name));
        }
        @Override public void put(String name, Object value) {
            resource();
            if (authoredData != null) { authoredData.put(name, value); return; }
            resource().scopedState().put(namespace, name, value, maxEntries,
                    contextLevel.registryAccess(), location(""), this::notifyLimit);
        }
        @Override public boolean remove(String name) {
            resource();
            if (authoredData != null) return authoredData.remove(name);
            return resource().scopedState().remove(namespace, name);
        }
        @Override public boolean hasRecord(String name) { return get(name) != null; }
        @Override public long revision() {
            AreaResource resource = resource();
            if (authoredData != null) {
                authoredData.values(); // Also validate the pinned scene incarnation.
                return SceneSavedData.get(contextLevel.getServer())
                        .document(area.address().dimension().identifier().toString()).object(resource.authoredObjectId()).revision();
            }
            return resource().scopedState().revision(namespace);
        }
        @Override public int size() {
            resource();
            return authoredData != null ? authoredData.values().size() : resource().scopedState().size(namespace);
        }
        @Override public Map<String, ScopedStateEntry> entries(int limit) {
            resource();
            if (authoredData != null) {
                Map<String, ScopedStateEntry> result = new java.util.LinkedHashMap<>();
                if (limit <= 0) return java.util.Collections.unmodifiableMap(result);
                for (Map.Entry<String, Object> entry : authoredData.values().entrySet()) {
                    result.put(entry.getKey(), new ScopedStateEntry(entry.getValue(), PortType.getTypeOf(entry.getValue())));
                    if (result.size() >= limit) break;
                }
                return java.util.Collections.unmodifiableMap(result);
            }
            return resource().scopedState().entries(
                    namespace, contextLevel.registryAccess(), location(""), limit);
        }
        @Override public Map<String, Object> read() {
            resource();
            if (authoredData != null) return authoredData.values();
            return resource().scopedState().readValues(namespace, contextLevel.registryAccess(), location(""));
        }
        @Override public com.mine.geometry_node.core.metadata.DataValuePolicy valuePolicy() {
            return com.mine.geometry_node.core.engine.metadata.value.GraphMetadataValuePolicy.scoped(contextLevel.registryAccess());
        }
        @Override public boolean update(UnaryOperator<Map<String, Object>> transformation) {
            AreaResource current = resource();
            if (authoredData != null) return authoredData.update(transformation);
            return current.scopedState().update(namespace, values -> {
                Map<String, Object> candidate = transformation.apply(values);
                if (resource() != current) throw new ScopedStateAccessException("Area changed during metadata update");
                return candidate;
            }, maxEntries, contextLevel.registryAccess(), location(""), this::notifyLimit);
        }

        private String location(String name) {
            return "area/" + identity() + "/" + namespace.serializedName() + "/" + name;
        }

        private @Nullable AreaResource resourceOrNull() {
            if (contextLevel.getServer().isStopped()) return null;
            return AreaResourceStore.INSTANCE.get(contextLevel.getServer(), area);
        }

        private AreaResource resource() {
            requireThread(contextLevel);
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
        @Override public Map<String, Object> read() { return delegate().read(); }
        @Override public com.mine.geometry_node.core.metadata.DataValuePolicy valuePolicy() {
            return delegate().valuePolicy();
        }
        @Override public boolean update(UnaryOperator<Map<String, Object>> transformation) {
            // Resolve once: a transformation may not read one team's state and commit another's.
            ScopedStateProvider pinned = delegate();
            String identity = pinned.identity();
            return pinned.update(current -> {
                Map<String, Object> candidate = transformation.apply(current);
                if (!available() || !identity.equals(identity()))
                    throw new ScopedStateAccessException("GROUP owner changed teams during metadata update");
                return candidate;
            });
        }

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
            requireThread((ServerLevel) entity.level());
            return entity;
        }

    }

    private static boolean isAvailable(@Nullable Entity entity) {
        return entity != null && !entity.isRemoved() && entity.level() instanceof ServerLevel level
                && !level.getServer().isStopped();
    }
}
