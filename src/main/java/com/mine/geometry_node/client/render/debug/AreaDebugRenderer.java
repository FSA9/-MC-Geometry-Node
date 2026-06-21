package com.mine.geometry_node.client.render.debug;

import com.mine.geometry_node.core.network.packet.s2c.PacketAreaDebugSnapshot;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.List;

public final class AreaDebugRenderer {
    private static final int WHITE = 255;
    private static final int FACE_ALPHA = 38;
    private static final int LINE_ALPHA = 210;
    private static final int SPHERE_SEGMENTS = 24;
    private static final int SPHERE_RINGS = 12;
    private static final int CYLINDER_SEGMENTS = 32;

    private static List<PacketAreaDebugSnapshot.AreaBox> boxes = List.of();
    private static boolean enabled;

    private AreaDebugRenderer() {
    }

    public static void handleSnapshot(PacketAreaDebugSnapshot packet) {
        enabled = packet.enabled();
        boxes = enabled ? List.copyOf(packet.boxes()) : List.of();
    }

    public static void clear() {
        enabled = false;
        boxes = List.of();
    }

    public static void render(PoseStack poseStack, Camera camera) {
        if (Minecraft.getInstance().level == null) {
            clear();
            return;
        }
        if (!enabled || boxes.isEmpty()) return;

        Minecraft minecraft = Minecraft.getInstance();
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        Vec3 camPos = camera.position();

        poseStack.pushPose();
        for (PacketAreaDebugSnapshot.AreaBox box : boxes) {
            renderFaces(box, poseStack, bufferSource, camPos);
        }
        bufferSource.endBatch(AreaDebugRenderTypes.AREA_FACE);
        for (PacketAreaDebugSnapshot.AreaBox box : boxes) {
            renderLines(box, poseStack, bufferSource, camPos);
        }
        poseStack.popPose();
        bufferSource.endBatch(AreaDebugRenderTypes.AREA_LINE);
    }

    private static void renderFaces(PacketAreaDebugSnapshot.AreaBox box,
                                    PoseStack poseStack,
                                    MultiBufferSource.BufferSource bufferSource,
                                    Vec3 camPos) {
        poseStack.pushPose();
        transformArea(box, poseStack, camPos);
        Matrix4f matrix = poseStack.last().pose();
        VertexConsumer faces = bufferSource.getBuffer(AreaDebugRenderTypes.AREA_FACE);
        switch (box.shape()) {
            case "sphere" -> drawSphereFaces(faces, matrix, sphereRadius(box));
            case "cylinder" -> drawCylinderFaces(faces, matrix, (float) box.sizeX() * 0.5f, (float) box.sizeY() * 0.5f, (float) box.sizeZ() * 0.5f);
            default -> {
                float[] bounds = boxBounds(box);
                drawBoxFaces(faces, matrix, bounds[0], bounds[1], bounds[2], bounds[3], bounds[4], bounds[5]);
            }
        }
        poseStack.popPose();
    }

    private static void renderLines(PacketAreaDebugSnapshot.AreaBox box,
                                    PoseStack poseStack,
                                    MultiBufferSource.BufferSource bufferSource,
                                    Vec3 camPos) {
        poseStack.pushPose();
        transformArea(box, poseStack, camPos);
        PoseStack.Pose pose = poseStack.last();
        Matrix4f matrix = pose.pose();
        VertexConsumer lines = bufferSource.getBuffer(AreaDebugRenderTypes.AREA_LINE);
        switch (box.shape()) {
            case "sphere" -> drawSphereLines(lines, pose, matrix, sphereRadius(box));
            case "cylinder" -> drawCylinderLines(lines, pose, matrix, (float) box.sizeX() * 0.5f, (float) box.sizeY() * 0.5f, (float) box.sizeZ() * 0.5f);
            default -> {
                float[] bounds = boxBounds(box);
                drawBoxLines(lines, pose, matrix, bounds[0], bounds[1], bounds[2], bounds[3], bounds[4], bounds[5]);
            }
        }
        poseStack.popPose();
    }

    private static void transformArea(PacketAreaDebugSnapshot.AreaBox box, PoseStack poseStack, Vec3 camPos) {
        Vec3 renderPos = new Vec3(box.centerX(), box.centerY(), box.centerZ()).subtract(camPos);
        poseStack.translate(renderPos.x, renderPos.y, renderPos.z);
        poseStack.mulPose(new Quaternionf().rotationYXZ(
                (float) Math.toRadians(box.rotationY()),
                (float) Math.toRadians(box.rotationX()),
                (float) Math.toRadians(box.rotationZ())
        ));
    }

    private static float sphereRadius(PacketAreaDebugSnapshot.AreaBox box) {
        return (float) Math.max(box.sizeX(), Math.max(box.sizeY(), box.sizeZ())) * 0.5f;
    }

