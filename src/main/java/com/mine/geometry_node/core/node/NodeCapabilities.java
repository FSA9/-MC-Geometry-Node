package com.mine.geometry_node.core.node;

import com.mine.geometry_node.core.engine.graph.GraphTypeRegistry;
import com.mine.geometry_node.core.engine.graph.GraphType;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Execution-relevant node metadata. Defaults preserve the current blueprint
 * and quest behavior; behavior-tree support must always be declared explicitly.
 */
public record NodeCapabilities(Set<String> graphTypeIds, Purity purity, Context context,
                               Lifecycle lifecycle, Cancellation cancellation,
                               ResourceUse resourceUse, Cost cost) {
    public static final NodeCapabilities LEGACY_BLUEPRINT = new NodeCapabilities(
            Set.of(GraphTypeRegistry.BLUEPRINT.id(), GraphTypeRegistry.QUEST.id()),
            Purity.UNSPECIFIED, Context.BLUEPRINT_EXECUTION, Lifecycle.INSTANT,
            Cancellation.NOT_APPLICABLE, ResourceUse.NONE, Cost.NORMAL);

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
        resourceUse = Objects.requireNonNull(resourceUse, "resourceUse");
        cost = Objects.requireNonNull(cost, "cost");
    }

    public boolean supports(String graphTypeId) {
        return graphTypeIds.contains(GraphType.normalizeId(graphTypeId));
    }

    public enum Purity { PURE, READ_ONLY, SIDE_EFFECTING, UNSPECIFIED }
    public enum Context { DATA, BLUEPRINT_EXECUTION, BEHAVIOR_EXECUTION }
    public enum Lifecycle { INSTANT, SUSPENDING, CONTINUOUS }
    public enum Cancellation { NOT_APPLICABLE, CANCELLABLE, NON_CANCELLABLE }
    public enum ResourceUse { NONE, MOVEMENT, LOOK, COMBAT, INTERACTION, CUSTOM }
    public enum Cost { TRIVIAL, NORMAL, EXPENSIVE }
}
