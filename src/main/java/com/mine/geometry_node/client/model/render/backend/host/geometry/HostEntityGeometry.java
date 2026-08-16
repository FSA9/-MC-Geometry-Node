package com.mine.geometry_node.client.model.render.backend.host.geometry;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;

/** Immutable host entity vertex stream produced from one canonical primitive. */
public final class HostEntityGeometry {
    private final float[] vertices;

    HostEntityGeometry(float[] vertices) {
        this.vertices = vertices;
    }

    public long triangleCount() {
        return vertices.length / 36L;
    }

    public void emit(PoseStack.Pose pose, VertexConsumer out, float red, float green, float blue, float alpha,
                     int light, boolean mirrored) {
        int triangleCount = vertices.length / 36;
        for (int triangle = 0; triangle < triangleCount; triangle++) {
            int first = triangle * 3;
            int second = mirrored ? first + 2 : first + 1;
            int third = mirrored ? first + 1 : first + 2;
            // Entity RenderTypes assemble quads; the duplicate produces a degenerate second triangle.
            emitVertex(pose, out, first, red, green, blue, alpha, light);
            emitVertex(pose, out, second, red, green, blue, alpha, light);
            emitVertex(pose, out, third, red, green, blue, alpha, light);
            emitVertex(pose, out, third, red, green, blue, alpha, light);
        }
    }

    private void emitVertex(PoseStack.Pose pose, VertexConsumer out, int sourceVertex,
                            float red, float green, float blue, float alpha, int light) {
        int index = sourceVertex * 12;
        out.addVertex(pose, vertices[index], vertices[index + 1], vertices[index + 2])
                .setColor(vertices[index + 8] * red, vertices[index + 9] * green,
                        vertices[index + 10] * blue, vertices[index + 11] * alpha)
                .setUv(vertices[index + 6], vertices[index + 7]).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light).setNormal(pose, vertices[index + 3], vertices[index + 4], vertices[index + 5]);
    }
}
