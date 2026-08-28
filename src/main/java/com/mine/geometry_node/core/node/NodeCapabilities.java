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
            Context.BLUEPRINT_EXECUTION, ChildConstraint.LEAF, Set.of(ResourceUse.NONE));

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
        if (resources.isEmpty()) resources = Set.of(ResourceUse.NONE);
        if (resources.size() > 1 && resources.contains(ResourceUse.NONE)) {
            throw new IllegalArgumentException("ResourceUse.NONE cannot be combined with owned resources");
        }
    }

    public boolean supports(String graphTypeId) {
        return graphTypeIds.contains(GraphType.normalizeId(graphTypeId));
    }

    public record ChildConstraint(int minimum, int maximum, boolean ordered) {
        public static final int UNBOUNDED = -1;
        public static final ChildConstraint LEAF = exactly(0);
        public static final ChildConstraint EXACTLY_ONE = exactly(1);
        public static final ChildConstraint ONE_OR_MORE_ORDERED = range(1, UNBOUNDED, true);

        public ChildConstraint {
            if (minimum < 0) throw new IllegalArgumentException("minimum must be non-negative");
            if (maximum < UNBOUNDED) throw new IllegalArgumentException("maximum must be -1 or non-negative");
            if (maximum != UNBOUNDED && maximum < minimum) {
                throw new IllegalArgumentException("maximum must be at least minimum");
            }
        }

        public static ChildConstraint exactly(int count) {
            return new ChildConstraint(count, count, false);
        }

        public static ChildConstraint range(int minimum, int maximum, boolean ordered) {
            return new ChildConstraint(minimum, maximum, ordered);
        }

        public boolean accepts(int count) {
            return count >= minimum && (maximum == UNBOUNDED || count <= maximum);
        }
    }

    public enum Context { DATA, BLUEPRINT_EXECUTION, BEHAVIOR_EXECUTION }
    public enum ResourceUse { NONE, MOVEMENT, LOOK, TARGET, COMBAT, INTERACTION, ANIMATION, ITEM_USE, CUSTOM }
}
