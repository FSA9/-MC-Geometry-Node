package com.mine.geometry_node.core.node;

import com.mine.geometry_node.core.engine.graph.GraphType;
import com.mine.geometry_node.core.engine.graph.GraphTypeRegistry;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Node metadata consumed by graph compilation and behavior execution. */
public record NodeCapabilities(Set<String> graphTypeIds, Context context,
                               ChildConstraint children, Set<ResourceUse> resources) {
    public static final NodeCapabilities LEGACY_BLUEPRINT = new NodeCapabilities(
            Set.of(GraphTypeRegistry.BLUEPRINT.id(), GraphTypeRegistry.QUEST.id()),
            Context.BLUEPRINT_EXECUTION, ChildConstraint.LEAF, Set.of());

    public NodeCapabilities {
        Set<String> normalizedTypes = new LinkedHashSet<>();
        for (String graphTypeId : Objects.requireNonNull(graphTypeIds, "graphTypeIds")) {
            String normalized = GraphType.normalizeId(graphTypeId);
            if (normalized.isEmpty()) throw new IllegalArgumentException("Node graph type cannot be empty");
            normalizedTypes.add(normalized);
        }
        graphTypeIds = Set.copyOf(normalizedTypes);
        if (graphTypeIds.isEmpty()) throw new IllegalArgumentException("Node graph types cannot be empty");
        context = Objects.requireNonNull(context, "context");
        children = Objects.requireNonNull(children, "children");
        resources = Set.copyOf(Objects.requireNonNull(resources, "resources"));
    }

    public boolean supports(String graphTypeId) {
        return graphTypeIds.contains(GraphType.normalizeId(graphTypeId));
    }

    public record ChildConstraint(int maximum) {
        public static final int UNBOUNDED = -1;
        public static final ChildConstraint LEAF = new ChildConstraint(0);
        public static final ChildConstraint SINGLE_CHILD = new ChildConstraint(1);
        public static final ChildConstraint UNBOUNDED_CHILDREN = new ChildConstraint(UNBOUNDED);

        public ChildConstraint {
            if (maximum < UNBOUNDED) throw new IllegalArgumentException("maximum must be -1 or non-negative");
        }
    }

    public enum Context { DATA, BLUEPRINT_EXECUTION, BEHAVIOR_EXECUTION }
    public enum ResourceUse { MOVEMENT, LOOK, TARGET }
}
