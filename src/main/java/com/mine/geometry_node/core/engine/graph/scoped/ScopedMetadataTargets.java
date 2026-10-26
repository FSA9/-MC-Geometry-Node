package com.mine.geometry_node.core.engine.graph.scoped;

import com.mine.geometry_node.core.engine.metadata.value.GraphMetadataValuePolicy;

import com.mine.geometry_node.core.engine.blueprint.spatial.area.AreaRef;
import com.mine.geometry_node.core.engine.graph.runtime.GraphRuntimeContext;
import com.mine.geometry_node.core.engine.graph.scoped.*;
import com.mine.geometry_node.core.metadata.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.Objects;
import java.util.Set;

/** Registered typed bindings for the existing scoped stores. No storage or GLOBAL registration here. */
public final class ScopedMetadataTargets {
    private ScopedMetadataTargets() {}
    public record OwnerTarget(Entity owner, ScopedStateNamespace namespace) {}
    public record AreaTarget(ServerLevel level, AreaRef reference, ScopedStateNamespace namespace) {}
    public record GlobalTarget(ServerLevel level, ScopedStateNamespace namespace) {}
    public record SharedTarget(ServerLevel level, ScopedStateNamespace namespace) {}
    public record WorldTarget(ServerLevel level, String dimensionId, ScopedStateNamespace namespace) {}
    public record GroupTarget(Entity owner, ScopedStateNamespace namespace) {}

    public static void register(MetadataCapabilities registry) {
        Set<MetadataCapabilities.Capability> data = Set.of(MetadataCapabilities.Capability.DATA);
        registry.register("geometry_node:scoped_owner", OwnerTarget.class, data,
                target -> new MetadataCapabilities.Access(null, owner(target.owner(), target.namespace())));
        registry.register("geometry_node:scoped_area", AreaTarget.class, data,
                target -> new MetadataCapabilities.Access(null, area(target.level(), target.reference(), target.namespace())));
        registry.register("geometry_node:scoped_global", GlobalTarget.class, data,
                target -> new MetadataCapabilities.Access(null, global(target.level(), target.namespace())));
        registry.register("geometry_node:scoped_shared", SharedTarget.class, data,
                target -> new MetadataCapabilities.Access(null, shared(target.level(), target.namespace())));
        registry.register("geometry_node:scoped_world", WorldTarget.class, data,
                target -> new MetadataCapabilities.Access(null, world(target.level(), target.dimensionId(), target.namespace())));
        registry.register("geometry_node:scoped_group", GroupTarget.class, data,
                target -> new MetadataCapabilities.Access(null, group(target.owner(), target.namespace())));
    }

    public static DataAccess data(ScopedStateProvider provider) {
        return new DataAccess(Objects.requireNonNull(provider), provider.valuePolicy());
    }
    public static DataAccess resolve(GraphRuntimeContext context, ScopedStateNamespace namespace, ScopedStateTarget target) {
        return data(ScopedStateProviderResolver.resolve(context, namespace, target));
    }
    public static DataAccess owner(Entity owner, ScopedStateNamespace namespace) {
        return new DataAccess(ScopedStateProviderResolver.owner(owner, namespace), GraphMetadataValuePolicy.scoped(owner.registryAccess()));
    }
    public static DataAccess area(ServerLevel level, AreaRef reference, ScopedStateNamespace namespace) {
        Objects.requireNonNull(level, "Area level is unavailable");
        return new DataAccess(ScopedStateProviderResolver.area(level, reference, namespace), GraphMetadataValuePolicy.scoped(level.registryAccess()));
    }
    public static DataAccess global(ServerLevel level, ScopedStateNamespace namespace) {
        return new DataAccess(ScopedStateProviderResolver.global(level, namespace), GraphMetadataValuePolicy.scoped(level.registryAccess()));
    }
    @Deprecated
    public static DataAccess shared(ServerLevel level, ScopedStateNamespace namespace) {
        return global(level, namespace);
    }
    public static DataAccess world(ServerLevel level, String dimensionId, ScopedStateNamespace namespace) {
        return new DataAccess(ScopedStateProviderResolver.world(level, dimensionId, namespace), GraphMetadataValuePolicy.scoped(level.registryAccess()));
    }
    public static DataAccess group(Entity owner, ScopedStateNamespace namespace) {
        return new DataAccess(ScopedStateProviderResolver.currentGroup(owner, namespace), GraphMetadataValuePolicy.scoped(owner.registryAccess()));
    }
}
