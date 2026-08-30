package com.mine.geometry_node.core.engine.graph.resource;

import com.mine.geometry_node.GeometryNode;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Registry of resource identity contracts. It never stores live business resources. */
public final class GraphResourceTypeRegistry {
    public static final GraphResourceType AREA = type("area",
            Set.of(GraphResourceSelector.Kind.NAMED), GraphResourceType.TargetEntityPolicy.NONE);
    public static final GraphResourceType AREA_STATE = type("area_state",
            Set.of(GraphResourceSelector.Kind.GRAPH), GraphResourceType.TargetEntityPolicy.NONE);
    public static final GraphResourceType AREA_QUERY = type("area_query",
            Set.of(GraphResourceSelector.Kind.NODE), GraphResourceType.TargetEntityPolicy.NONE);
    public static final GraphResourceType GEOMETRY_DEBUG = type("geometry_debug",
            Set.of(GraphResourceSelector.Kind.NODE, GraphResourceSelector.Kind.NAMED),
            GraphResourceType.TargetEntityPolicy.NONE);
    public static final GraphResourceType SCHEMATIC_PROJECTION = type("schematic_projection",
            Set.of(GraphResourceSelector.Kind.NODE, GraphResourceSelector.Kind.NAMED),
            GraphResourceType.TargetEntityPolicy.NONE);

    public static final GraphResourceTypeRegistry INSTANCE = new GraphResourceTypeRegistry();

    private final Map<Identifier, GraphResourceType> types = new LinkedHashMap<>();

    private GraphResourceTypeRegistry() {
        registerBuiltin(AREA);
        registerBuiltin(AREA_STATE);
        registerBuiltin(AREA_QUERY);
        registerBuiltin(GEOMETRY_DEBUG);
        registerBuiltin(SCHEMATIC_PROJECTION);
    }

    public synchronized GraphResourceType register(GraphResourceType type) {
        GraphResourceType previous = types.putIfAbsent(type.id(), type);
        if (previous != null) throw new IllegalArgumentException("Duplicate graph resource type: " + type.id());
        return type;
    }

    public synchronized GraphResourceType require(Identifier id) {
        GraphResourceType type = types.get(id);
        if (type == null) throw new IllegalArgumentException("Unknown graph resource type: " + id);
        return type;
    }

    public synchronized Map<Identifier, GraphResourceType> all() {
        return Map.copyOf(types);
    }

    private void registerBuiltin(GraphResourceType type) {
        types.put(type.id(), type);
    }

    private static GraphResourceType type(String path, Set<GraphResourceSelector.Kind> selectors,
                                          GraphResourceType.TargetEntityPolicy targetPolicy) {
        return new GraphResourceType(Identifier.fromNamespaceAndPath(GeometryNode.MODID, path),
                GraphResourceLifetime.BINDING, selectors, targetPolicy);
    }
}
