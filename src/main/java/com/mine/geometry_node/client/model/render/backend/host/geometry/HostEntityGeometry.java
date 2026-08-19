package com.mine.geometry_node.client.model.render.backend.host.geometry;

import com.mine.geometry_node.client.model.render.backend.host.light.contract.HostLightBinding;
import com.mine.geometry_node.client.model.render.backend.host.lod.HostModelLodPlan;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix3fc;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

import java.util.Arrays;

/** Immutable host entity vertex stream produced from one canonical primitive. */
public final class HostEntityGeometry {
    private final float[] vertices;
    private final HostSpatialClusterPlan clusters;
    private final HostModelLodPlan lod;

    HostEntityGeometry(float[] vertices) {
        this(vertices, null, 0);
    }

    HostEntityGeometry(float[] vertices, int[] canonicalIndices, int canonicalVertexCount) {
        this.vertices = Arrays.copyOf(vertices, vertices.length);
        this.clusters = HostSpatialClusterPlan.build(this.vertices);
        this.lod = canonicalIndices == null
                ? HostModelLodPlan.build(this.vertices)
                : HostModelLodPlan.build(this.vertices, canonicalIndices, canonicalVertexCount);
    }

    public long triangleCount() {
        return vertices.length / 36L;
    }

    public HostSpatialClusterPlan clusters() { return clusters; }
    public HostModelLodPlan lod() { return lod; }
    public int sourceTriangleCount() { return Math.toIntExact(triangleCount()); }
    public int staticTriangleCount() { return lod.staticTriangleCount(); }

    float[] staticVertexData() {
        return Arrays.copyOf(vertices, vertices.length);
    }

    public void emit(PoseStack.Pose pose, VertexConsumer out, float red, float green, float blue, float alpha,
                     int light, boolean mirrored) {
        emit(pose, out, red, green, blue, alpha, HostLightBinding.constant(light), mirrored);
    }

    public void emit(PoseStack.Pose pose, VertexConsumer out, float red, float green, float blue, float alpha,
                     HostLightBinding lightBinding, boolean mirrored) {
        emitRange(pose.pose(), pose.normal(), out, red, green, blue, alpha, lightBinding, mirrored,
                0, Math.toIntExact(triangleCount()));
    }

    public void emitRange(Matrix4fc pose, Matrix3fc normal, VertexConsumer out,
                          float red, float green, float blue, float alpha,
                          int light, boolean mirrored, int firstTriangle, int triangleCount) {
        emitRange(pose, normal, out, red, green, blue, alpha, light, mirrored,
                firstTriangle, triangleCount, false);
    }

    public void emitRange(Matrix4fc pose, Matrix3fc normal, VertexConsumer out,
                          float red, float green, float blue, float alpha,
                          HostLightBinding lightBinding, boolean mirrored,
                          int firstTriangle, int triangleCount) {
        emitRange(pose, normal, out, red, green, blue, alpha, lightBinding, mirrored,
                firstTriangle, triangleCount, false);
    }

    public void emitOrderedRange(Matrix4fc pose, Matrix3fc normal, VertexConsumer out,
                                 float red, float green, float blue, float alpha,
                                 int light, boolean mirrored, int firstTriangle, int triangleCount) {
        emitRange(pose, normal, out, red, green, blue, alpha, light, mirrored,
                firstTriangle, triangleCount, true);
    }

    public void emitOrderedRange(Matrix4fc pose, Matrix3fc normal, VertexConsumer out,
                                 float red, float green, float blue, float alpha,
                                 HostLightBinding lightBinding, boolean mirrored,
                                 int firstTriangle, int triangleCount) {
        emitRange(pose, normal, out, red, green, blue, alpha, lightBinding, mirrored,
                firstTriangle, triangleCount, true);
    }

    public void emitStaticRange(Matrix4fc pose, Matrix3fc normal, VertexConsumer out,
                                float red, float green, float blue, float alpha,
                                int light, boolean mirrored, int firstTriangle, int triangleCount) {
        emitStaticRange(pose, normal, out, red, green, blue, alpha,
                HostLightBinding.constant(light), mirrored, firstTriangle, triangleCount);
    }

