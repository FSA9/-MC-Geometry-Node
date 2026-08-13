package com.mine.geometry_node.core.engine.system.model.domain;

import java.util.List;

public record ModelMesh(String name, List<ModelPrimitive> primitives, ModelBounds bounds) {
    public ModelMesh {
        name = name == null ? "" : name;
        primitives = primitives == null ? List.of() : List.copyOf(primitives);
        if (primitives.isEmpty()) throw new IllegalArgumentException("mesh must contain at least one primitive");
        if (bounds == null) throw new IllegalArgumentException("mesh bounds must not be null");
    }
}
