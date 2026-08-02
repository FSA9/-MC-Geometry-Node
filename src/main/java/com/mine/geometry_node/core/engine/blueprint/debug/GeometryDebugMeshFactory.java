package com.mine.geometry_node.core.engine.blueprint.debug;

import com.mine.geometry_node.core.node.value.geometry.GeometryValue;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public final class GeometryDebugMeshFactory {
    private static final int MAX_CUBE_AXIS_VERTICES = 32;
    private static final int MAX_CYLINDER_RADIAL_VERTICES = 96;
    private static final int MAX_CYLINDER_SIDE_SEGMENTS = 32;
    private static final int MAX_CYLINDER_FILL_SEGMENTS = 16;
    private static final int MAX_UV_SPHERE_SEGMENTS = 128;
    private static final int MAX_UV_SPHERE_RINGS = 64;

    private GeometryDebugMeshFactory() {
    }

    public static List<GeometryDebugElement> buildMeshes(String sourceKey,
                                                      String graphId,
                                                      String localId,
                                                      GeometryValue geometry,
                                                      int limit,
                                                      Vec3 translation) {
        if (geometry == null || geometry.isEmpty() || limit <= 0) {
            return List.of();
        }

        GeometryValue.Primitive[] primitives = geometry.primitives();
        List<GeometryDebugElement> meshes = new ArrayList<>(Math.min(limit, primitives.length));
        Vec3 safeTranslation = translation != null ? translation : Vec3.ZERO;
        String safeLocalId = localId != null && !localId.isBlank() ? localId : "geometry";
        for (int i = 0; i < primitives.length && meshes.size() < limit; i++) {
            String id = sourceKey + ":" + safeLocalId + (primitives.length > 1 ? ":" + i : "");
            meshes.add(buildPrimitiveMesh(id, graphId, primitives[i], safeTranslation));
        }
        return meshes;
    }

    public static GeometryDebugElement buildPrimitiveMesh(String id,
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
                    clampInt(primitive.verticesZ(), 2, MAX_CUBE_AXIS_VERTICES),
                    DebugRenderChannel.GEOMETRY.color()
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
                    primitive.fillType(),
                    DebugRenderChannel.GEOMETRY.color()
            );
            case UV_SPHERE -> buildUvSphereMesh(
                    id,
                    graphId,
                    center,
                    clampInt(primitive.sphereSegments(), 3, MAX_UV_SPHERE_SEGMENTS),
                    clampInt(primitive.sphereRings(), 2, MAX_UV_SPHERE_RINGS),
                    (float) Math.max(0.001D, size.x * 0.5D),
                    DebugRenderChannel.GEOMETRY.color()
            );
        };
    }

    public static GeometryDebugElement buildShapeMesh(DebugRenderShape shape) {
        Vec3 center = shape.center() != null ? shape.center() : Vec3.ZERO;
        Vec3 size = sanitizeSize(shape.size() != null ? shape.size() : new Vec3(1.0D, 1.0D, 1.0D));
        String shapeType = shape.shape() != null ? shape.shape() : "box";
        GeometryDebugElement mesh = switch (shapeType) {
            case "sphere" -> buildPerfectShape(
                    shape, GeometryDebugType.PERFECT_SPHERE, center, size
            );
            case "cylinder" -> buildPerfectShape(
                    shape, GeometryDebugType.PERFECT_CYLINDER, center, size
            );
            default -> buildCubeMesh(
                    shape.id(), shape.graphId(), center, size,
                    2, 2, 2,
                    shape.color()
            );
        };
        return mesh.type() == GeometryDebugType.MESH ? rotateVertices(mesh, shape.rotation()) : mesh;
    }

    private static GeometryDebugElement buildPerfectShape(DebugRenderShape shape,
                                                          GeometryDebugType type,
                                                          Vec3 center,
                                                          Vec3 size) {
        return new GeometryDebugElement(
                shape.id(), shape.graphId(), type, shape.color(), false,
                center, size, shape.rotation(),
                new float[0], new int[0], new int[0]
        );
    }

    private static GeometryDebugElement buildCubeMesh(String id,
                                                   String graphId,
                                                   Vec3 center,
                                                   Vec3 size,
                                                   int verticesX,
                                                   int verticesY,
                                                   int verticesZ,
                                                   int color) {
        MeshBuilder builder = new MeshBuilder(color);

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

    private static GeometryDebugElement buildCylinderMesh(String id,
                                                       String graphId,
                                                       Vec3 center,
                                                       int radialVertices,
                                                       int sideSegments,
                                                       int fillSegments,
                                                       float radius,
                                                       float depth,
                                                       GeometryValue.CylinderFillType fillType,
                                                       int color) {
        MeshBuilder builder = new MeshBuilder(color);

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

    private static GeometryDebugElement buildUvSphereMesh(String id,
                                                       String graphId,
                                                       Vec3 center,
                                                       int segments,
                                                       int rings,
                                                       float radius,
                                                       int color) {
        MeshBuilder builder = new MeshBuilder(color);

        for (int ring = 0; ring < rings; ring++) {
            for (int segment = 0; segment < segments; segment++) {
                int next = (segment + 1) % segments;
                if (ring == 0) {
                    int top = sphereVertex(builder, segments, rings, radius, 0, segment);
                    int b = sphereVertex(builder, segments, rings, radius, 1, segment);
                    int c = sphereVertex(builder, segments, rings, radius, 1, next);
                    builder.addFaceWithEdges(top, b, c, top);
                } else if (ring == rings - 1) {
                    int a = sphereVertex(builder, segments, rings, radius, ring, segment);
                    int b = sphereVertex(builder, segments, rings, radius, rings, segment);
                    int c = sphereVertex(builder, segments, rings, radius, ring, next);
                    builder.addFaceWithEdges(a, b, c, a);
                } else {
                    int a = sphereVertex(builder, segments, rings, radius, ring, segment);
                    int b = sphereVertex(builder, segments, rings, radius, ring + 1, segment);
                    int c = sphereVertex(builder, segments, rings, radius, ring + 1, next);
                    int d = sphereVertex(builder, segments, rings, radius, ring, next);
                    builder.addFaceWithEdges(a, b, c, d);
                }
            }
        }

        return builder.build(id, graphId, center);
    }

    private static int sphereVertex(MeshBuilder builder,
                                    int segments,
                                    int rings,
                                    float radius,
                                    int ring,
                                    int segment) {
        if (ring <= 0) {
            return builder.addVertex(key(5, 0, 0, 0), 0.0f, radius, 0.0f);
        }
        if (ring >= rings) {
            return builder.addVertex(key(5, rings, 0, 0), 0.0f, -radius, 0.0f);
        }

        float phi = (float) (Math.PI * ring / rings);
        float theta = (float) (Math.PI * 2.0D * segment / segments);
        float ringRadius = (float) Math.sin(phi) * radius;
        return builder.addVertex(
                key(5, ring, segment, 0),
                (float) Math.cos(theta) * ringRadius,
                (float) Math.cos(phi) * radius,
                (float) Math.sin(theta) * ringRadius
        );
    }

    private static float axis(float center, float size, int index, int count) {
        if (count <= 1) {
            return center;
        }
        return center - size * 0.5f + size * index / (count - 1);
    }

    private static GeometryDebugElement rotateVertices(GeometryDebugElement mesh, Vec3 rotation) {
        if (rotation == null || rotation.equals(Vec3.ZERO)) {
            return mesh;
        }
        Quaternionf quaternion = new Quaternionf().rotationYXZ(
                (float) Math.toRadians(rotation.y),
                (float) Math.toRadians(rotation.x),
                (float) Math.toRadians(rotation.z)
        );
        float[] rotated = mesh.vertices().clone();
        Vector3f vertex = new Vector3f();
        for (int i = 0; i + 2 < rotated.length; i += 3) {
            vertex.set(rotated[i], rotated[i + 1], rotated[i + 2]);
            quaternion.transform(vertex);
            rotated[i] = vertex.x;
            rotated[i + 1] = vertex.y;
            rotated[i + 2] = vertex.z;
        }
        return new GeometryDebugElement(
                mesh.id(), mesh.graphId(), mesh.type(), mesh.color(), mesh.showPoints(),
                mesh.center(), mesh.size(), mesh.rotation(),
                rotated, mesh.edges(), mesh.faces()
        );
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
        private final int color;
        private final FloatArrayList vertices = new FloatArrayList();
        private final IntArrayList edges = new IntArrayList();
        private final IntArrayList faces = new IntArrayList();
        private final Long2IntOpenHashMap vertexByKey = new Long2IntOpenHashMap();
        private final LongOpenHashSet edgeKeys = new LongOpenHashSet();

        private MeshBuilder(int color) {
            this.color = color;
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

        private GeometryDebugElement build(String id, String graphId, Vec3 center) {
            return new GeometryDebugElement(
                    id, graphId, GeometryDebugType.MESH, color, true,
                    center, Vec3.ZERO, Vec3.ZERO,
                    vertices.toFloatArray(), edges.toIntArray(), faces.toIntArray()
            );
        }
    }
}
