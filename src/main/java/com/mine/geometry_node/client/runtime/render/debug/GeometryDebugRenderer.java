package com.mine.geometry_node.client.runtime.render.debug;

import com.mine.geometry_node.core.engine.blueprint.debug.GeometryDebugType;
import com.mine.geometry_node.core.network.packet.s2c.PacketGeometryDebugSnapshot;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

public final class GeometryDebugRenderer {
    private static final int FACE_A = 46;
    private static final int EDGE_A = 230;
    private static final int POINT_A = 255;
    private static final float EDGE_WIDTH = 1.6f;
    private static final float POINT_SIZE = 7.0f;
    private static final int PERFECT_CURVE_SEGMENTS = 128;
    private static final int PERFECT_SPHERE_SEGMENTS = 64;
    private static final int PERFECT_SPHERE_RINGS = 32;

    private static List<PacketGeometryDebugSnapshot.Mesh> meshes = List.of();
    private static boolean enabled;

    private GeometryDebugRenderer() {
    }

    public static void handleSnapshot(PacketGeometryDebugSnapshot packet) {
        enabled = packet.enabled();
        meshes = enabled ? List.copyOf(packet.meshes()) : List.of();
    }

    public static void clear() {
        enabled = false;
        meshes = List.of();
    }

    public static void render(PoseStack poseStack, Camera camera) {
        if (Minecraft.getInstance().level == null) {
            clear();
            return;
        }
        if (!enabled || meshes.isEmpty()) return;

        Minecraft minecraft = Minecraft.getInstance();
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        Vec3 camPos = camera.position();

        poseStack.pushPose();
        Matrix4f matrix = poseStack.last().pose();
        VertexConsumer faces = bufferSource.getBuffer(GeometryDebugRenderTypes.GEOMETRY_FACE);
        for (PacketGeometryDebugSnapshot.Mesh mesh : meshes) {
            switch (mesh.geometryType()) {
                case MESH -> drawFaces(mesh, faces, matrix, camPos);
                case PERFECT_CYLINDER -> drawPerfectCylinderFaces(mesh, faces, matrix, camPos);
                case PERFECT_SPHERE -> drawPerfectSphereFaces(mesh, faces, matrix, camPos);
            }
        }
        bufferSource.endBatch(GeometryDebugRenderTypes.GEOMETRY_FACE);

        PoseStack.Pose pose = poseStack.last();
        VertexConsumer lines = bufferSource.getBuffer(GeometryDebugRenderTypes.GEOMETRY_LINE);
        for (PacketGeometryDebugSnapshot.Mesh mesh : meshes) {
            switch (mesh.geometryType()) {
                case MESH -> drawEdges(mesh, lines, pose, matrix, camPos);
                case PERFECT_CYLINDER -> drawPerfectCylinderLines(mesh, lines, pose, matrix, camPos);
                case PERFECT_SPHERE -> drawPerfectSphereGuides(mesh, lines, pose, matrix, camPos);
            }
        }
        bufferSource.endBatch(GeometryDebugRenderTypes.GEOMETRY_LINE);

        VertexConsumer points = bufferSource.getBuffer(GeometryDebugRenderTypes.GEOMETRY_POINT);
        for (PacketGeometryDebugSnapshot.Mesh mesh : meshes) {
            if (mesh.geometryType() == GeometryDebugType.MESH && mesh.showPoints()) {
                drawPoints(mesh, points, matrix, camPos);
            }
        }
        poseStack.popPose();
        bufferSource.endBatch(GeometryDebugRenderTypes.GEOMETRY_POINT);
    }

