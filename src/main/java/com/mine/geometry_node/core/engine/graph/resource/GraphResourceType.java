package com.mine.geometry_node.core.engine.graph.resource;

import net.minecraft.resources.Identifier;

import java.util.Objects;
import java.util.Set;

/** Declarative identity contract for one kind of graph-owned runtime resource. */
public record GraphResourceType(Identifier id,
                                GraphResourceLifetime lifetime,
                                Set<GraphResourceSelector.Kind> allowedSelectors,
                                TargetEntityPolicy targetEntityPolicy) {
    public GraphResourceType {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(lifetime, "lifetime");
        allowedSelectors = Set.copyOf(Objects.requireNonNull(allowedSelectors, "allowedSelectors"));
        if (allowedSelectors.isEmpty()) throw new IllegalArgumentException("allowedSelectors cannot be empty");
        Objects.requireNonNull(targetEntityPolicy, "targetEntityPolicy");
    }

    public enum TargetEntityPolicy { NONE, OPTIONAL, REQUIRED }
}
