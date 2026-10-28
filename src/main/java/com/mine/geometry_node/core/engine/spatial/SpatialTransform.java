package com.mine.geometry_node.core.engine.spatial;

import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.Objects;

/**
 * Shared placement convention, in double precision:
 * A + W + (Rw * Ra) * (L + P + Rl * (S * (point - P))).
 * World offsets never rotate; local offsets orbit the placement origin and local rotations
 * never move that offset. Angles use Minecraft's negative-yaw YXZ convention, in degrees.
 */
public record SpatialTransform(Vec3 worldOffset, Vec3 worldRotation, Vec3 localOffset,
                               Vec3 localRotation, Vec3 pivot, Vec3 scale) {
    public static final SpatialTransform IDENTITY = new SpatialTransform(Vec3.ZERO, Vec3.ZERO,
            Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, new Vec3(1, 1, 1));

    public SpatialTransform(Vec3 worldOffset, Vec3 worldRotation, Vec3 localOffset, Vec3 localRotation) {
        this(worldOffset, worldRotation, localOffset, localRotation, Vec3.ZERO, new Vec3(1, 1, 1));
    }

    public SpatialTransform {
        finite(worldOffset, "worldOffset");
        finite(worldRotation, "worldRotation");
        finite(localOffset, "localOffset");
        finite(localRotation, "localRotation");
        finite(pivot, "pivot");
        finite(scale, "scale");
    }

    public static Quaterniond rotation(Vec3 degrees) {
        finite(degrees, "rotation");
        return new Quaterniond().rotationYXZ(Math.toRadians(-degrees.y % 360),
                Math.toRadians(degrees.x % 360), Math.toRadians(degrees.z % 360));
    }

    public static Quaterniond outerRotation(Vec3 worldRotation, Quaterniond anchorRotation) {
        return rotation(worldRotation).mul(Objects.requireNonNull(anchorRotation, "anchorRotation"));
    }

    public static Vec3 rotate(Quaterniond rotation, Vec3 value) {
        Vector3d transformed = Objects.requireNonNull(rotation, "rotation")
                .transform(new Vector3d(value.x, value.y, value.z));
        return new Vec3(transformed.x, transformed.y, transformed.z);
    }

    public static Vec3 eulerDegrees(Quaterniond rotation) {
        Vector3d radians = new Quaterniond(rotation).normalize().getEulerAnglesYXZ(new Vector3d());
        return new Vec3(Math.toDegrees(radians.x), -Math.toDegrees(radians.y), Math.toDegrees(radians.z));
    }

    public static Vec3 position(Vec3 anchorPosition, Vec3 worldOffset, Vec3 worldRotation,
                                Quaterniond anchorRotation, Vec3 localOffset) {
        return anchorPosition.add(worldOffset).add(rotate(outerRotation(worldRotation, anchorRotation), localOffset));
    }

    public Vec3 position(Vec3 anchorPosition, Quaterniond anchorRotation) {
        return position(anchorPosition, worldOffset, worldRotation, anchorRotation, localOffset);
    }

    public Quaterniond orientation(Quaterniond anchorRotation) {
        return outerRotation(worldRotation, anchorRotation).mul(rotation(localRotation));
    }

    public Vec3 apply(Vec3 point, Vec3 anchorPosition, Quaterniond anchorRotation) {
        Vec3 scaled = point.subtract(pivot).multiply(scale);
        Vec3 local = rotate(rotation(localRotation), scaled).add(pivot).add(localOffset);
        return anchorPosition.add(worldOffset).add(rotate(outerRotation(worldRotation, anchorRotation), local));
    }

    private static void finite(Vec3 value, String name) {
        Objects.requireNonNull(value, name);
        if (!Double.isFinite(value.x) || !Double.isFinite(value.y) || !Double.isFinite(value.z))
            throw new IllegalArgumentException("Nonfinite " + name);
    }
}
