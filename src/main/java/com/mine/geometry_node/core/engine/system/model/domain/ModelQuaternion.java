package com.mine.geometry_node.core.engine.system.model.domain;

public record ModelQuaternion(float x, float y, float z, float w) {
    public static final ModelQuaternion IDENTITY = new ModelQuaternion(0.0F, 0.0F, 0.0F, 1.0F);

    public ModelQuaternion {
        ModelNumbers.finite(x, "x");
        ModelNumbers.finite(y, "y");
        ModelNumbers.finite(z, "z");
        ModelNumbers.finite(w, "w");
        double lengthSquared = (double) x * x + (double) y * y + (double) z * z + (double) w * w;
        if (lengthSquared < 1.0E-12D) throw new IllegalArgumentException("quaternion must not be zero length");
    }
}
