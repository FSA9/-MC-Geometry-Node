package com.mine.geometry_node.core.engine.graph.scoped;

import com.mine.geometry_node.core.engine.graph.runtime.GraphRuntimeContext;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.scores.Team;

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
        return ScopedStateStorage.get(level).provider(namespace, scope, identity, level,
                ScopedStateServerConfig.maxEntries(namespace));
    }

    private static ServerLevel requireServerLevel(Level level, String scope) {
        if (level instanceof ServerLevel serverLevel) return serverLevel;
        throw new ScopedStateAccessException(scope + " scoped state is only available on the server");
    }

    private static final class CurrentGroupProvider implements ScopedStateProvider {
        private final WeakReference<Entity> owner;
        private final ScopedStateNamespace namespace;

        private CurrentGroupProvider(Entity owner, ScopedStateNamespace namespace) {
            this.owner = new WeakReference<>(Objects.requireNonNull(owner, "owner"));
            this.namespace = Objects.requireNonNull(namespace, "namespace");
        }

        @Override public ScopedStateScope scope() { return ScopedStateScope.GROUP; }
        @Override public String identity() {
            Team team = team();
            return team != null ? "scoreboard:" + team.getName() : "";
        }
        @Override public boolean available() {
            Entity entity = owner.get();
            return entity != null && entity.level() instanceof ServerLevel && entity.getTeam() != null;
        }
        @Override public ScopedStateEntry get(String name) { return delegate().get(name); }
        @Override public ScopedStateEntry put(String name, Object value) { return delegate().put(name, value); }
        @Override public boolean remove(String name) { return delegate().remove(name); }
        @Override public boolean hasRecord(String name) { return delegate().hasRecord(name); }
        @Override public Map<String, ScopedStateEntry> entries() { return delegate().entries(); }
        @Override public long revision() { return available() ? delegate().revision() : 0L; }
        @Override public int size() { return delegate().size(); }

        private ScopedStateProvider delegate() {
            Entity entity = owner.get();
            if (entity == null) {
                throw new ScopedStateAccessException("GROUP blackboard owner is unavailable");
            }
            return group(entity, namespace);
        }

        private Team team() {
            Entity entity = owner.get();
            return entity != null ? entity.getTeam() : null;
        }
    }
}
