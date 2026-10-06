package com.mine.geometry_node.client.model.render.geometry;

import com.mine.geometry_node.core.engine.system.model.asset.GltfAsset;
import org.joml.Matrix4fc;

/** Shared camera-to-whole-model AABB distance; callers pass a camera-relative transform. */
public final class ModelQualityDistance {
    public static double distance(GltfAsset.Bounds bounds, Matrix4fc transform) {
        if (bounds == null || bounds.isEmpty()) return 0;
        double minX = Double.POSITIVE_INFINITY, minY = minX, minZ = minX;
        double maxX = Double.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;
        for (int corner = 0; corner < 8; corner++) {
            double x = (corner & 1) == 0 ? bounds.minX() : bounds.maxX();
            double y = (corner & 2) == 0 ? bounds.minY() : bounds.maxY();
            double z = (corner & 4) == 0 ? bounds.minZ() : bounds.maxZ();
            double tx = transform.m00() * x + transform.m10() * y + transform.m20() * z + transform.m30();
            double ty = transform.m01() * x + transform.m11() * y + transform.m21() * z + transform.m31();
            double tz = transform.m02() * x + transform.m12() * y + transform.m22() * z + transform.m32();
            minX = Math.min(minX, tx); minY = Math.min(minY, ty); minZ = Math.min(minZ, tz);
            maxX = Math.max(maxX, tx); maxY = Math.max(maxY, ty); maxZ = Math.max(maxZ, tz);
        }
        double x = Math.max(minX, Math.max(0, -maxX));
        double y = Math.max(minY, Math.max(0, -maxY));
        double z = Math.max(minZ, Math.max(0, -maxZ));
        double distance = Math.sqrt(x * x + y * y + z * z);
        return Double.isFinite(distance) ? distance : 0;
    }

    private ModelQualityDistance() {}
}
