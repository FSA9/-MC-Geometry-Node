package com.mine.geometry_node.client.model.runtime;

import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

public record ModelInstancePlacement(Vector3d position, Quaternionf rotation, Vector3f scale,
                                  boolean fullBright, boolean forceDoubleSided,
                                  float red, float green, float blue, float alpha) {
    public ModelInstancePlacement {
        if (position == null || rotation == null || scale == null) throw new IllegalArgumentException("placement transform must not be null");
        position = new Vector3d(position);
        if (!Double.isFinite(position.x) || !Double.isFinite(position.y) || !Double.isFinite(position.z)) {
            throw new IllegalArgumentException("placement position must be finite");
        }
        rotation = new Quaternionf(rotation);
        if (!finite(rotation.x) || !finite(rotation.y) || !finite(rotation.z) || !finite(rotation.w)
                || rotation.lengthSquared() <= 1.0E-12F) {
            throw new IllegalArgumentException("placement rotation must be finite and non-zero");
        }
        rotation.normalize();
        scale = new Vector3f(scale);
        if (!finite(scale.x) || !finite(scale.y) || !finite(scale.z) || scale.x == 0 || scale.y == 0 || scale.z == 0) {
            throw new IllegalArgumentException("placement scale must be finite and non-zero");
        }
        if (Math.abs(scale.x) < ModelTransformMath.MIN_AXIS_LENGTH
                || Math.abs(scale.y) < ModelTransformMath.MIN_AXIS_LENGTH
                || Math.abs(scale.z) < ModelTransformMath.MIN_AXIS_LENGTH) {
            throw new IllegalArgumentException("placement scale axis is below the supported numeric floor");
        }
        red = unit(red, "red"); green = unit(green, "green"); blue = unit(blue, "blue"); alpha = unit(alpha, "alpha");
    }

    public static ModelInstancePlacement at(double x, double y, double z) {
        return new ModelInstancePlacement(new Vector3d(x, y, z), new Quaternionf(), new Vector3f(1),
                false, false, 1, 1, 1, 1);
    }

    public static ModelInstancePlacement previewAt(double x, double y, double z) {
        return new ModelInstancePlacement(new Vector3d(x, y, z), new Quaternionf(), new Vector3f(1),
                true, true, 1, 1, 1, 1);
    }

    @Override public Vector3d position() { return new Vector3d(position); }
    @Override public Quaternionf rotation() { return new Quaternionf(rotation); }
    @Override public Vector3f scale() { return new Vector3f(scale); }

    private static boolean finite(float value) { return Float.isFinite(value); }
    private static float unit(float value, String name) {
        if (!finite(value) || value < 0 || value > 1) throw new IllegalArgumentException(name + " must be within [0, 1]");
        return value;
    }
}