    public void emitStaticRange(Matrix4fc pose, Matrix3fc normal, VertexConsumer out,
                                float red, float green, float blue, float alpha,
                                HostLightBinding lightBinding, boolean mirrored,
                                int firstTriangle, int triangleCount) {
        int available = staticTriangleCount();
        if (firstTriangle < 0 || triangleCount < 0 || firstTriangle > available - triangleCount) {
            throw new IndexOutOfBoundsException("HOST static triangle range is outside the geometry");
        }
        Vector3f transformedNormal = new Vector3f();
        if (lightBinding == null) throw new NullPointerException("lightBinding");
        int sourceTriangles = Math.toIntExact(this.triangleCount());
        for (int staticTriangle = firstTriangle; staticTriangle < firstTriangle + triangleCount; staticTriangle++) {
            float[] stream;
            int triangle;
            if (staticTriangle < sourceTriangles) {
                stream = vertices;
                triangle = clusters.sourceTriangle(staticTriangle);
            } else {
                stream = null;
                triangle = staticTriangle - sourceTriangles;
            }
            int first = triangle * 3;
            int second = mirrored ? first + 2 : first + 1;
            int third = mirrored ? first + 1 : first + 2;
            int firstOccurrence = stream == null
                    ? HostVertexOccurrence.proxy(sourceTriangles, triangle, 0, mirrored)
                    : HostVertexOccurrence.source(triangle, 0, mirrored);
            int secondOccurrence = stream == null
                    ? HostVertexOccurrence.proxy(sourceTriangles, triangle, 1, mirrored)
                    : HostVertexOccurrence.source(triangle, 1, mirrored);
            int thirdOccurrence = stream == null
                    ? HostVertexOccurrence.proxy(sourceTriangles, triangle, 2, mirrored)
                    : HostVertexOccurrence.source(triangle, 2, mirrored);
            int firstLight = lightBinding.packedLight(firstOccurrence);
            int secondLight = lightBinding.packedLight(secondOccurrence);
            int thirdLight = lightBinding.packedLight(thirdOccurrence);
            if (stream == null) {
                emitProxyVertex(pose, normal, transformedNormal, out, first, red, green, blue, alpha, firstLight);
                emitProxyVertex(pose, normal, transformedNormal, out, second, red, green, blue, alpha, secondLight);
                emitProxyVertex(pose, normal, transformedNormal, out, third, red, green, blue, alpha, thirdLight);
                emitProxyVertex(pose, normal, transformedNormal, out, third, red, green, blue, alpha, thirdLight);
            } else {
                emitVertex(stream, pose, normal, transformedNormal, out, first, red, green, blue, alpha, firstLight);
                emitVertex(stream, pose, normal, transformedNormal, out, second, red, green, blue, alpha, secondLight);
                emitVertex(stream, pose, normal, transformedNormal, out, third, red, green, blue, alpha, thirdLight);
                emitVertex(stream, pose, normal, transformedNormal, out, third, red, green, blue, alpha, thirdLight);
            }
        }
    }

    public void emitStaticTriangleRange(Matrix4fc pose, Matrix3fc normal, VertexConsumer out,
                                        float red, float green, float blue, float alpha,
                                        int light, boolean mirrored, int firstTriangle, int triangleCount) {
        emitStaticTriangleRange(pose, normal, out, red, green, blue, alpha,
                HostLightBinding.constant(light), mirrored, firstTriangle, triangleCount);
    }

    public void emitStaticTriangleRange(Matrix4fc pose, Matrix3fc normal, VertexConsumer out,
                                        float red, float green, float blue, float alpha,
                                        HostLightBinding lightBinding, boolean mirrored,
                                        int firstTriangle, int triangleCount) {
        int available = staticTriangleCount();
        if (firstTriangle < 0 || triangleCount < 0 || firstTriangle > available - triangleCount) {
            throw new IndexOutOfBoundsException("HOST static triangle range is outside the geometry");
        }
        Vector3f transformedNormal = new Vector3f();
        if (lightBinding == null) throw new NullPointerException("lightBinding");
        int sourceTriangles = Math.toIntExact(this.triangleCount());
        for (int staticTriangle = firstTriangle; staticTriangle < firstTriangle + triangleCount; staticTriangle++) {
            boolean source = staticTriangle < sourceTriangles;
            int triangle = source
                    ? clusters.sourceTriangle(staticTriangle)
                    : staticTriangle - sourceTriangles;
            int first = triangle * 3;
            int second = mirrored ? first + 2 : first + 1;
            int third = mirrored ? first + 1 : first + 2;
            int firstOccurrence = source
                    ? HostVertexOccurrence.source(triangle, 0, mirrored)
                    : HostVertexOccurrence.proxy(sourceTriangles, triangle, 0, mirrored);
            int secondOccurrence = source
                    ? HostVertexOccurrence.source(triangle, 1, mirrored)
                    : HostVertexOccurrence.proxy(sourceTriangles, triangle, 1, mirrored);
            int thirdOccurrence = source
                    ? HostVertexOccurrence.source(triangle, 2, mirrored)
                    : HostVertexOccurrence.proxy(sourceTriangles, triangle, 2, mirrored);
            if (source) {
                emitVertex(vertices, pose, normal, transformedNormal, out, first, red, green, blue, alpha,
                        lightBinding.packedLight(firstOccurrence));
                emitVertex(vertices, pose, normal, transformedNormal, out, second, red, green, blue, alpha,
                        lightBinding.packedLight(secondOccurrence));
                emitVertex(vertices, pose, normal, transformedNormal, out, third, red, green, blue, alpha,
                        lightBinding.packedLight(thirdOccurrence));
            } else {
                emitProxyVertex(pose, normal, transformedNormal, out, first, red, green, blue, alpha,
                        lightBinding.packedLight(firstOccurrence));
                emitProxyVertex(pose, normal, transformedNormal, out, second, red, green, blue, alpha,
                        lightBinding.packedLight(secondOccurrence));
                emitProxyVertex(pose, normal, transformedNormal, out, third, red, green, blue, alpha,
                        lightBinding.packedLight(thirdOccurrence));
            }
        }
    }

