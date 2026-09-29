package com.mine.geometry_node.core.engine.system.model.math;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
/** Right-handed Y-up, metres (= blocks). World translation stays double until camera subtraction. */
public record ModelTransform(double x, double y, double z, float qx, float qy, float qz, float qw,
                             float sx, float sy, float sz) {
    public static final ModelTransform IDENTITY = new ModelTransform(0,0,0,0,0,0,1,1,1,1);
    public ModelTransform {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Float.isFinite(qx) || !Float.isFinite(qy) || !Float.isFinite(qz) || !Float.isFinite(qw)
                || !Float.isFinite(sx) || !Float.isFinite(sy) || !Float.isFinite(sz)
                || Math.abs(sx) < 1e-6f || Math.abs(sy) < 1e-6f || Math.abs(sz) < 1e-6f)
            throw new IllegalArgumentException("non-finite or singular model transform");
        double norm = Math.sqrt((double)qx*qx + (double)qy*qy + (double)qz*qz + (double)qw*qw);
        if (norm < 1e-12) throw new IllegalArgumentException("zero quaternion");
        qx /= norm; qy /= norm; qz /= norm; qw /= norm;
    }
    public Matrix4f relativeTo(double cx, double cy, double cz) {
        return new Matrix4f().translation((float)(x-cx), (float)(y-cy), (float)(z-cz))
                .rotate(new Quaternionf(qx,qy,qz,qw)).scale(sx,sy,sz);
    }
    public boolean mirrored() { return (sx < 0) ^ (sy < 0) ^ (sz < 0); }
}
