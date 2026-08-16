package com.mine.geometry_node.client.model.runtime;

import com.mine.geometry_node.core.engine.system.model.domain.*;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

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

    public static ModelWorldBounds transform(ModelBounds bounds, Matrix4fc nodeWorld,
                                             double worldX, double worldY, double worldZ,
                                             Quaternionfc rotation, Vector3fc scale) {
        Matrix4f transform = new Matrix4f().rotate(rotation).scale(scale).mul(nodeWorld);
        Vector3f min = new Vector3f(Float.POSITIVE_INFINITY);
        Vector3f max = new Vector3f(Float.NEGATIVE_INFINITY);
        for (int corner = 0; corner < 8; corner++) {
            Vector3f point = transform.transformPosition(new Vector3f(
                    (float) ((corner & 1) == 0 ? bounds.min().x() : bounds.max().x()),
                    (float) ((corner & 2) == 0 ? bounds.min().y() : bounds.max().y()),
                    (float) ((corner & 4) == 0 ? bounds.min().z() : bounds.max().z())));
            min.min(point);
            max.max(point);
        }
        return new ModelWorldBounds(worldX + min.x, worldY + min.y, worldZ + min.z,
                worldX + max.x, worldY + max.y, worldZ + max.z);
    }
}
