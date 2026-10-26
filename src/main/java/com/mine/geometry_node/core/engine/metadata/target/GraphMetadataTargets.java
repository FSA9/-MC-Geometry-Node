package com.mine.geometry_node.core.engine.metadata.target;

import com.mine.geometry_node.core.engine.graph.scoped.ScopedMetadataTargets;
import com.mine.geometry_node.core.engine.system.entity.EntityMetadataAdapter;
import com.mine.geometry_node.core.engine.system.faction.service.FactionMetadataTargets;

import com.mine.geometry_node.core.engine.graph.data.GraphDataContext;
import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateNamespace;
import com.mine.geometry_node.core.metadata.MetadataCapabilities;
import net.minecraft.server.level.ServerLevel;
import java.util.Objects;

/** Resolves node targets through registered adapters without introducing another metadata store. */
public final class GraphMetadataTargets {
    private GraphMetadataTargets() {}

    public static MetadataCapabilities.Access resolve(GraphDataContext context, GraphMetadataTarget target) {
        Objects.requireNonNull(target);
        MetadataCapabilities registry = MetadataCapabilities.GLOBAL;
        if (target instanceof GraphMetadataTarget.EntityTarget entity)
            return registry.resolve("geometry_node:entity", new EntityMetadataAdapter.Target(entity.entity()));
        ServerLevel level = Objects.requireNonNull(context.getLevel(), "Metadata execution level is unavailable");
        return switch (target) {
            case GraphMetadataTarget.EntityTarget ignored -> throw new AssertionError();
            case GraphMetadataTarget.DimensionTarget dimension -> registry.resolve("geometry_node:scoped_world",
                    new ScopedMetadataTargets.WorldTarget(level, dimension.dimension(), ScopedStateNamespace.PUBLIC));
            case GraphMetadataTarget.GlobalTarget ignored -> registry.resolve("geometry_node:scoped_global",
                    new ScopedMetadataTargets.GlobalTarget(level, ScopedStateNamespace.PUBLIC));
            case GraphMetadataTarget.ResourceTarget resource -> RuntimeMetadataTargets.resolve(registry,
                    level.getServer(), resource.reference());
            case GraphMetadataTarget.FactionTarget faction -> registry.resolve(FactionMetadataTargets.KIND,
                    FactionMetadataTargets.target(level.getServer(), faction.id()));
        };
    }
}
