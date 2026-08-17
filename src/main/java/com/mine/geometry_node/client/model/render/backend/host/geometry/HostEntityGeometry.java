package com.mine.geometry_node.client.model.render.backend.host.geometry;

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

    HostEntityGeometry(float[] vertices) {
        this.vertices = Arrays.copyOf(vertices, vertices.length);
        this.clusters = HostSpatialClusterPlan.build(this.vertices);
    }

    public long triangleCount() {
        return vertices.length / 36L;
    }

    public HostSpatialClusterPlan clusters() { return clusters; }

    float[] staticVertexData() {
        return Arrays.copyOf(vertices, vertices.length);
    }

    public void emit(PoseStack.Pose pose, VertexConsumer out, float red, float green, float blue, float alpha,
                     int light, boolean mirrored) {
        emitRange(pose.pose(), pose.normal(), out, red, green, blue, alpha, light, mirrored,
                0, Math.toIntExact(triangleCount()));
    }

    public void emitRange(Matrix4fc pose, Matrix3fc normal, VertexConsumer out,
                          float red, float green, float blue, float alpha,
                          int light, boolean mirrored, int firstTriangle, int triangleCount) {
        emitRange(pose, normal, out, red, green, blue, alpha, light, mirrored,
                firstTriangle, triangleCount, false);
    }

    public void emitOrderedRange(Matrix4fc pose, Matrix3fc normal, VertexConsumer out,
                                 float red, float green, float blue, float alpha,
                                 int light, boolean mirrored, int firstTriangle, int triangleCount) {
        emitRange(pose, normal, out, red, green, blue, alpha, light, mirrored,
                firstTriangle, triangleCount, true);
    }

    private void emitRange(Matrix4fc pose, Matrix3fc normal, VertexConsumer out,
                           float red, float green, float blue, float alpha,
                           int light, boolean mirrored, int firstTriangle, int triangleCount,
                           boolean spatialOrder) {
        int available = Math.toIntExact(triangleCount());
        if (firstTriangle < 0 || triangleCount < 0 || firstTriangle > available - triangleCount) {
            throw new IndexOutOfBoundsException("HOST triangle range is outside the geometry");
        }
        Vector3f transformedNormal = new Vector3f();
        for (int orderedTriangle = firstTriangle; orderedTriangle < firstTriangle + triangleCount;
             orderedTriangle++) {
            int triangle = spatialOrder ? clusters.sourceTriangle(orderedTriangle) : orderedTriangle;
            int first = triangle * 3;
            int second = mirrored ? first + 2 : first + 1;
            int third = mirrored ? first + 1 : first + 2;
            // Entity RenderTypes assemble quads; the duplicate produces a degenerate second triangle.
            emitVertex(pose, normal, transformedNormal, out, first, red, green, blue, alpha, light);
            emitVertex(pose, normal, transformedNormal, out, second, red, green, blue, alpha, light);
            emitVertex(pose, normal, transformedNormal, out, third, red, green, blue, alpha, light);
            emitVertex(pose, normal, transformedNormal, out, third, red, green, blue, alpha, light);
        }
    }

    private void emitVertex(Matrix4fc pose, Matrix3fc normal, Vector3f transformedNormal,
                            VertexConsumer out, int sourceVertex,
                            float red, float green, float blue, float alpha, int light) {
        int index = sourceVertex * 12;
        normal.transform(vertices[index + 3], vertices[index + 4], vertices[index + 5], transformedNormal);
        float lengthSquared = transformedNormal.lengthSquared();
        if (!Float.isFinite(lengthSquared) || lengthSquared <= 1.0E-12F) {
            throw new IllegalArgumentException("HOST transformed normal must be finite and non-zero");
        }
        transformedNormal.mul((float) (1.0 / Math.sqrt(lengthSquared)));
        out.addVertex(pose, vertices[index], vertices[index + 1], vertices[index + 2])
                .setColor(vertices[index + 8] * red, vertices[index + 9] * green,
                        vertices[index + 10] * blue, vertices[index + 11] * alpha)
                .setUv(vertices[index + 6], vertices[index + 7]).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light).setNormal(transformedNormal.x, transformedNormal.y, transformedNormal.z);
    }
}
