package com.mine.geometry_node.core.engine.system.model.domain;

public record ModelVector3(float x, float y, float z) {
    public static final ModelVector3 ZERO = new ModelVector3(0.0F, 0.0F, 0.0F);
    public static final ModelVector3 ONE = new ModelVector3(1.0F, 1.0F, 1.0F);

    public ModelVector3 {
        ModelNumbers.finite(x, "x");
        ModelNumbers.finite(y, "y");
        ModelNumbers.finite(z, "z");
    }
}
