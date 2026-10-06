package com.mine.geometry_node.client.model.render.shadow;

import com.mine.geometry_node.client.model.render.geometry.ModelCullingBounds;
import com.mine.geometry_node.core.engine.system.model.asset.GltfAsset;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

/** Full-containment shortcut only; uncertain bounds keep the normal cluster tests. */
public final class ModelShadowCulling {
    private final FrustumIntersection frustum;
    private final ModelCullingBounds transformed = new ModelCullingBounds();
    private final double cameraX, cameraY, cameraZ;

    public ModelShadowCulling(Matrix4fc view, Matrix4fc projection,
                              double cameraX, double cameraY, double cameraZ) {
        frustum = new FrustumIntersection(new Matrix4f(projection).mul(view));
        this.cameraX = cameraX; this.cameraY = cameraY; this.cameraZ = cameraZ;
    }

    public boolean fullyInside(GltfAsset.Bounds bounds, Matrix4fc transform, double maxDistance) {
        if (bounds == null || bounds.isEmpty()) return false;
        transformed.set(bounds, transform, cameraX, cameraY, cameraZ, true);
        // Match Minecraft's absolute-AABB-to-camera conversion, including distant worlds.
        float minX = (float) (transformed.minX() - cameraX), minY = (float) (transformed.minY() - cameraY);
        float minZ = (float) (transformed.minZ() - cameraZ), maxX = (float) (transformed.maxX() - cameraX);
        float maxY = (float) (transformed.maxY() - cameraY), maxZ = (float) (transformed.maxZ() - cameraZ);
        float extent = Math.max(Math.max(Math.abs(minX), Math.abs(maxX)),
                Math.max(Math.max(Math.abs(minY), Math.abs(maxY)), Math.max(Math.abs(minZ), Math.abs(maxZ))));
        if (!Float.isFinite(extent)) return false;
        float margin = Math.max(0.00001f, 8 * Math.ulp(extent));
        minX -= margin; minY -= margin; minZ -= margin;
        maxX += margin; maxY += margin; maxZ += margin;
        if (maxDistance >= 0) {
            double x = Math.max(Math.abs(minX), Math.abs(maxX));
            double y = Math.max(Math.abs(minY), Math.abs(maxY));
            double z = Math.max(Math.abs(minZ), Math.abs(maxZ));
            // Stronger than the ordinary sphere-intersection test: every child is in range.
            if (x * x + y * y + z * z >= maxDistance * maxDistance) return false;
        }
        return frustum.intersectAab(minX, minY, minZ, maxX, maxY, maxZ) == FrustumIntersection.INSIDE;
    }
}
