package com.mine.geometry_node.client.model.runtime;

import org.joml.Matrix4fc;

/** Numeric guards shared by placement validation and draw-time normal transforms. */
public final class ModelTransformMath {
    public static final float MIN_AXIS_LENGTH = 1.0E-6F;
    public static final double MIN_NORMALIZED_DETERMINANT = 1.0E-6D;

    private ModelTransformMath() {}

    public static boolean isRenderable(Matrix4fc transform) {
        double determinant = normalizedDeterminant(transform);
        return Double.isFinite(determinant) && Math.abs(determinant) > MIN_NORMALIZED_DETERMINANT;
    }

    /**
     * Returns the orientation determinant after removing axis magnitudes, so large or small
     * non-singular uniform scales do not change the singularity decision.
     */
    public static double normalizedDeterminant(Matrix4fc matrix) {
        double m00 = matrix.m00(), m01 = matrix.m01(), m02 = matrix.m02();
        double m10 = matrix.m10(), m11 = matrix.m11(), m12 = matrix.m12();
        double m20 = matrix.m20(), m21 = matrix.m21(), m22 = matrix.m22();
        double firstLength = length(m00, m01, m02);
        double secondLength = length(m10, m11, m12);
        double thirdLength = length(m20, m21, m22);
        double magnitude = firstLength * secondLength * thirdLength;
        if (!Double.isFinite(magnitude) || magnitude <= 0.0D) return Double.NaN;
        double determinant = m00 * (m11 * m22 - m12 * m21)
                - m10 * (m01 * m22 - m02 * m21)
                + m20 * (m01 * m12 - m02 * m11);
        return determinant / magnitude;
    }

    private static double length(double x, double y, double z) {
        return Math.sqrt(x * x + y * y + z * z);
    }
}
