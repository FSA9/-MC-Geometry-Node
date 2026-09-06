package com.mine.geometry_node.core.engine.graph.resource;

import com.mine.geometry_node.GeometryNode;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Registry of resource identity contracts. It never stores live business resources. */
public final class GraphResourceTypeRegistry {
    public static final GraphResourceType AREA = type("area",
            Set.of(GraphResourceSelector.Kind.NAMED), GraphResourceType.TargetEntityPolicy.OPTIONAL);
    public static final GraphResourceType AREA_STATE = type("area_state",
            Set.of(GraphResourceSelector.Kind.GRAPH), GraphResourceType.TargetEntityPolicy.NONE);
    public static final GraphResourceType AREA_QUERY = type("area_query",
            Set.of(GraphResourceSelector.Kind.NODE), GraphResourceType.TargetEntityPolicy.NONE);
    public static final GraphResourceType FORCE_FIELD = type("force_field",
            Set.of(GraphResourceSelector.Kind.NAMED), GraphResourceType.TargetEntityPolicy.NONE);
    public static final GraphResourceType GEOMETRY_DEBUG = type("geometry_debug",
            Set.of(GraphResourceSelector.Kind.NODE, GraphResourceSelector.Kind.NAMED),
            GraphResourceType.TargetEntityPolicy.NONE);
    public static final GraphResourceType SCHEMATIC_PROJECTION = type("schematic_projection",
            Set.of(GraphResourceSelector.Kind.NODE, GraphResourceSelector.Kind.NAMED),
            GraphResourceType.TargetEntityPolicy.NONE);

    public static final GraphResourceTypeRegistry INSTANCE = new GraphResourceTypeRegistry();

    private final Map<Identifier, GraphResourceType> registrations = new LinkedHashMap<>();
    private volatile Map<Identifier, GraphResourceType> snapshot = Map.of();

    private GraphResourceTypeRegistry() {
        registerBuiltin(AREA);
        registerBuiltin(AREA_STATE);
        registerBuiltin(AREA_QUERY);
        registerBuiltin(FORCE_FIELD);
        registerBuiltin(GEOMETRY_DEBUG);
        registerBuiltin(SCHEMATIC_PROJECTION);
        publishSnapshot();
    }

    /** Registers one contract and atomically publishes a new immutable read snapshot. */
    public synchronized GraphResourceType register(GraphResourceType type) {
        Objects.requireNonNull(type, "type");
        GraphResourceType previous = registrations.putIfAbsent(type.id(), type);
        if (previous != null) {
            throw new IllegalArgumentException("Duplicate graph resource type: " + type.id());
        }
        publishSnapshot();
        return type;
    }

    public GraphResourceType require(Identifier id) {
        GraphResourceType type = snapshot.get(Objects.requireNonNull(id, "id"));
        if (type == null) throw new IllegalArgumentException("Unknown graph resource type: " + id);
        return type;
    }

    public Map<Identifier, GraphResourceType> all() {
        return snapshot;
    }

    private void registerBuiltin(GraphResourceType type) {
        GraphResourceType previous = registrations.putIfAbsent(type.id(), type);
        if (previous != null) {
            throw new IllegalStateException("Duplicate built-in graph resource type: " + type.id());
        }
    }

    private void publishSnapshot() {
        snapshot = Map.copyOf(registrations);
    }

    private static GraphResourceType type(String path, Set<GraphResourceSelector.Kind> selectors,
                                          GraphResourceType.TargetEntityPolicy targetPolicy) {
        return new GraphResourceType(Identifier.fromNamespaceAndPath(GeometryNode.MODID, path),
                GraphResourceLifetime.BINDING, selectors, targetPolicy);
    }
}