    private static float[] boxBounds(PacketAreaDebugSnapshot.AreaBox box) {
        float hX = (float) box.sizeX() * 0.5f;
        float hY = (float) box.sizeY() * 0.5f;
        float hZ = (float) box.sizeZ() * 0.5f;
        return new float[]{-hX, -hY, -hZ, hX, hY, hZ};
    }

    private static void drawBoxFaces(VertexConsumer buffer, Matrix4f matrix,
                                     float minX, float minY, float minZ,
                                     float maxX, float maxY, float maxZ) {
        drawQuad(buffer, matrix, minX, minY, minZ, maxX, minY, minZ, maxX, maxY, minZ, minX, maxY, minZ);
        drawQuad(buffer, matrix, minX, minY, maxZ, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, minY, maxZ);
        drawQuad(buffer, matrix, minX, minY, minZ, minX, minY, maxZ, maxX, minY, maxZ, maxX, minY, minZ);
        drawQuad(buffer, matrix, minX, maxY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, minX, maxY, maxZ);
        drawQuad(buffer, matrix, minX, minY, minZ, minX, maxY, minZ, minX, maxY, maxZ, minX, minY, maxZ);
        drawQuad(buffer, matrix, maxX, minY, minZ, maxX, minY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ);
    }

    private static void drawSphereFaces(VertexConsumer buffer, Matrix4f matrix, float radius) {
        for (int ring = 0; ring < SPHERE_RINGS; ring++) {
            float phi1 = (float) (-Math.PI * 0.5 + Math.PI * ring / SPHERE_RINGS);
            float phi2 = (float) (-Math.PI * 0.5 + Math.PI * (ring + 1) / SPHERE_RINGS);
            for (int segment = 0; segment < SPHERE_SEGMENTS; segment++) {
                float theta1 = (float) (Math.PI * 2.0 * segment / SPHERE_SEGMENTS);
                float theta2 = (float) (Math.PI * 2.0 * (segment + 1) / SPHERE_SEGMENTS);
                float[] p1 = spherePoint(radius, phi1, theta1);
                float[] p2 = spherePoint(radius, phi1, theta2);
                float[] p3 = spherePoint(radius, phi2, theta2);
                float[] p4 = spherePoint(radius, phi2, theta1);
                drawQuad(buffer, matrix, p1[0], p1[1], p1[2], p2[0], p2[1], p2[2], p3[0], p3[1], p3[2], p4[0], p4[1], p4[2]);
            }
        }
    }

    private static void drawCylinderFaces(VertexConsumer buffer, Matrix4f matrix, float radiusX, float halfY, float radiusZ) {
        for (int segment = 0; segment < CYLINDER_SEGMENTS; segment++) {
            float theta1 = (float) (Math.PI * 2.0 * segment / CYLINDER_SEGMENTS);
            float theta2 = (float) (Math.PI * 2.0 * (segment + 1) / CYLINDER_SEGMENTS);
            float x1 = (float) Math.cos(theta1) * radiusX;
            float z1 = (float) Math.sin(theta1) * radiusZ;
            float x2 = (float) Math.cos(theta2) * radiusX;
            float z2 = (float) Math.sin(theta2) * radiusZ;
            drawQuad(buffer, matrix, x1, -halfY, z1, x2, -halfY, z2, x2, halfY, z2, x1, halfY, z1);
            drawQuad(buffer, matrix, 0.0f, halfY, 0.0f, x1, halfY, z1, x2, halfY, z2, 0.0f, halfY, 0.0f);
            drawQuad(buffer, matrix, 0.0f, -halfY, 0.0f, x2, -halfY, z2, x1, -halfY, z1, 0.0f, -halfY, 0.0f);
        }
    }

    private static void drawQuad(VertexConsumer buffer, Matrix4f matrix,
                                 float x1, float y1, float z1,
                                 float x2, float y2, float z2,
                                 float x3, float y3, float z3,
                                 float x4, float y4, float z4) {
        buffer.addVertex(matrix, x1, y1, z1).setColor(WHITE, WHITE, WHITE, FACE_ALPHA);
        buffer.addVertex(matrix, x2, y2, z2).setColor(WHITE, WHITE, WHITE, FACE_ALPHA);
        buffer.addVertex(matrix, x3, y3, z3).setColor(WHITE, WHITE, WHITE, FACE_ALPHA);
        buffer.addVertex(matrix, x4, y4, z4).setColor(WHITE, WHITE, WHITE, FACE_ALPHA);
    }

