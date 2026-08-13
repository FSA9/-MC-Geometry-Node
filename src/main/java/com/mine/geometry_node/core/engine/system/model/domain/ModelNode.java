package com.mine.geometry_node.core.engine.system.model.domain;

import java.util.List;
import java.util.Optional;

public record ModelNode(String name, ModelTransform transform, int meshIndex, int skinIndex, List<Integer> children,
                        Optional<ModelBounds> bounds) {
    public ModelNode {
        name = name == null ? "" : name;
        if (transform == null) throw new IllegalArgumentException("node transform must not be null");
        if (meshIndex < -1) throw new IllegalArgumentException("meshIndex must be -1 or greater");
        if (skinIndex < -1) throw new IllegalArgumentException("skinIndex must be -1 or greater");
        children = children == null ? List.of() : List.copyOf(children);
        bounds = bounds == null ? Optional.empty() : bounds;
        if (children.stream().anyMatch(index -> index == null || index < 0)) throw new IllegalArgumentException("child indices must not be negative");
    }

    public ModelNode(String name, ModelTransform transform, int meshIndex, List<Integer> children,
                     Optional<ModelBounds> bounds) {
        this(name, transform, meshIndex, -1, children, bounds);
    }
}
