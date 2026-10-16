package com.mine.geometry_node.client.runtime.game;

import org.joml.Matrix4f;

/** Projection math shared by camera rendering and visibility culling.
 * Uses a right-handed view space (forward is negative Z), with the depth
 * convention supplied by the rendering backend. No world or GL context is required.
 */
public final class EditViewportProjection {
    private EditViewportProjection() {}

    public static Matrix4f set(Matrix4f destination, EditViewportCamera.Snapshot camera,
                               float aspect, float near, float far, boolean zeroToOne) {
        if (!Float.isFinite(aspect) || aspect <= 0.0F
                || !Float.isFinite(near) || near <= 0.0F
                || !Float.isFinite(far) || far <= near) {
            throw new IllegalArgumentException("Projection requires a positive aspect and 0 < near < far");
        }
        if (camera.orthographic()) {
            float halfHeight = (float) camera.orthographicHeight() * 0.5F;
            // Parallel rays cover the entire image plane, including points behind
            // the orbit eye. Keeping the perspective near plane slices the lower
            // part of a tilted ground plane into a straight, empty band. Center
            // the depth interval on the pivot instead; a signed near distance is
            // valid for an orthographic projection. The same matrix drives culling.
            float pivotDepth = (float) camera.distance();
            return destination.setOrtho(-halfHeight * aspect, halfHeight * aspect,
                    -halfHeight, halfHeight, pivotDepth - far, pivotDepth + far, zeroToOne);
        }
        return destination.setPerspective((float) Math.toRadians(camera.verticalFovDegrees()),
                aspect, near, far, zeroToOne);
    }
}
