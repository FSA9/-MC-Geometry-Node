package com.mine.geometry_node.client.render.effects;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

final class BeamGeometry {
    private static final double MIN_LENGTH_SQR = 1.0e-5;
    private static final Vec3 X_AXIS = new Vec3(1.0, 0.0, 0.0);
    private static final Vec3 Y_AXIS = new Vec3(0.0, 1.0, 0.0);

    private BeamGeometry() {
    }

    static void drawPrism(VertexConsumer buffer, Matrix4f matrix,
                          Vec3 start, Vec3 end, double radius, int color) {
        Vec3 delta = end.subtract(start);
        if (delta.lengthSqr() < MIN_LENGTH_SQR) {
            return;
        }

        Vec3 direction = delta.normalize();
        Vec3 referenceUp = Math.abs(direction.y) > 0.99 ? X_AXIS : Y_AXIS;
        Vec3 right = direction.cross(referenceUp).normalize().scale(radius);
        Vec3 up = right.cross(direction).normalize().scale(radius);

        Vec3 p1 = start.add(right).add(up);
        Vec3 p2 = start.subtract(right).add(up);
        Vec3 p3 = start.subtract(right).subtract(up);
        Vec3 p4 = start.add(right).subtract(up);
        Vec3 p5 = p1.add(delta);
        Vec3 p6 = p2.add(delta);
        Vec3 p7 = p3.add(delta);
        Vec3 p8 = p4.add(delta);

        int a = (color >>> 24) & 0xFF;
        int r = (color >>> 16) & 0xFF;
        int g = (color >>> 8) & 0xFF;
        int b = color & 0xFF;

        drawQuad(buffer, matrix, p1, p5, p6, p2, r, g, b, a);
        drawQuad(buffer, matrix, p4, p3, p7, p8, r, g, b, a);
        drawQuad(buffer, matrix, p1, p4, p8, p5, r, g, b, a);
        drawQuad(buffer, matrix, p2, p6, p7, p3, r, g, b, a);
        drawQuad(buffer, matrix, p1, p2, p3, p4, r, g, b, a);
        drawQuad(buffer, matrix, p5, p8, p7, p6, r, g, b, a);
    }

    private static void drawQuad(VertexConsumer buffer, Matrix4f matrix,
                                 Vec3 v1, Vec3 v2, Vec3 v3, Vec3 v4,
                                 int r, int g, int b, int a) {
        buffer.addVertex(matrix, (float) v1.x, (float) v1.y, (float) v1.z).setColor(r, g, b, a);
        buffer.addVertex(matrix, (float) v2.x, (float) v2.y, (float) v2.z).setColor(r, g, b, a);
        buffer.addVertex(matrix, (float) v3.x, (float) v3.y, (float) v3.z).setColor(r, g, b, a);
        buffer.addVertex(matrix, (float) v4.x, (float) v4.y, (float) v4.z).setColor(r, g, b, a);
    }
}
