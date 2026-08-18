package com.mine.geometry_node.client.model.render.backend.host.entity;

import com.mine.geometry_node.client.model.render.backend.host.light.contract.HostLightBinding;
import org.joml.Matrix3f;
import org.joml.Matrix3fc;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.util.Objects;

/** Exact identity of vertex data baked for one static HOST geometry variant. */
public final class HostStaticVariantKey {
    private final Object instanceIdentity;
    private final long poseRevision;
    private final Matrix4f poseTransform;
    private final Matrix3f normalTransform;
    private final int packedOverlay;
    private final HostLightBinding.Identity lightIdentity;
    private final boolean mirrored;
    private final int redBits;
    private final int greenBits;
    private final int blueBits;
    private final int alphaBits;
    private final int firstTriangle;
    private final int triangleCount;
    private final Object layoutIdentity;
    private final long layoutGeneration;

    public HostStaticVariantKey(Object instanceIdentity, long poseRevision,
                                Matrix4fc poseTransform, Matrix3fc normalTransform,
                                int packedOverlay, int packedLight, boolean mirrored,
                                float red, float green, float blue, float alpha,
                                int firstTriangle, int triangleCount,
                                Object layoutIdentity, long layoutGeneration) {
        this(instanceIdentity, poseRevision, poseTransform, normalTransform, packedOverlay,
                HostLightBinding.constant(packedLight), mirrored, red, green, blue, alpha,
                firstTriangle, triangleCount, layoutIdentity, layoutGeneration);
    }

    public HostStaticVariantKey(Object instanceIdentity, long poseRevision,
                                Matrix4fc poseTransform, Matrix3fc normalTransform,
                                int packedOverlay, HostLightBinding lightBinding, boolean mirrored,
                                float red, float green, float blue, float alpha,
                                int firstTriangle, int triangleCount,
                                Object layoutIdentity, long layoutGeneration) {
        this.instanceIdentity = Objects.requireNonNull(instanceIdentity, "instanceIdentity");
        this.poseRevision = poseRevision;
        this.poseTransform = new Matrix4f(Objects.requireNonNull(poseTransform, "poseTransform"));
        this.normalTransform = new Matrix3f(Objects.requireNonNull(normalTransform, "normalTransform"));
        this.packedOverlay = packedOverlay;
        this.lightIdentity = Objects.requireNonNull(lightBinding, "lightBinding").identity();
        this.mirrored = mirrored;
        this.redBits = finiteBits(red, "red");
        this.greenBits = finiteBits(green, "green");
        this.blueBits = finiteBits(blue, "blue");
        this.alphaBits = finiteBits(alpha, "alpha");
        if (firstTriangle < 0 || triangleCount < 1) {
            throw new IllegalArgumentException("static triangle range must be non-empty");
        }
        this.firstTriangle = firstTriangle;
        this.triangleCount = triangleCount;
        this.layoutIdentity = Objects.requireNonNull(layoutIdentity, "layoutIdentity");
        this.layoutGeneration = layoutGeneration;
    }

    public Object instanceIdentity() { return instanceIdentity; }
    public long poseRevision() { return poseRevision; }
    public Matrix4f poseTransform() { return new Matrix4f(poseTransform); }
    public Matrix3f normalTransform() { return new Matrix3f(normalTransform); }
    public int packedOverlay() { return packedOverlay; }
    public int packedLight() {
        return switch (lightIdentity.mode()) {
            case CONSTANT, FULL_BRIGHT -> lightIdentity.constantPackedLight();
            case FIELD -> throw new IllegalStateException("FIELD light has no scalar packed-light value");
        };
    }
    public HostLightBinding.Identity lightIdentity() { return lightIdentity; }
    public HostStaticAdmissionKey admissionKey() { return new HostStaticAdmissionKey(this); }
    public boolean mirrored() { return mirrored; }
    public float red() { return Float.intBitsToFloat(redBits); }
    public float green() { return Float.intBitsToFloat(greenBits); }
    public float blue() { return Float.intBitsToFloat(blueBits); }
    public float alpha() { return Float.intBitsToFloat(alphaBits); }
    public int firstTriangle() { return firstTriangle; }
    public int triangleCount() { return triangleCount; }
    public Object layoutIdentity() { return layoutIdentity; }
    public long layoutGeneration() { return layoutGeneration; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof HostStaticVariantKey key)) return false;
        return instanceIdentity == key.instanceIdentity
                && poseRevision == key.poseRevision
                && poseTransform.equals(key.poseTransform)
                && normalTransform.equals(key.normalTransform)
                && packedOverlay == key.packedOverlay
                && lightIdentity.equals(key.lightIdentity)
                && mirrored == key.mirrored
                && redBits == key.redBits
                && greenBits == key.greenBits
                && blueBits == key.blueBits
                && alphaBits == key.alphaBits
                && firstTriangle == key.firstTriangle
                && triangleCount == key.triangleCount
                && layoutIdentity == key.layoutIdentity
                && layoutGeneration == key.layoutGeneration;
    }

    @Override public int hashCode() {
        int result = System.identityHashCode(instanceIdentity);
        result = 31 * result + Long.hashCode(poseRevision);
        result = 31 * result + poseTransform.hashCode();
        result = 31 * result + normalTransform.hashCode();
        result = 31 * result + packedOverlay;
        result = 31 * result + lightIdentity.hashCode();
        result = 31 * result + Boolean.hashCode(mirrored);
        result = 31 * result + redBits;
        result = 31 * result + greenBits;
        result = 31 * result + blueBits;
        result = 31 * result + alphaBits;
        result = 31 * result + firstTriangle;
        result = 31 * result + triangleCount;
        result = 31 * result + System.identityHashCode(layoutIdentity);
        return 31 * result + Long.hashCode(layoutGeneration);
    }

    private static int finiteBits(float value, String name) {
        if (!Float.isFinite(value)) throw new IllegalArgumentException(name + " must be finite");
        return Float.floatToIntBits(value);
    }
}
