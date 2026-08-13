package com.mine.geometry_node.core.engine.system.model.domain;

import java.util.List;
import java.util.Optional;

public record ModelScene(String name, List<Integer> rootNodes, Optional<ModelBounds> bounds) {
    public ModelScene {
        name = name == null ? "" : name;
        rootNodes = rootNodes == null ? List.of() : List.copyOf(rootNodes);
        bounds = bounds == null ? Optional.empty() : bounds;
        if (rootNodes.stream().anyMatch(index -> index == null || index < 0)) throw new IllegalArgumentException("root node indices must not be negative");
    }
}