    private static void drawFaces(PacketGeometryDebugSnapshot.Mesh mesh,
                                  VertexConsumer buffer,
                                  Matrix4f matrix,
                                  Vec3 camPos) {
        float[] vertices = mesh.vertices();
        int[] faces = mesh.faces();
        int vertexCount = vertices.length / 3;
        float baseX = (float) (mesh.centerX() - camPos.x);
        float baseY = (float) (mesh.centerY() - camPos.y);
        float baseZ = (float) (mesh.centerZ() - camPos.z);
        int red = red(mesh.color());
        int green = green(mesh.color());
        int blue = blue(mesh.color());
        int alpha = scaledAlpha(mesh.color(), FACE_A);
        for (int i = 0; i + 3 < faces.length; i += 4) {
            int a = faces[i];
            int b = faces[i + 1];
            int c = faces[i + 2];
            int d = faces[i + 3];
            if (!validIndex(a, vertexCount) || !validIndex(b, vertexCount) || !validIndex(c, vertexCount) || !validIndex(d, vertexCount)) {
                continue;
            }
            drawFaceVertex(buffer, matrix, vertices, a, baseX, baseY, baseZ, red, green, blue, alpha);
            drawFaceVertex(buffer, matrix, vertices, b, baseX, baseY, baseZ, red, green, blue, alpha);
            drawFaceVertex(buffer, matrix, vertices, c, baseX, baseY, baseZ, red, green, blue, alpha);
            drawFaceVertex(buffer, matrix, vertices, d, baseX, baseY, baseZ, red, green, blue, alpha);
        }
    }

    private static void drawFaceVertex(VertexConsumer buffer,
                                       Matrix4f matrix,
                                       float[] vertices,
                                       int index,
                                       float baseX,
                                       float baseY,
                                       float baseZ,
                                       int red,
                                       int green,
                                       int blue,
                                       int alpha) {
        int offset = index * 3;
        buffer.addVertex(matrix,
                        baseX + vertices[offset],
                        baseY + vertices[offset + 1],
                        baseZ + vertices[offset + 2])
                .setColor(red, green, blue, alpha);
    }

    private static void drawEdges(PacketGeometryDebugSnapshot.Mesh mesh,
                                  VertexConsumer buffer,
                                  PoseStack.Pose pose,
                                  Matrix4f matrix,
                                  Vec3 camPos) {
        float[] vertices = mesh.vertices();
        int[] edges = mesh.edges();
        int vertexCount = vertices.length / 3;
        float baseX = (float) (mesh.centerX() - camPos.x);
        float baseY = (float) (mesh.centerY() - camPos.y);
        float baseZ = (float) (mesh.centerZ() - camPos.z);
        int red = red(mesh.color());
        int green = green(mesh.color());
        int blue = blue(mesh.color());
        int alpha = scaledAlpha(mesh.color(), EDGE_A);
        for (int i = 0; i + 1 < edges.length; i += 2) {
            int a = edges[i];
            int b = edges[i + 1];
            if (!validIndex(a, vertexCount) || !validIndex(b, vertexCount)) {
                continue;
            }
            drawLine(buffer, pose, matrix, vertices, a, b, baseX, baseY, baseZ,
                    red, green, blue, alpha);
        }
    }

    private static void drawLine(VertexConsumer buffer,
                                 PoseStack.Pose pose,
                                 Matrix4f matrix,
                                 float[] vertices,
                                 int a,
                                 int b,
                                 float baseX,
                                 float baseY,
                                 float baseZ,
                                 int red,
                                 int green,
                                 int blue,
                                 int alpha) {
        int offsetA = a * 3;
        int offsetB = b * 3;
        float x1 = baseX + vertices[offsetA];
        float y1 = baseY + vertices[offsetA + 1];
        float z1 = baseZ + vertices[offsetA + 2];
        float x2 = baseX + vertices[offsetB];
        float y2 = baseY + vertices[offsetB + 1];
        float z2 = baseZ + vertices[offsetB + 2];

        float nx = x2 - x1;
        float ny = y2 - y1;
        float nz = z2 - z1;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len > 0.0f) {
            nx /= len;
            ny /= len;
            nz /= len;
        }