    private static void drawBoxLines(VertexConsumer buffer, PoseStack.Pose pose, Matrix4f matrix,
                                     float minX, float minY, float minZ,
                                     float maxX, float maxY, float maxZ) {
        drawLine(buffer, pose, matrix, minX, minY, minZ, maxX, minY, minZ);
        drawLine(buffer, pose, matrix, maxX, minY, minZ, maxX, minY, maxZ);
        drawLine(buffer, pose, matrix, maxX, minY, maxZ, minX, minY, maxZ);
        drawLine(buffer, pose, matrix, minX, minY, maxZ, minX, minY, minZ);

        drawLine(buffer, pose, matrix, minX, maxY, minZ, maxX, maxY, minZ);
        drawLine(buffer, pose, matrix, maxX, maxY, minZ, maxX, maxY, maxZ);
        drawLine(buffer, pose, matrix, maxX, maxY, maxZ, minX, maxY, maxZ);
        drawLine(buffer, pose, matrix, minX, maxY, maxZ, minX, maxY, minZ);

        drawLine(buffer, pose, matrix, minX, minY, minZ, minX, maxY, minZ);
        drawLine(buffer, pose, matrix, maxX, minY, minZ, maxX, maxY, minZ);
        drawLine(buffer, pose, matrix, maxX, minY, maxZ, maxX, maxY, maxZ);
        drawLine(buffer, pose, matrix, minX, minY, maxZ, minX, maxY, maxZ);
    }

    private static void drawSphereLines(VertexConsumer buffer, PoseStack.Pose pose, Matrix4f matrix, float radius) {
        drawCircle(buffer, pose, matrix, radius, radius, 0.0f, CirclePlane.XZ);
        drawCircle(buffer, pose, matrix, radius, radius, 0.0f, CirclePlane.XY);
        drawCircle(buffer, pose, matrix, radius, radius, 0.0f, CirclePlane.YZ);
    }

    private static void drawCylinderLines(VertexConsumer buffer, PoseStack.Pose pose, Matrix4f matrix, float radiusX, float halfY, float radiusZ) {
        drawCircle(buffer, pose, matrix, radiusX, radiusZ, halfY, CirclePlane.XZ);
        drawCircle(buffer, pose, matrix, radiusX, radiusZ, -halfY, CirclePlane.XZ);
        drawLine(buffer, pose, matrix, radiusX, -halfY, 0.0f, radiusX, halfY, 0.0f);
        drawLine(buffer, pose, matrix, -radiusX, -halfY, 0.0f, -radiusX, halfY, 0.0f);
        drawLine(buffer, pose, matrix, 0.0f, -halfY, radiusZ, 0.0f, halfY, radiusZ);
        drawLine(buffer, pose, matrix, 0.0f, -halfY, -radiusZ, 0.0f, halfY, -radiusZ);
    }

    private static void drawCircle(VertexConsumer buffer,
                                   PoseStack.Pose pose,
                                   Matrix4f matrix,
                                   float radiusA,
                                   float radiusB,
                                   float offset,
                                   CirclePlane plane) {
        for (int segment = 0; segment < CYLINDER_SEGMENTS; segment++) {
            float theta1 = (float) (Math.PI * 2.0 * segment / CYLINDER_SEGMENTS);
            float theta2 = (float) (Math.PI * 2.0 * (segment + 1) / CYLINDER_SEGMENTS);
            float[] p1 = circlePoint(radiusA, radiusB, offset, theta1, plane);
            float[] p2 = circlePoint(radiusA, radiusB, offset, theta2, plane);
            drawLine(buffer, pose, matrix, p1[0], p1[1], p1[2], p2[0], p2[1], p2[2]);
        }
    }

    private static float[] spherePoint(float radius, float phi, float theta) {
        float cosPhi = (float) Math.cos(phi);
        return new float[]{
                radius * cosPhi * (float) Math.cos(theta),
                radius * (float) Math.sin(phi),
                radius * cosPhi * (float) Math.sin(theta)
        };
    }

    private static float[] circlePoint(float radiusA, float radiusB, float offset, float theta, CirclePlane plane) {
        float a = (float) Math.cos(theta) * radiusA;
        float b = (float) Math.sin(theta) * radiusB;
        return switch (plane) {
            case XZ -> new float[]{a, offset, b};
            case XY -> new float[]{a, b, offset};
            case YZ -> new float[]{offset, a, b};
        };
    }

    private static void drawLine(VertexConsumer buffer, PoseStack.Pose pose, Matrix4f matrix,
                                 float x1, float y1, float z1,
                                 float x2, float y2, float z2) {
        float nx = x2 - x1;
        float ny = y2 - y1;
        float nz = z2 - z1;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len > 0) {
            nx /= len;
            ny /= len;
            nz /= len;
        }
        buffer.addVertex(matrix, x1, y1, z1).setColor(WHITE, WHITE, WHITE, LINE_ALPHA).setNormal(pose, nx, ny, nz);
        buffer.addVertex(matrix, x2, y2, z2).setColor(WHITE, WHITE, WHITE, LINE_ALPHA).setNormal(pose, nx, ny, nz);
    }

    private enum CirclePlane {
        XZ,
        XY,
        YZ
    }
}
