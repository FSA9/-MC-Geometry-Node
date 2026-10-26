package com.mine.geometry_node.client.runtime.model.runtime;

import com.mine.geometry_node.core.engine.system.model.asset.GltfAsset;
import com.mine.geometry_node.core.engine.system.model.math.ModelTransform;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4d;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/** Absolute bounds calculated without casting the world translation to float. */
final class ModelResidencyBounds {
    private ModelResidencyBounds() {}

    static AABB transform(GltfAsset.Bounds bounds, ModelTransform transform) {
        if (bounds.isEmpty()) return new AABB(transform.x(), transform.y(), transform.z(),
                transform.x(), transform.y(), transform.z());
        var matrix = new Matrix4d().translation(transform.x(), transform.y(), transform.z())
                .rotate(new Quaterniond(transform.qx(), transform.qy(), transform.qz(), transform.qw()))
                .scale(transform.sx(), transform.sy(), transform.sz());
        var point = new Vector3d();
        double minX = Double.POSITIVE_INFINITY, minY = minX, minZ = minX;
        double maxX = Double.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;
        for (int corner = 0; corner < 8; corner++) {
            matrix.transformPosition((corner & 1) == 0 ? bounds.minX() : bounds.maxX(),
                    (corner & 2) == 0 ? bounds.minY() : bounds.maxY(),
                    (corner & 4) == 0 ? bounds.minZ() : bounds.maxZ(), point);
            minX = Math.min(minX, point.x); minY = Math.min(minY, point.y); minZ = Math.min(minZ, point.z);
            maxX = Math.max(maxX, point.x); maxY = Math.max(maxY, point.y); maxZ = Math.max(maxZ, point.z);
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
