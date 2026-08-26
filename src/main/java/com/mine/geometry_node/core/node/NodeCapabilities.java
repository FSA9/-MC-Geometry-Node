package com.mine.geometry_node.core.node;

import com.mine.geometry_node.core.engine.graph.GraphType;
import com.mine.geometry_node.core.engine.graph.GraphTypeRegistry;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Execution-relevant node metadata. Defaults preserve the current blueprint
 * and quest behavior; behavior-tree support must always be declared explicitly.
 */
public record NodeCapabilities(Set<String> graphTypeIds, Purity purity, Context context,
                               Lifecycle lifecycle, Cancellation cancellation,
                               ChildConstraint children, Set<ResourceUse> resources,
                               Cost cost, Set<Permission> permissions) {
    public static final NodeCapabilities LEGACY_BLUEPRINT = new NodeCapabilities(
            Set.of(GraphTypeRegistry.BLUEPRINT.id(), GraphTypeRegistry.QUEST.id()),
            Purity.UNSPECIFIED, Context.BLUEPRINT_EXECUTION, Lifecycle.INSTANT,
            Cancellation.NOT_APPLICABLE, ChildConstraint.LEAF, Set.of(ResourceUse.NONE),
            Cost.NORMAL, Set.of(Permission.UNSPECIFIED));

    /**
     * Compatibility constructor for registrations written before structural and
     * permission capabilities became part of the node contract.
     */
    public NodeCapabilities(Set<String> graphTypeIds, Purity purity, Context context,
                            Lifecycle lifecycle, Cancellation cancellation,
                            ResourceUse resourceUse, Cost cost) {
        this(graphTypeIds, purity, context, lifecycle, cancellation,
                ChildConstraint.LEAF, Set.of(Objects.requireNonNull(resourceUse, "resourceUse")),
                cost, Set.of(Permission.UNSPECIFIED));
    }

    public NodeCapabilities(Set<String> graphTypeIds, Purity purity, Context context,
                            Lifecycle lifecycle, Cancellation cancellation,
                            ChildConstraint children, Set<ResourceUse> resources,
                            Cost cost, Permission permission) {
        this(graphTypeIds, purity, context, lifecycle, cancellation, children,
                resources, cost, Set.of(Objects.requireNonNull(permission, "permission")));
    }

    public NodeCapabilities {
        Set<String> normalizedTypes = new LinkedHashSet<>();
        for (String graphTypeId : Objects.requireNonNull(graphTypeIds, "graphTypeIds")) {
            String normalized = GraphType.normalizeId(graphTypeId);
            if (normalized.isEmpty()) throw new IllegalArgumentException("Node graph type cannot be empty");
            normalizedTypes.add(normalized);
        }
        graphTypeIds = Set.copyOf(normalizedTypes);
        if (graphTypeIds.isEmpty()) throw new IllegalArgumentException("Node graph types cannot be empty");
        purity = Objects.requireNonNull(purity, "purity");
        context = Objects.requireNonNull(context, "context");
        lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        cancellation = Objects.requireNonNull(cancellation, "cancellation");
        children = Objects.requireNonNull(children, "children");
        resources = Set.copyOf(Objects.requireNonNull(resources, "resources"));
        if (resources.isEmpty()) resources = Set.of(ResourceUse.NONE);
        if (resources.size() > 1 && resources.contains(ResourceUse.NONE)) {
            throw new IllegalArgumentException("ResourceUse.NONE cannot be combined with owned resources");
        }
        cost = Objects.requireNonNull(cost, "cost");
        permissions = Set.copyOf(Objects.requireNonNull(permissions, "permissions"));
        if (permissions.isEmpty()) permissions = Set.of(Permission.NONE);
        if (permissions.size() > 1 && (permissions.contains(Permission.NONE)
                || permissions.contains(Permission.UNSPECIFIED))) {
            throw new IllegalArgumentException("NONE/UNSPECIFIED cannot be combined with explicit permissions");
        }
    }

    public boolean supports(String graphTypeId) {
        return graphTypeIds.contains(GraphType.normalizeId(graphTypeId));
    }

    /** Compatibility view for callers that only understand one resource. */
    @Deprecated
    public ResourceUse resourceUse() {
        return resources.size() == 1 ? resources.iterator().next() : ResourceUse.CUSTOM;
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

    public enum Purity { PURE, READ_ONLY, SIDE_EFFECTING, UNSPECIFIED }
    public enum Context { DATA, BLUEPRINT_EXECUTION, BEHAVIOR_EXECUTION }
    public enum Lifecycle { INSTANT, SUSPENDING, CONTINUOUS }
    public enum Cancellation { NOT_APPLICABLE, CANCELLABLE, NON_CANCELLABLE }
    public enum ResourceUse { NONE, MOVEMENT, LOOK, TARGET, COMBAT, INTERACTION, ANIMATION, ITEM_USE, CUSTOM }
    public enum Cost { TRIVIAL, NORMAL, EXPENSIVE }
    public enum Permission {
        NONE, READ_OWNER, READ_WORLD, WRITE_BLACKBOARD,
        MUTATE_OWNER, MUTATE_WORLD, PRIVILEGED, UNSPECIFIED
    }
}
