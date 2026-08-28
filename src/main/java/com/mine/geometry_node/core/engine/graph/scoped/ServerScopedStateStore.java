package com.mine.geometry_node.core.engine.graph.scoped;

import com.mine.geometry_node.core.engine.graph.runtime.GraphRuntimeContext;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.scores.Team;
import org.jetbrains.annotations.Nullable;

/**
 * Blueprint adapter for the shared scoped-state backend used by behavior blackboards.
 * INSTANCE remains behavior-frame private and is intentionally not addressable here.
 */
public final class ServerScopedStateStore implements ScopedStateStore {
    private static final int MAX_KEY_LENGTH = 256;
    private static final String BLUEPRINT_SOURCE = "blueprint";

    @Override
    public void set(GraphRuntimeContext context, ScopedStateNamespace namespace,
                    ScopedStateTarget target, String name, Object value) {
        String key = requireKey(name);
        if (value == null) {
            throw new ScopedStateAccessException(
                    "Scoped state value cannot be Java null: " + key);
        }
        ResolvedTarget resolved = resolve(context, namespace, target);
        resolved.provider().put(key, value, BLUEPRINT_SOURCE, resolved.level().getGameTime());
    }

    @Override
    public @Nullable Object get(GraphRuntimeContext context,
                                ScopedStateNamespace namespace,
                                ScopedStateTarget target, String name) {
        ScopedStateEntry entry = resolve(context, namespace, target).provider().get(requireKey(name));
        return entry != null ? entry.value() : null;
    }

    @Override
    public boolean has(GraphRuntimeContext context,
                       ScopedStateNamespace namespace,
                       ScopedStateTarget target, String name) {
        return resolve(context, namespace, target).provider().hasRecord(requireKey(name));
    }

    @Override
    public boolean clear(GraphRuntimeContext context,
                         ScopedStateNamespace namespace,
                         ScopedStateTarget target, String name) {
        String key = requireKey(name);
        ResolvedTarget resolved = resolve(context, namespace, target);
        if (!resolved.provider().hasRecord(key)) return false;
        resolved.provider().remove(key, BLUEPRINT_SOURCE, resolved.level().getGameTime());
        return true;
    }

    private static ResolvedTarget resolve(GraphRuntimeContext context,
                                          ScopedStateNamespace namespace,
                                          ScopedStateTarget target) {
        java.util.Objects.requireNonNull(context, "context");
        java.util.Objects.requireNonNull(namespace, "namespace");
        java.util.Objects.requireNonNull(target, "target");
        if (target instanceof ScopedStateTarget.OwnerTarget ownerTarget) {
            Entity entity = requireEntity(ownerTarget.entity(), "OWNER");
            ServerLevel level = requireServerLevel(entity.level(), "OWNER");
            return new ResolvedTarget(new OwnerScopedStateProvider(
                    entity, namespace, ScopedStateServerConfig.maxEntries(namespace)), level);
        }
        if (target instanceof ScopedStateTarget.SharedTarget) {
            ServerLevel level = requireContextLevel(context, "SHARED");
            return new ResolvedTarget(ScopedStateStorage.get(level).provider(
                    namespace, ScopedStateScope.SHARED, "server", level,
                    ScopedStateServerConfig.maxEntries(namespace)), level);
        }
        if (target instanceof ScopedStateTarget.GroupTarget groupTarget) {
            Entity entity = requireEntity(groupTarget.entity(), "GROUP");
            ServerLevel level = requireServerLevel(entity.level(), "GROUP");
            Team team = entity.getTeam();
            if (team == null) {
                throw new ScopedStateAccessException(
                        "GROUP scoped state requires an entity in a scoreboard team");
            }
            String identity = "scoreboard:" + team.getName();
            return new ResolvedTarget(ScopedStateStorage.get(level).provider(
                    namespace, ScopedStateScope.GROUP, identity, level,
                    ScopedStateServerConfig.maxEntries(namespace)), level);
        }
        if (target instanceof ScopedStateTarget.WorldTarget worldTarget) {
            ServerLevel contextLevel = requireContextLevel(context, "WORLD");
            ServerLevel level = resolveLevel(contextLevel, worldTarget.dimensionId());
            return new ResolvedTarget(ScopedStateStorage.get(level).provider(
                    namespace, ScopedStateScope.WORLD,
                    level.dimension().identifier().toString(), level,
                    ScopedStateServerConfig.maxEntries(namespace)), level);
        }
        throw new ScopedStateAccessException(
                "Unsupported scoped state target: " + target.getClass().getSimpleName());
    }

    private static ServerLevel resolveLevel(ServerLevel contextLevel, String dimensionId) {
        Identifier id = Identifier.tryParse(dimensionId);
        if (id == null) {
            throw new ScopedStateAccessException(
                    "Invalid WORLD dimension id: " + dimensionId);
        }
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, id);
        ServerLevel level = contextLevel.getServer().getLevel(key);
        if (level == null) {
            throw new ScopedStateAccessException(
                    "WORLD dimension is unavailable: " + dimensionId);
        }
        return level;
    }

    private static String requireKey(String name) {
        String key = name != null ? name.trim() : "";
        if (key.isEmpty()) {
            throw new ScopedStateAccessException("Scoped state key cannot be empty");
        }
        if (key.length() > MAX_KEY_LENGTH) {
            throw new ScopedStateAccessException(
                    "Scoped state key exceeds " + MAX_KEY_LENGTH + " characters");
        }
        return key;
    }

    private static Entity requireEntity(@Nullable Entity entity, String scope) {
        if (entity == null) {
            throw new ScopedStateAccessException(
                    scope + " scoped state requires an entity target");
        }
        return entity;
    }

    private static ServerLevel requireContextLevel(@Nullable GraphRuntimeContext context, String scope) {
        if (context == null) {
            throw new ScopedStateAccessException(
                    scope + " scoped state requires a server runtime context");
        }
        return context.level();
    }

    private static ServerLevel requireServerLevel(Level level, String scope) {
        if (level instanceof ServerLevel serverLevel) return serverLevel;
        throw new ScopedStateAccessException(
                scope + " scoped state is only available on the server");
    }

    private record ResolvedTarget(ScopedStateProvider provider, ServerLevel level) {
    }
}