    private void emitProxyVertex(Matrix4fc pose, Matrix3fc normal, Vector3f transformedNormal,
                                 VertexConsumer out, int sourceVertex,
                                 float red, float green, float blue, float alpha, int light) {
        int index = sourceVertex * 12;
        normal.transform(lod.proxyComponent(index + 3), lod.proxyComponent(index + 4),
                lod.proxyComponent(index + 5), transformedNormal);
        float lengthSquared = transformedNormal.lengthSquared();
        if (!Float.isFinite(lengthSquared) || lengthSquared <= 1.0E-12F) {
            throw new IllegalArgumentException("HOST transformed normal must be finite and non-zero");
        }
        transformedNormal.mul((float) (1.0 / Math.sqrt(lengthSquared)));
        out.addVertex(pose, lod.proxyComponent(index), lod.proxyComponent(index + 1), lod.proxyComponent(index + 2))
                .setColor(lod.proxyComponent(index + 8) * red, lod.proxyComponent(index + 9) * green,
                        lod.proxyComponent(index + 10) * blue, lod.proxyComponent(index + 11) * alpha)
                .setUv(lod.proxyComponent(index + 6), lod.proxyComponent(index + 7))
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(transformedNormal.x, transformedNormal.y, transformedNormal.z);
    }

    private void emitRange(Matrix4fc pose, Matrix3fc normal, VertexConsumer out,
                           float red, float green, float blue, float alpha,
                           int light, boolean mirrored, int firstTriangle, int triangleCount,
                           boolean spatialOrder) {
        emitRange(pose, normal, out, red, green, blue, alpha, HostLightBinding.constant(light), mirrored,
                firstTriangle, triangleCount, spatialOrder);
    }

    private void emitRange(Matrix4fc pose, Matrix3fc normal, VertexConsumer out,
                           float red, float green, float blue, float alpha,
                           HostLightBinding lightBinding, boolean mirrored,
                           int firstTriangle, int triangleCount, boolean spatialOrder) {
        int available = Math.toIntExact(triangleCount());
        if (firstTriangle < 0 || triangleCount < 0 || firstTriangle > available - triangleCount) {
            throw new IndexOutOfBoundsException("HOST triangle range is outside the geometry");
        }
        Vector3f transformedNormal = new Vector3f();
        if (lightBinding == null) throw new NullPointerException("lightBinding");
        for (int orderedTriangle = firstTriangle; orderedTriangle < firstTriangle + triangleCount;
             orderedTriangle++) {
            int triangle = spatialOrder ? clusters.sourceTriangle(orderedTriangle) : orderedTriangle;
            int first = triangle * 3;
            int second = mirrored ? first + 2 : first + 1;
            int third = mirrored ? first + 1 : first + 2;
            int firstOccurrence = HostVertexOccurrence.source(triangle, 0, mirrored);
            int secondOccurrence = HostVertexOccurrence.source(triangle, 1, mirrored);
            int thirdOccurrence = HostVertexOccurrence.source(triangle, 2, mirrored);
            // Entity RenderTypes assemble quads; the duplicate produces a degenerate second triangle.
            emitVertex(vertices, pose, normal, transformedNormal, out, first, red, green, blue, alpha,
                    lightBinding.packedLight(firstOccurrence));
            emitVertex(vertices, pose, normal, transformedNormal, out, second, red, green, blue, alpha,
                    lightBinding.packedLight(secondOccurrence));
            int thirdLight = lightBinding.packedLight(thirdOccurrence);
            emitVertex(vertices, pose, normal, transformedNormal, out, third, red, green, blue, alpha, thirdLight);
            emitVertex(vertices, pose, normal, transformedNormal, out, third, red, green, blue, alpha, thirdLight);
        }
    }

    private static void emitVertex(float[] stream, Matrix4fc pose, Matrix3fc normal,
                            Vector3f transformedNormal,
                            VertexConsumer out, int sourceVertex,
                            float red, float green, float blue, float alpha, int light) {
        int index = sourceVertex * 12;
        normal.transform(stream[index + 3], stream[index + 4], stream[index + 5], transformedNormal);
        float lengthSquared = transformedNormal.lengthSquared();
        if (!Float.isFinite(lengthSquared) || lengthSquared <= 1.0E-12F) {
            throw new IllegalArgumentException("HOST transformed normal must be finite and non-zero");
        }
        transformedNormal.mul((float) (1.0 / Math.sqrt(lengthSquared)));
        out.addVertex(pose, stream[index], stream[index + 1], stream[index + 2])
                .setColor(stream[index + 8] * red, stream[index + 9] * green,
                        stream[index + 10] * blue, stream[index + 11] * alpha)
                .setUv(stream[index + 6], stream[index + 7]).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light).setNormal(transformedNormal.x, transformedNormal.y, transformedNormal.z);
    }
}
