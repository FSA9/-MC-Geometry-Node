package com.mine.geometry_node.client.model.render.geometry;

import com.mine.geometry_node.core.engine.system.model.asset.GltfAsset;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

/** Render-thread scratch for the distance sphere and absolute frustum AABB of one primitive. */
public final class ModelCullingBounds {
    private final Vector3f point = new Vector3f();
    private float centerDistanceSquared, radiusSquared;
    private double minX, minY, minZ, maxX, maxY, maxZ;

    public void set(GltfAsset.Bounds bounds, Matrix4fc transform,
                    double cameraX, double cameraY, double cameraZ, boolean includeFrustumBounds) {
        transform.transformPosition((bounds.minX() + bounds.maxX()) * .5f,
                (bounds.minY() + bounds.maxY()) * .5f, (bounds.minZ() + bounds.maxZ()) * .5f, point);
        float centerX = point.x, centerY = point.y, centerZ = point.z;
        centerDistanceSquared = point.lengthSquared();
        radiusSquared = 0;
        minX = minY = minZ = Double.POSITIVE_INFINITY;
        maxX = maxY = maxZ = Double.NEGATIVE_INFINITY;
        for (int ix = 0; ix < 2; ix++) for (int iy = 0; iy < 2; iy++) for (int iz = 0; iz < 2; iz++) {
            transform.transformPosition(ix == 0 ? bounds.minX() : bounds.maxX(),
                    iy == 0 ? bounds.minY() : bounds.maxY(), iz == 0 ? bounds.minZ() : bounds.maxZ(), point);
            radiusSquared = Math.max(radiusSquared, point.distanceSquared(centerX, centerY, centerZ));
            if (includeFrustumBounds) {
                minX = Math.min(minX, cameraX + point.x);
                minY = Math.min(minY, cameraY + point.y);
                minZ = Math.min(minZ, cameraZ + point.z);
                maxX = Math.max(maxX, cameraX + point.x);
                maxY = Math.max(maxY, cameraY + point.y);
                maxZ = Math.max(maxZ, cameraZ + point.z);
            }
        }
    }

    public float centerDistanceSquared() { return centerDistanceSquared; }
    public float radiusSquared() { return radiusSquared; }
    public double minX() { return minX; }
    public double minY() { return minY; }
    public double minZ() { return minZ; }
    public double maxX() { return maxX; }
    public double maxY() { return maxY; }
    public double maxZ() { return maxZ; }
}
