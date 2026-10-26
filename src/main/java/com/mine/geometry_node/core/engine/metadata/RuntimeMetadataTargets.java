package com.mine.geometry_node.core.engine.metadata;

import com.mine.geometry_node.core.engine.blueprint.spatial.area.*;
import com.mine.geometry_node.core.engine.blueprint.spatial.forceField.*;
import com.mine.geometry_node.core.engine.graph.debug.DebugRendererSessionManager;
import com.mine.geometry_node.core.engine.graph.light.GraphLightService;
import com.mine.geometry_node.core.engine.graph.collision.GeometryCollisionService;
import com.mine.geometry_node.core.engine.graph.resource.*;
import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateNamespace;
import com.mine.geometry_node.core.engine.system.model.collision.runtime.ModelCollisionScene;
import com.mine.geometry_node.core.engine.system.marker.model.MarkerAddress;
import com.mine.geometry_node.core.engine.system.schematic.projection.SchematicProjectionService;
import com.mine.geometry_node.core.metadata.MetadataCapabilities;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/** Explicit built-in registrations; the application bootstrap chooses the registry and order. */
public final class RuntimeMetadataTargets {
    public static final String MODEL_COLLISION = "geometry_node:model_collision";
    private static final Set<MetadataCapabilities.Capability> BOTH = Set.of(
            MetadataCapabilities.Capability.TAG, MetadataCapabilities.Capability.DATA);

    public record GraphTarget(MinecraftServer server, GraphResourceId id) {
        public GraphTarget { Objects.requireNonNull(server); Objects.requireNonNull(id); }
        public ServerLevel level() { return requireLevel(server, id.scope().dimension()); }
    }
    public record AreaTarget(MinecraftServer server, AreaRef reference) {
        public AreaTarget { Objects.requireNonNull(server); Objects.requireNonNull(reference); }
    }
    public record ForceFieldTarget(MinecraftServer server, ForceFieldAddress address) {
        public ForceFieldTarget { Objects.requireNonNull(server); Objects.requireNonNull(address); }
    }
    public record ModelCollisionTarget(ServerLevel level, Object instanceId) {
        public ModelCollisionTarget {
            Objects.requireNonNull(level);
            if (!(instanceId instanceof String) && !(instanceId instanceof UUID))
                throw new IllegalArgumentException("Model collision identity must be a String or UUID");
        }
    }

    private static final Map<GraphResourceType, Function<GraphTarget, Object>> PUBLIC_GRAPH_TARGETS = Map.of(
            GraphResourceTypeRegistry.AREA, target -> {
                AreaAddress address = new AreaAddress(target.id().scope().dimension(), namedKey(target.id()));
                AreaResource area = AreaResourceStore.INSTANCE.get(target.server(), address);
                if (area == null || !target.id().equals(area.owner()))
                    throw new IllegalStateException("Graph area no longer exists");
                return new AreaTarget(target.server(), area.reference());
            },
            GraphResourceTypeRegistry.FORCE_FIELD, target -> {
                ForceFieldAddress address = new ForceFieldAddress(target.id().scope().dimension(), namedKey(target.id()));
                ForceFieldResource field = ForceFieldResourceStore.INSTANCE.get(target.server(), address);
                if (field == null || !target.id().equals(field.owner()))
                    throw new IllegalStateException("Graph force field no longer exists");
                return new ForceFieldTarget(target.server(), address);
            });

    private RuntimeMetadataTargets() {}

    public static void register(MetadataCapabilities registry) {
        registry.register(GraphResourceTypeRegistry.GEOMETRY_DEBUG.id().toString(), GraphTarget.class, BOTH,
                t -> DebugRendererSessionManager.metadata(t.level(), t.id()));
        registry.register(GraphResourceTypeRegistry.GEOMETRY_COLLISION.id().toString(), GraphTarget.class, BOTH,
                t -> GeometryCollisionService.INSTANCE.metadata(t.level(), t.id()));
        registry.register(GraphResourceTypeRegistry.LIGHT.id().toString(), GraphTarget.class, BOTH,
                t -> GraphLightService.INSTANCE.metadata(t.level(), t.id()));
        registry.register(GraphResourceTypeRegistry.SCHEMATIC_PROJECTION.id().toString(), GraphTarget.class, BOTH,
                t -> SchematicProjectionService.INSTANCE.metadata(t.server(), t.id()));
        registry.register(GraphResourceTypeRegistry.AREA.id().toString(), AreaTarget.class, BOTH,
                t -> area(registry, t));
        registry.register(GraphResourceTypeRegistry.FORCE_FIELD.id().toString(), ForceFieldTarget.class, BOTH,
                t -> ForceFieldResourceStore.INSTANCE.metadata(t.server(), t.address()));
        registry.register(MODEL_COLLISION, ModelCollisionTarget.class, BOTH,
                t -> ModelCollisionScene.metadata(t.level(), t.instanceId()));
    }

