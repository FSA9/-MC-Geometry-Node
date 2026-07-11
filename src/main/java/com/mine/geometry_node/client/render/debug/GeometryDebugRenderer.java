package com.mine.geometry_node.client.render.debug;

import com.mine.geometry_node.core.network.packet.s2c.PacketGeometryDebugSnapshot;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.List;

public final class GeometryDebugRenderer {
    private static final int FACE_R = 58;
    private static final int FACE_G = 156;
    private static final int FACE_B = 220;
    private static final int FACE_A = 46;
    private static final int EDGE_R = 235;
    private static final int EDGE_G = 245;
    private static final int EDGE_B = 255;
    private static final int EDGE_A = 230;
    private static final int POINT_R = 255;
    private static final int POINT_G = 178;
    private static final int POINT_B = 64;
    private static final int POINT_A = 255;
    private static final float EDGE_WIDTH = 1.6f;
    private static final float POINT_SIZE = 7.0f;

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
        VertexConsumer faces = bufferSource.getBuffer(AreaDebugRenderTypes.GEOMETRY_FACE);
        for (PacketGeometryDebugSnapshot.Mesh mesh : meshes) {
            drawFaces(mesh, faces, matrix, camPos);
        }
        bufferSource.endBatch(AreaDebugRenderTypes.GEOMETRY_FACE);

        PoseStack.Pose pose = poseStack.last();
        VertexConsumer lines = bufferSource.getBuffer(AreaDebugRenderTypes.GEOMETRY_LINE);
        for (PacketGeometryDebugSnapshot.Mesh mesh : meshes) {
            drawEdges(mesh, lines, pose, matrix, camPos);
        }
        bufferSource.endBatch(AreaDebugRenderTypes.GEOMETRY_LINE);

        VertexConsumer points = bufferSource.getBuffer(AreaDebugRenderTypes.GEOMETRY_POINT);
        for (PacketGeometryDebugSnapshot.Mesh mesh : meshes) {
            drawPoints(mesh, points, matrix, camPos);
        }
        poseStack.popPose();
        bufferSource.endBatch(AreaDebugRenderTypes.GEOMETRY_POINT);
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
        for (int i = 0; i + 3 < faces.length; i += 4) {
            int a = faces[i];
            int b = faces[i + 1];
            int c = faces[i + 2];
            int d = faces[i + 3];
            if (!validIndex(a, vertexCount) || !validIndex(b, vertexCount) || !validIndex(c, vertexCount) || !validIndex(d, vertexCount)) {
                continue;
            }
            drawFaceVertex(buffer, matrix, vertices, a, baseX, baseY, baseZ);
            drawFaceVertex(buffer, matrix, vertices, b, baseX, baseY, baseZ);
            drawFaceVertex(buffer, matrix, vertices, c, baseX, baseY, baseZ);
            drawFaceVertex(buffer, matrix, vertices, d, baseX, baseY, baseZ);
        }
    }

    private static void drawFaceVertex(VertexConsumer buffer,
                                       Matrix4f matrix,
                                       float[] vertices,
                                       int index,
                                       float baseX,
                                       float baseY,
                                       float baseZ) {
        int offset = index * 3;
        buffer.addVertex(matrix,
                        baseX + vertices[offset],
                        baseY + vertices[offset + 1],
                        baseZ + vertices[offset + 2])
                .setColor(FACE_R, FACE_G, FACE_B, FACE_A);
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
        for (int i = 0; i + 1 < edges.length; i += 2) {
            int a = edges[i];
            int b = edges[i + 1];
            if (!validIndex(a, vertexCount) || !validIndex(b, vertexCount)) {
                continue;
            }
            drawLine(buffer, pose, matrix, vertices, a, b, baseX, baseY, baseZ);
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
                                 float baseZ) {
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
                .setColor(EDGE_R, EDGE_G, EDGE_B, EDGE_A)
                .setNormal(pose, nx, ny, nz)
                .setLineWidth(EDGE_WIDTH);
        buffer.addVertex(matrix, x2, y2, z2)
                .setColor(EDGE_R, EDGE_G, EDGE_B, EDGE_A)
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
        for (int i = 0; i + 2 < vertices.length; i += 3) {
            buffer.addVertex(matrix,
                            baseX + vertices[i],
                            baseY + vertices[i + 1],
                            baseZ + vertices[i + 2])
                    .setColor(POINT_R, POINT_G, POINT_B, POINT_A)
                    .setLineWidth(POINT_SIZE);
        }
    }

    private static boolean validIndex(int index, int vertexCount) {
        return index >= 0 && index < vertexCount;
    }
}
