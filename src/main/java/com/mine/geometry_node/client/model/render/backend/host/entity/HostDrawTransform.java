package com.mine.geometry_node.client.model.render.backend.host.entity;

import com.mine.geometry_node.client.model.runtime.ModelInstancePlacement;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.Objects;
import java.util.Optional;

/** One authoritative instance + node transform resolution for a HOST draw. */
public final class HostDrawTransform {
    private static final float MIN_DETERMINANT = 1.0E-8F;

    private final Matrix4f baked;
    private final Matrix4f cameraRelative;
    private final Matrix3f normal;
    private final Vector3d worldCenter;
    private final boolean mirrored;

    private HostDrawTransform(Matrix4f baked, Matrix4f cameraRelative, Matrix3f normal,
                              Vector3d worldCenter, boolean mirrored) {
        this.baked = baked;
        this.cameraRelative = cameraRelative;
        this.normal = normal;
        this.worldCenter = worldCenter;
        this.mirrored = mirrored;
    }

    public static Optional<HostDrawTransform> resolve(ModelInstancePlacement placement, Matrix4fc nodeWorld,
                                                       Vector3fc localCenter,
                                                       double cameraX, double cameraY, double cameraZ) {
        Objects.requireNonNull(placement, "placement");
        Objects.requireNonNull(nodeWorld, "nodeWorld");
        Objects.requireNonNull(localCenter, "localCenter");
        Matrix4f baked = new Matrix4f().rotate(placement.rotation()).scale(placement.scale()).mul(nodeWorld);
        float determinant = baked.determinant3x3();
        if (!Float.isFinite(determinant) || Math.abs(determinant) <= MIN_DETERMINANT) return Optional.empty();
        Matrix3f normal;
        try {
            normal = new Matrix3f(baked).invert().transpose();
        } catch (RuntimeException failure) {
            return Optional.empty();
        }
        Matrix4f cameraRelative = new Matrix4f().translate(
                (float) (placement.position().x - cameraX),
                (float) (placement.position().y - cameraY),
                (float) (placement.position().z - cameraZ)).mul(baked);
        Vector3f modelCenter = baked.transformPosition(localCenter, new Vector3f());
        Vector3d worldCenter = new Vector3d(placement.position()).add(modelCenter.x, modelCenter.y, modelCenter.z);
        return Optional.of(new HostDrawTransform(baked, cameraRelative, normal, worldCenter, determinant < 0));
    }

    public Matrix4f baked() {
        return new Matrix4f(baked);
    }

    public Matrix4f cameraRelative() {
        return new Matrix4f(cameraRelative);
    }

    public Matrix3f normal() {
        return new Matrix3f(normal);
    }

    public Vector3d worldCenter() {
        return new Vector3d(worldCenter);
    }

    public boolean mirrored() {
        return mirrored;
    }
}