    private static MetadataCapabilities.Access area(MetadataCapabilities registry, AreaTarget target) {
        requireThread(target.server());
        AreaResource area = AreaResourceStore.INSTANCE.get(target.server(), target.reference());
        if (area == null) throw new IllegalStateException("Area metadata handle is stale");
        if (area.isAuthored())
            return registry.resolve(SceneMetadataTargets.KIND, SceneMetadataTargets.target(target.server(),
                    target.reference().address().dimension().identifier().toString(), area.authoredObjectId()));
        return new MetadataCapabilities.Access(AreaResourceStore.INSTANCE.metadataTags(target.server(), target.reference()),
                ScopedMetadataTargets.area(requireLevel(target.server(), target.reference().address().dimension()),
                        target.reference(), ScopedStateNamespace.PUBLIC));
    }

    /**
     * Shared node-facing resource entry point. Extension graph resource types register an adapter under
     * their resource type ID with {@link GraphTarget}; no node-specific type switch is required.
     */
    public static MetadataCapabilities.Access resolve(MinecraftServer server, GraphResourceReferences.Reference reference) {
        return resolve(MetadataCapabilities.GLOBAL, server, reference);
    }

    public static MetadataCapabilities.Access resolve(MetadataCapabilities registry, MinecraftServer server,
                                                       GraphResourceReferences.Reference reference) {
        requireThread(server);
        Objects.requireNonNull(reference);
        return switch (reference) {
            case GraphResourceReferences.GraphReference graph -> {
                GraphTarget target = new GraphTarget(server, graph.id());
                Function<GraphTarget, Object> publicTarget = PUBLIC_GRAPH_TARGETS.get(graph.id().type());
                yield registry.resolve(graph.id().type().id().toString(),
                        publicTarget == null ? target : publicTarget.apply(target));
            }
            case GraphResourceReferences.PublicReference address -> switch (address.kind()) {
                case AREA -> {
                    AreaResource area = AreaResourceStore.INSTANCE.get(server, new AreaAddress(address.dimension(), address.key()));
                    if (area == null) throw new IllegalStateException("Area no longer exists");
                    yield registry.resolve(GraphResourceTypeRegistry.AREA.id().toString(), new AreaTarget(server, area.reference()));
                }
                case FORCE_FIELD -> registry.resolve(GraphResourceTypeRegistry.FORCE_FIELD.id().toString(),
                        new ForceFieldTarget(server, new ForceFieldAddress(address.dimension(), address.key())));
                case SCHEMATIC_PLACEMENT -> registry.resolve(PlacementMetadataTargets.KIND,
                        PlacementMetadataTargets.target(requireLevel(server, address.dimension()), address.key()));
                case MARKER -> registry.resolve(MarkerMetadataTargets.KIND, MarkerMetadataTargets.target(server,
                        address.viewerId() == null ? MarkerAddress.all(address.key())
                                : MarkerAddress.self(address.viewerId(), address.key())));
                case MODEL_COLLISION -> registry.resolve(MODEL_COLLISION,
                        new ModelCollisionTarget(requireLevel(server, address.dimension()),
                                GraphResourceReferences.modelCollisionIdentity(address)));
                case SCENE_OBJECT -> registry.resolve(SceneMetadataTargets.KIND,
                        SceneMetadataTargets.target(server, address.dimension().identifier().toString(), UUID.fromString(address.key())));
            };
        };
    }

    public static void requireThread(MinecraftServer server) {
        Objects.requireNonNull(server);
        if (!server.isSameThread() || server.isStopped())
            throw new IllegalStateException("Runtime metadata requires the live server thread");
    }

    private static ServerLevel requireLevel(MinecraftServer server, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension) {
        requireThread(server);
        ServerLevel level = server.getLevel(dimension);
        if (level == null) throw new IllegalStateException("Resource dimension is not loaded");
        return level;
    }

    private static String namedKey(GraphResourceId id) {
        if (id.selector() instanceof GraphResourceSelector.Named named) return named.key();
        throw new IllegalArgumentException("Public resource requires a named selector");
    }
}
