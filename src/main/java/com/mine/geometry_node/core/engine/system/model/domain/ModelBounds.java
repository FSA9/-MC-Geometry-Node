package com.mine.geometry_node.core.engine.system.model.domain;

public record ModelBounds(ModelVector3 min, ModelVector3 max) {
    public ModelBounds {
        if (min == null || max == null) throw new IllegalArgumentException("bounds must not be null");
        if (min.x() > max.x() || min.y() > max.y() || min.z() > max.z()) {
            throw new IllegalArgumentException("bounds min must not exceed max");
        }
    }
}
