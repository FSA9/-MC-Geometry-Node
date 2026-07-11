package com.mine.geometry_node.core.engine.blueprint.debug;

import com.mine.geometry_node.core.node.value.geometry.GeometryValue;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class GeometryDebugMeshFactory {
    private static final int MAX_CUBE_AXIS_VERTICES = 32;
    private static final int MAX_CYLINDER_RADIAL_VERTICES = 96;
    private static final int MAX_CYLINDER_SIDE_SEGMENTS = 32;
    private static final int MAX_CYLINDER_FILL_SEGMENTS = 16;

    private GeometryDebugMeshFactory() {
    }

    public static List<GeometryDebugMesh> buildMeshes(String sourceKey,
                                                      String graphId,
                                                      String localId,
                                                      GeometryValue geometry,
                                                      int limit,
                                                      Vec3 translation) {
        if (geometry == null || geometry.isEmpty() || limit <= 0) {
            return List.of();
        }

        GeometryValue.Primitive[] primitives = geometry.primitives();
        List<GeometryDebugMesh> meshes = new ArrayList<>(Math.min(limit, primitives.length));
        Vec3 safeTranslation = translation != null ? translation : Vec3.ZERO;
        String safeLocalId = localId != null && !localId.isBlank() ? localId : "geometry";
        for (int i = 0; i < primitives.length && meshes.size() < limit; i++) {
            String id = sourceKey + ":" + safeLocalId + (primitives.length > 1 ? ":" + i : "");
            meshes.add(buildPrimitiveMesh(id, graphId, primitives[i], safeTranslation));
        }
        return meshes;
    }

    public static GeometryDebugMesh buildPrimitiveMesh(String id,
                                                       String graphId,
                                                       GeometryValue.Primitive primitive,
                                                       Vec3 translation) {
        Vec3 safeTranslation = translation != null ? translation : Vec3.ZERO;
        Vec3 center = primitive.center().add(safeTranslation);
        Vec3 size = sanitizeSize(primitive.size());
        return switch (primitive.type()) {
            case CUBE -> buildCubeMesh(
                    id,
                    graphId,
                    center,
                    size,
                    clampInt(primitive.verticesX(), 2, MAX_CUBE_AXIS_VERTICES),
                    clampInt(primitive.verticesY(), 2, MAX_CUBE_AXIS_VERTICES),
                    clampInt(primitive.verticesZ(), 2, MAX_CUBE_AXIS_VERTICES)
            );
            case CYLINDER -> buildCylinderMesh(
                    id,
                    graphId,
                    center,
                    clampInt(primitive.radialVertices(), 3, MAX_CYLINDER_RADIAL_VERTICES),
                    clampInt(primitive.sideSegments(), 1, MAX_CYLINDER_SIDE_SEGMENTS),
                    clampInt(primitive.fillSegments(), 1, MAX_CYLINDER_FILL_SEGMENTS),
                    (float) Math.max(0.001D, size.x * 0.5D),
                    (float) Math.max(0.001D, size.y),
                    primitive.fillType()
            );
        };
    }

    private static GeometryDebugMesh buildCubeMesh(String id,
                                                   String graphId,
                                                   Vec3 center,
                                                   Vec3 size,
                                                   int verticesX,
                                                   int verticesY,
                                                   int verticesZ) {
        MeshBuilder builder = new MeshBuilder();

        for (int z : new int[]{0, verticesZ - 1}) {
            for (int x = 0; x < verticesX - 1; x++) {
                for (int y = 0; y < verticesY - 1; y++) {
                    int a = cubeVertex(builder, size, verticesX, verticesY, verticesZ, x, y, z);
                    int b = cubeVertex(builder, size, verticesX, verticesY, verticesZ, x + 1, y, z);
                    int c = cubeVertex(builder, size, verticesX, verticesY, verticesZ, x + 1, y + 1, z);
                    int d = cubeVertex(builder, size, verticesX, verticesY, verticesZ, x, y + 1, z);
                    builder.addFaceWithEdges(a, b, c, d);
                }
            }
        }

        for (int x : new int[]{0, verticesX - 1}) {
            for (int z = 0; z < verticesZ - 1; z++) {
                for (int y = 0; y < verticesY - 1; y++) {
                    int a = cubeVertex(builder, size, verticesX, verticesY, verticesZ, x, y, z);
                    int b = cubeVertex(builder, size, verticesX, verticesY, verticesZ, x, y, z + 1);
                    int c = cubeVertex(builder, size, verticesX, verticesY, verticesZ, x, y + 1, z + 1);
                    int d = cubeVertex(builder, size, verticesX, verticesY, verticesZ, x, y + 1, z);
                    builder.addFaceWithEdges(a, b, c, d);
                }
            }
        }

        for (int y : new int[]{0, verticesY - 1}) {
            for (int x = 0; x < verticesX - 1; x++) {
                for (int z = 0; z < verticesZ - 1; z++) {
                    int a = cubeVertex(builder, size, verticesX, verticesY, verticesZ, x, y, z);
                    int b = cubeVertex(builder, size, verticesX, verticesY, verticesZ, x + 1, y, z);
                    int c = cubeVertex(builder, size, verticesX, verticesY, verticesZ, x + 1, y, z + 1);
                    int d = cubeVertex(builder, size, verticesX, verticesY, verticesZ, x, y, z + 1);
                    builder.addFaceWithEdges(a, b, c, d);
                }
            }
        }

        return builder.build(id, graphId, center);
    }

    private static int cubeVertex(MeshBuilder builder,
                                  Vec3 size,
                                  int verticesX,
                                  int verticesY,
                                  int verticesZ,
                                  int ix,
                                  int iy,
                                  int iz) {
        return builder.addVertex(
                key(1, ix, iy, iz),
                axis(0.0f, (float) size.x, ix, verticesX),
                axis(0.0f, (float) size.y, iy, verticesY),
                axis(0.0f, (float) size.z, iz, verticesZ)
        );
    }

    private static GeometryDebugMesh buildCylinderMesh(String id,
                                                       String graphId,
                                                       Vec3 center,
                                                       int radialVertices,
                                                       int sideSegments,
                                                       int fillSegments,
                                                       float radius,
                                                       float depth,
                                                       GeometryValue.CylinderFillType fillType) {
        MeshBuilder builder = new MeshBuilder();

        for (int y = 0; y < sideSegments; y++) {
            for (int radial = 0; radial < radialVertices; radial++) {
                int next = (radial + 1) % radialVertices;
                int a = cylinderSideVertex(builder, radialVertices, sideSegments, radius, depth, y, radial);
                int b = cylinderSideVertex(builder, radialVertices, sideSegments, radius, depth, y, next);
                int c = cylinderSideVertex(builder, radialVertices, sideSegments, radius, depth, y + 1, next);
                int d = cylinderSideVertex(builder, radialVertices, sideSegments, radius, depth, y + 1, radial);
                builder.addFaceWithEdges(a, b, c, d);
            }
        }

        if (fillType != GeometryValue.CylinderFillType.NONE) {
            addCylinderCap(builder, radialVertices, sideSegments, fillSegments, radius, depth, fillType, true);
            addCylinderCap(builder, radialVertices, sideSegments, fillSegments, radius, depth, fillType, false);
        }

        return builder.build(id, graphId, center);
    }

    private static int cylinderSideVertex(MeshBuilder builder,
                                          int radialVertices,
                                          int sideSegments,
                                          float radius,
                                          float depth,
                                          int yIndex,
                                          int radialIndex) {
        float theta = (float) (Math.PI * 2.0D * radialIndex / radialVertices);
        float y = axis(0.0f, depth, yIndex, sideSegments + 1);
        return builder.addVertex(
                key(2, yIndex, radialIndex, 0),
                (float) Math.cos(theta) * radius,
                y,
                (float) Math.sin(theta) * radius
        );
    }

    private static void addCylinderCap(MeshBuilder builder,
                                       int radialVertices,
                                       int sideSegments,
                                       int fillSegments,
                                       float radius,
                                       float depth,
                                       GeometryValue.CylinderFillType fillType,
                                       boolean top) {
        int sideY = top ? sideSegments : 0;
        int capKind = top ? 3 : 4;

        for (int ring = 1; ring <= fillSegments; ring++) {
            for (int radial = 0; radial < radialVertices; radial++) {
                int next = (radial + 1) % radialVertices;
                int a = capVertex(builder, radialVertices, sideSegments, fillSegments, radius, depth, capKind, top, sideY, ring - 1, radial);
                int b = capVertex(builder, radialVertices, sideSegments, fillSegments, radius, depth, capKind, top, sideY, ring, radial);
                int c = capVertex(builder, radialVertices, sideSegments, fillSegments, radius, depth, capKind, top, sideY, ring, next);
                int d = ring == 1
                        ? a
                        : capVertex(builder, radialVertices, sideSegments, fillSegments, radius, depth, capKind, top, sideY, ring - 1, next);

                if (fillType == GeometryValue.CylinderFillType.TRIANGLE) {
                    builder.addFaceWithEdges(a, b, c, d);
                } else {
                    builder.addFace(a, b, c, d);
                    builder.addEdge(b, c);
                    if (ring > 1) {
                        builder.addEdge(a, d);
                    }
                }
            }
        }
    }

    private static int capVertex(MeshBuilder builder,
                                 int radialVertices,
                                 int sideSegments,
                                 int fillSegments,
                                 float radius,
                                 float depth,
                                 int capKind,
                                 boolean top,
                                 int sideY,
                                 int ring,
                                 int radialIndex) {
        if (ring <= 0) {
            return builder.addVertex(key(capKind, 0, 0, 0), 0.0f, axis(0.0f, depth, top ? sideSegments : 0, sideSegments + 1), 0.0f);
        }
        if (ring >= fillSegments) {
            return cylinderSideVertex(builder, radialVertices, sideSegments, radius, depth, sideY, radialIndex);
        }

        float theta = (float) (Math.PI * 2.0D * radialIndex / radialVertices);
        float ringRadius = radius * ring / fillSegments;
        float y = axis(0.0f, depth, top ? sideSegments : 0, sideSegments + 1);
        return builder.addVertex(
                key(capKind, ring, radialIndex, 0),
                (float) Math.cos(theta) * ringRadius,
                y,
                (float) Math.sin(theta) * ringRadius
        );
    }

    private static float axis(float center, float size, int index, int count) {
        if (count <= 1) {
            return center;
        }
        return center - size * 0.5f + size * index / (count - 1);
    }

    private static Vec3 sanitizeSize(Vec3 size) {
        return new Vec3(
                sanitizePositive(size.x, 1.0D),
                sanitizePositive(size.y, 1.0D),
                sanitizePositive(size.z, 1.0D)
        );
    }

    private static double sanitizePositive(double value, double fallback) {
        if (!Double.isFinite(value)) {
            return fallback;
        }
        return Math.max(0.001D, Math.abs(value));
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static long key(int kind, int a, int b, int c) {
        return ((long) (kind & 0x3FF) << 54)
                | ((long) (a & 0x3FFFF) << 36)
                | ((long) (b & 0x3FFFF) << 18)
                | (long) (c & 0x3FFFF);
    }

    private static final class MeshBuilder {
        private final FloatArrayList vertices = new FloatArrayList();
        private final IntArrayList edges = new IntArrayList();
        private final IntArrayList faces = new IntArrayList();
        private final Long2IntOpenHashMap vertexByKey = new Long2IntOpenHashMap();
        private final LongOpenHashSet edgeKeys = new LongOpenHashSet();

        private MeshBuilder() {
            vertexByKey.defaultReturnValue(-1);
        }

        private int addVertex(long key, float x, float y, float z) {
            int existing = vertexByKey.get(key);
            if (existing >= 0) {
                return existing;
            }

            int index = vertices.size() / 3;
            vertexByKey.put(key, index);
            vertices.add(x);
            vertices.add(y);
            vertices.add(z);
            return index;
        }

        private void addEdge(int a, int b) {
            if (a == b) {
                return;
            }
            int min = Math.min(a, b);
            int max = Math.max(a, b);
            long key = ((long) min << 32) ^ (max & 0xFFFFFFFFL);
            if (edgeKeys.add(key)) {
                edges.add(a);
                edges.add(b);
            }
        }

        private void addFace(int a, int b, int c, int d) {
            faces.add(a);
            faces.add(b);
            faces.add(c);
            faces.add(d);
        }

        private void addFaceWithEdges(int a, int b, int c, int d) {
            addFace(a, b, c, d);
            addEdge(a, b);
            addEdge(b, c);
            addEdge(c, d);
            addEdge(d, a);
        }

        private GeometryDebugMesh build(String id, String graphId, Vec3 center) {
            return new GeometryDebugMesh(id, graphId, center, vertices.toFloatArray(), edges.toIntArray(), faces.toIntArray());
        }
    }
}