        buffer.addVertex(matrix, x1, y1, z1)
                .setColor(red, green, blue, alpha)
                .setNormal(pose, nx, ny, nz)
                .setLineWidth(EDGE_WIDTH);
        buffer.addVertex(matrix, x2, y2, z2)
                .setColor(red, green, blue, alpha)
                .setNormal(pose, nx, ny, nz)
                .setLineWidth(EDGE_WIDTH);
    }

    private static void drawPoints(PacketGeometryDebugSnapshot.Mesh mesh,
                                   VertexConsumer buffer,
                                   Matrix4f matrix,
                                   Vec3 camPos) {
        float[] vertices = mesh.vertices();
        float baseX = (float) (mesh.centerX() - camPos.x);
        float baseY = (float) (mesh.centerY() - camPos.y);
        float baseZ = (float) (mesh.centerZ() - camPos.z);
        int red = red(mesh.color());
        int green = green(mesh.color());
        int blue = blue(mesh.color());
        int alpha = scaledAlpha(mesh.color(), POINT_A);
        for (int i = 0; i + 2 < vertices.length; i += 3) {
            buffer.addVertex(matrix,
                            baseX + vertices[i],
                            baseY + vertices[i + 1],
                            baseZ + vertices[i + 2])
                    .setColor(red, green, blue, alpha)
                    .setLineWidth(POINT_SIZE);
        }
    }

    private static void drawPerfectCylinderFaces(PacketGeometryDebugSnapshot.Mesh mesh,
                                                 VertexConsumer buffer,
                                                 Matrix4f matrix,
                                                 Vec3 camPos) {
        PrimitiveTransform transform = new PrimitiveTransform(mesh, camPos);
        float radiusX = positiveHalf(mesh.sizeX());
        float halfY = positiveHalf(mesh.sizeY());
        float radiusZ = positiveHalf(mesh.sizeZ());
        int red = red(mesh.color());
        int green = green(mesh.color());
        int blue = blue(mesh.color());
        int alpha = scaledAlpha(mesh.color(), FACE_A);

        for (int segment = 0; segment < PERFECT_CURVE_SEGMENTS; segment++) {
            double theta1 = Math.PI * 2.0D * segment / PERFECT_CURVE_SEGMENTS;
            double theta2 = Math.PI * 2.0D * (segment + 1) / PERFECT_CURVE_SEGMENTS;
            float x1 = (float) Math.cos(theta1) * radiusX;
            float z1 = (float) Math.sin(theta1) * radiusZ;
            float x2 = (float) Math.cos(theta2) * radiusX;
            float z2 = (float) Math.sin(theta2) * radiusZ;

            transform.faceVertex(buffer, matrix, x1, -halfY, z1, red, green, blue, alpha);
            transform.faceVertex(buffer, matrix, x2, -halfY, z2, red, green, blue, alpha);
            transform.faceVertex(buffer, matrix, x2, halfY, z2, red, green, blue, alpha);
            transform.faceVertex(buffer, matrix, x1, halfY, z1, red, green, blue, alpha);

            transform.faceVertex(buffer, matrix, 0.0F, halfY, 0.0F, red, green, blue, alpha);
            transform.faceVertex(buffer, matrix, x1, halfY, z1, red, green, blue, alpha);
            transform.faceVertex(buffer, matrix, x2, halfY, z2, red, green, blue, alpha);
            transform.faceVertex(buffer, matrix, 0.0F, halfY, 0.0F, red, green, blue, alpha);

            transform.faceVertex(buffer, matrix, 0.0F, -halfY, 0.0F, red, green, blue, alpha);
            transform.faceVertex(buffer, matrix, x2, -halfY, z2, red, green, blue, alpha);
            transform.faceVertex(buffer, matrix, x1, -halfY, z1, red, green, blue, alpha);
            transform.faceVertex(buffer, matrix, 0.0F, -halfY, 0.0F, red, green, blue, alpha);
        }
    }

    private static void drawPerfectCylinderLines(PacketGeometryDebugSnapshot.Mesh mesh,
                                                 VertexConsumer buffer,
                                                 PoseStack.Pose pose,
                                                 Matrix4f matrix,
                                                 Vec3 camPos) {
        PrimitiveTransform transform = new PrimitiveTransform(mesh, camPos);
        float radiusX = positiveHalf(mesh.sizeX());
        float halfY = positiveHalf(mesh.sizeY());
        float radiusZ = positiveHalf(mesh.sizeZ());
        drawEllipse(buffer, pose, matrix, transform, radiusX, radiusZ, halfY, CirclePlane.XZ, mesh.color());
        drawEllipse(buffer, pose, matrix, transform, radiusX, radiusZ, -halfY, CirclePlane.XZ, mesh.color());

        drawPrimitiveLine(buffer, pose, matrix, transform, radiusX, -halfY, 0.0F, radiusX, halfY, 0.0F, mesh.color());
        drawPrimitiveLine(buffer, pose, matrix, transform, -radiusX, -halfY, 0.0F, -radiusX, halfY, 0.0F, mesh.color());
        drawPrimitiveLine(buffer, pose, matrix, transform, 0.0F, -halfY, radiusZ, 0.0F, halfY, radiusZ, mesh.color());
        drawPrimitiveLine(buffer, pose, matrix, transform, 0.0F, -halfY, -radiusZ, 0.0F, halfY, -radiusZ, mesh.color());
    }

    private static void drawPerfectSphereFaces(PacketGeometryDebugSnapshot.Mesh mesh,
                                               VertexConsumer buffer,
                                               Matrix4f matrix,
                                               Vec3 camPos) {
        PrimitiveTransform transform = new PrimitiveTransform(mesh, camPos);
        float radius = perfectSphereRadius(mesh);
        int red = red(mesh.color());
        int green = green(mesh.color());
        int blue = blue(mesh.color());
        int alpha = scaledAlpha(mesh.color(), FACE_A);

        for (int ring = 0; ring < PERFECT_SPHERE_RINGS; ring++) {
            double phi1 = -Math.PI * 0.5D + Math.PI * ring / PERFECT_SPHERE_RINGS;
            double phi2 = -Math.PI * 0.5D + Math.PI * (ring + 1) / PERFECT_SPHERE_RINGS;
            float cosPhi1 = (float) Math.cos(phi1);
            float sinPhi1 = (float) Math.sin(phi1);
            float cosPhi2 = (float) Math.cos(phi2);
            float sinPhi2 = (float) Math.sin(phi2);
            for (int segment = 0; segment < PERFECT_SPHERE_SEGMENTS; segment++) {
                double theta1 = Math.PI * 2.0D * segment / PERFECT_SPHERE_SEGMENTS;
                double theta2 = Math.PI * 2.0D * (segment + 1) / PERFECT_SPHERE_SEGMENTS;
                float cosTheta1 = (float) Math.cos(theta1);
                float sinTheta1 = (float) Math.sin(theta1);
                float cosTheta2 = (float) Math.cos(theta2);
                float sinTheta2 = (float) Math.sin(theta2);
                transform.faceVertex(buffer, matrix,
                        radius * cosPhi1 * cosTheta1, radius * sinPhi1, radius * cosPhi1 * sinTheta1,
                        red, green, blue, alpha);
                transform.faceVertex(buffer, matrix,
                        radius * cosPhi1 * cosTheta2, radius * sinPhi1, radius * cosPhi1 * sinTheta2,
                        red, green, blue, alpha);
                transform.faceVertex(buffer, matrix,
                        radius * cosPhi2 * cosTheta2, radius * sinPhi2, radius * cosPhi2 * sinTheta2,
                        red, green, blue, alpha);
                transform.faceVertex(buffer, matrix,
                        radius * cosPhi2 * cosTheta1, radius * sinPhi2, radius * cosPhi2 * sinTheta1,
                        red, green, blue, alpha);
            }
        }
    }

    private static void drawPerfectSphereGuides(PacketGeometryDebugSnapshot.Mesh mesh,
                                                VertexConsumer buffer,
                                                PoseStack.Pose pose,
                                                Matrix4f matrix,
                                                Vec3 camPos) {
        PrimitiveTransform transform = new PrimitiveTransform(mesh, camPos);
        float radius = perfectSphereRadius(mesh);
        drawEllipse(buffer, pose, matrix, transform, radius, radius, 0.0F, CirclePlane.XY, mesh.color());
        drawEllipse(buffer, pose, matrix, transform, radius, radius, 0.0F, CirclePlane.XZ, mesh.color());
        drawEllipse(buffer, pose, matrix, transform, radius, radius, 0.0F, CirclePlane.YZ, mesh.color());
    }

    private static void drawEllipse(VertexConsumer buffer,
                                    PoseStack.Pose pose,
                                    Matrix4f matrix,
                                    PrimitiveTransform transform,
                                    float radiusA,
                                    float radiusB,
                                    float offset,
                                    CirclePlane plane,
                                    int color) {
        for (int segment = 0; segment < PERFECT_CURVE_SEGMENTS; segment++) {
            double theta1 = Math.PI * 2.0D * segment / PERFECT_CURVE_SEGMENTS;
            double theta2 = Math.PI * 2.0D * (segment + 1) / PERFECT_CURVE_SEGMENTS;
            float a1 = (float) Math.cos(theta1) * radiusA;
            float b1 = (float) Math.sin(theta1) * radiusB;
            float a2 = (float) Math.cos(theta2) * radiusA;
            float b2 = (float) Math.sin(theta2) * radiusB;
            switch (plane) {
                case XY -> drawPrimitiveLine(buffer, pose, matrix, transform,
                        a1, b1, offset, a2, b2, offset, color);
                case XZ -> drawPrimitiveLine(buffer, pose, matrix, transform,
                        a1, offset, b1, a2, offset, b2, color);
                case YZ -> drawPrimitiveLine(buffer, pose, matrix, transform,
                        offset, a1, b1, offset, a2, b2, color);
            }
        }
    }

    private static void drawPrimitiveLine(VertexConsumer buffer,
                                          PoseStack.Pose pose,
                                          Matrix4f matrix,
                                          PrimitiveTransform transform,
                                          float x1,
                                          float y1,
                                          float z1,
                                          float x2,
                                          float y2,
                                          float z2,
                                          int color) {
        Vector3f start = transform.first(x1, y1, z1);
        Vector3f end = transform.second(x2, y2, z2);
        float nx = end.x - start.x;
        float ny = end.y - start.y;
        float nz = end.z - start.z;
        float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (length > 0.0F) {
            nx /= length;
            ny /= length;
            nz /= length;
        }
        int alpha = scaledAlpha(color, EDGE_A);
        buffer.addVertex(matrix, start.x, start.y, start.z)
                .setColor(red(color), green(color), blue(color), alpha)
                .setNormal(pose, nx, ny, nz)
                .setLineWidth(EDGE_WIDTH);
        buffer.addVertex(matrix, end.x, end.y, end.z)
                .setColor(red(color), green(color), blue(color), alpha)
                .setNormal(pose, nx, ny, nz)
                .setLineWidth(EDGE_WIDTH);
    }

    private static float perfectSphereRadius(PacketGeometryDebugSnapshot.Mesh mesh) {
        return positiveHalf(Math.max(mesh.sizeX(), Math.max(mesh.sizeY(), mesh.sizeZ())));
    }

    private static float positiveHalf(double value) {
        return (float) Math.max(0.001D, Math.abs(value) * 0.5D);
    }

    private static int red(int color) {
        return color >>> 16 & 0xFF;
    }

    private static int green(int color) {
        return color >>> 8 & 0xFF;
    }

    private static int blue(int color) {
        return color & 0xFF;
    }

    private static int scaledAlpha(int color, int renderAlpha) {
        return ((color >>> 24) & 0xFF) * renderAlpha / 255;
    }

    private static boolean validIndex(int index, int vertexCount) {
        return index >= 0 && index < vertexCount;
    }

    private enum CirclePlane {
        XY,
        XZ,
        YZ
    }

    private static final class PrimitiveTransform {
        private final Quaternionf rotation;
        private final float baseX;
        private final float baseY;
        private final float baseZ;
        private final Vector3f first = new Vector3f();
        private final Vector3f second = new Vector3f();

        private PrimitiveTransform(PacketGeometryDebugSnapshot.Mesh mesh, Vec3 camPos) {
            rotation = new Quaternionf().rotationYXZ(
                    (float) Math.toRadians(mesh.rotationY()),
                    (float) Math.toRadians(mesh.rotationX()),
                    (float) Math.toRadians(mesh.rotationZ())
            );
            baseX = (float) (mesh.centerX() - camPos.x);
            baseY = (float) (mesh.centerY() - camPos.y);
            baseZ = (float) (mesh.centerZ() - camPos.z);
        }

        private Vector3f first(float x, float y, float z) {
            return transform(first, x, y, z);
        }

        private Vector3f second(float x, float y, float z) {
            return transform(second, x, y, z);
        }

        private void faceVertex(VertexConsumer buffer,
                                Matrix4f matrix,
                                float x,
                                float y,
                                float z,
                                int red,
                                int green,
                                int blue,
                                int alpha) {
            Vector3f vertex = first(x, y, z);
            buffer.addVertex(matrix, vertex.x, vertex.y, vertex.z).setColor(red, green, blue, alpha);
        }

        private Vector3f transform(Vector3f target, float x, float y, float z) {
            target.set(x, y, z);
            rotation.transform(target);
            target.add(baseX, baseY, baseZ);
            return target;
        }
    }
}
