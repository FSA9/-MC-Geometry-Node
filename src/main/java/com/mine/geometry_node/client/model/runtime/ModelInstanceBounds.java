package com.mine.geometry_node.client.model.runtime;

import com.mine.geometry_node.core.engine.system.model.domain.*;

public final class ModelInstanceBounds {
    private ModelInstanceBounds() {}

    public static ModelWorldBounds transform(ModelBounds bounds, double worldX, double worldY, double worldZ,
                                             float qx, float qy, float qz, float qw,
                                             float scaleX, float scaleY, float scaleZ) {
        ModelVector3 min = bounds.min(), max = bounds.max();
        double minX = Double.POSITIVE_INFINITY, minY = minX, minZ = minX;
        double maxX = Double.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;
        for (int corner = 0; corner < 8; corner++) {
            double x = ((corner & 1) == 0 ? min.x() : max.x()) * scaleX;
            double y = ((corner & 2) == 0 ? min.y() : max.y()) * scaleY;
            double z = ((corner & 4) == 0 ? min.z() : max.z()) * scaleZ;
            double tx = 2.0 * (qy * z - qz * y);
            double ty = 2.0 * (qz * x - qx * z);
            double tz = 2.0 * (qx * y - qy * x);
            double rotatedX = x + qw * tx + (qy * tz - qz * ty);
            double rotatedY = y + qw * ty + (qz * tx - qx * tz);
            double rotatedZ = z + qw * tz + (qx * ty - qy * tx);
            double px = worldX + rotatedX, py = worldY + rotatedY, pz = worldZ + rotatedZ;
            minX = java.lang.Math.min(minX, px); minY = java.lang.Math.min(minY, py); minZ = java.lang.Math.min(minZ, pz);
            maxX = java.lang.Math.max(maxX, px); maxY = java.lang.Math.max(maxY, py); maxZ = java.lang.Math.max(maxZ, pz);
        }
        return new ModelWorldBounds(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
