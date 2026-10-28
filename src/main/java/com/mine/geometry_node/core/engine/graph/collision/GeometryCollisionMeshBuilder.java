package com.mine.geometry_node.core.engine.graph.collision;

import com.mine.geometry_node.core.engine.system.model.collision.ModelCollisionMesh;
import com.mine.geometry_node.core.node.value.geometry.GeometryValue;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

/** Builds closed primitive volumes directly, without model decomposition or convex hull fitting. */
public final class GeometryCollisionMeshBuilder {
    private static final int MAX_PRIMITIVES = 256;
    private static final int MAX_PARTS = 128;
    private static final long MAX_GEOMETRY_BYTES = 512 * 1024;

    private GeometryCollisionMeshBuilder() {}

    /** World translation is applied by the collision index, preserving local-coordinate precision. */
    public static ModelCollisionMesh build(GeometryValue geometry, Vec3 worldRotation,
            Vec3 pivot, Vec3 translation, Vec3 localRotation, Vec3 scale) {
        return build(geometry, new Transform(worldRotation, pivot, translation, localRotation, scale));
    }

    public static ModelCollisionMesh build(GeometryValue geometry, Quaterniond outerRotation,
            Vec3 pivot, Vec3 translation, Vec3 localRotation, Vec3 scale) {
        Vec3 safeScale = finite(scale, new Vec3(1, 1, 1));
        if (Math.abs(safeScale.x) < 1e-6 || Math.abs(safeScale.y) < 1e-6 || Math.abs(safeScale.z) < 1e-6)
            throw new IllegalArgumentException("collision scale must be nonzero");
        return build(geometry, new Transform(new Quaterniond(outerRotation), finite(pivot, Vec3.ZERO),
                finite(translation, Vec3.ZERO), quaternion(localRotation), safeScale));
    }

    /** Authored scenes use XYZ radians; avoid converting through the graph's YXZ degree convention. */
    public static ModelCollisionMesh buildScene(GeometryValue geometry, Vec3 rotation, Vec3 scale) {
        return build(geometry, new Transform(new Quaterniond(), Vec3.ZERO, Vec3.ZERO,
                new Quaterniond().rotationXYZ(rotation.x, rotation.y, rotation.z), scale));
    }

    private static ModelCollisionMesh build(GeometryValue geometry, Transform transform) {
        if (geometry == null || geometry.isEmpty())
            throw new IllegalArgumentException("collision geometry is empty");
        if (geometry.primitiveCount() > MAX_PRIMITIVES)
            throw new IllegalArgumentException("collision geometry exceeds 256 primitives");
        List<ModelCollisionMesh.Part> parts = new ArrayList<>();
        long geometryBytes = 0;
        int triangles = 0;
        for (var primitive : geometry.primitives()) {
            List<Polyhedron> solids = switch (primitive.type()) {
                case CUBE -> List.of(cube(primitive));
                case CYLINDER -> cylinder(primitive);
                case UV_SPHERE -> sphere(primitive);
            };
            for (Polyhedron solid : solids) {
                if (parts.size() >= MAX_PARTS)
                    throw new IllegalArgumentException("collision geometry exceeds 128 convex parts");
                var part = solid.build(transform, primitive.center());
                geometryBytes += part.estimatedGeometryBytes();
                if (geometryBytes > MAX_GEOMETRY_BYTES)
                    throw new IllegalArgumentException("collision geometry exceeds 512 KiB");
                triangles += part.triangleCount();
                parts.add(part);
            }
        }
        return new ModelCollisionMesh(parts, triangles);
    }

    private static Polyhedron cube(GeometryValue.Primitive primitive) {
        Vec3 size = primitive.size();
        Polyhedron mesh = new Polyhedron();
        for (int i = 0; i < 8; i++)
            mesh.vertex((i & 1) == 0 ? -size.x / 2 : size.x / 2,
                    (i & 2) == 0 ? -size.y / 2 : size.y / 2,
                    (i & 4) == 0 ? -size.z / 2 : size.z / 2);
        mesh.face(0, 1, 3, 2);
        mesh.face(4, 6, 7, 5);
        mesh.face(0, 4, 5, 1);
        mesh.face(2, 3, 7, 6);
        mesh.face(0, 2, 6, 4);
        mesh.face(1, 5, 7, 3);
        return mesh;
    }

    private static List<Polyhedron> cylinder(GeometryValue.Primitive primitive) {
        int segments = clamp(primitive.radialVertices(), 3, 96);
        double radius = Math.max(0.001, primitive.size().x / 2);
        double halfHeight = primitive.size().y / 2;
        List<Polyhedron> result = new ArrayList<>();
        // Above 64 sides, split into convex angular sectors to retain the exact silhouette.
        int step = segments <= 64 ? segments : Math.min(62, segments / 2);
        for (int start = 0; start < segments; start += step) {
            int count = Math.min(step, segments - start);
            boolean whole = count == segments;
            boolean needsAxis = !whole && count * 2 != segments;
            int perimeter = whole ? count : count + 1 + (needsAxis ? 1 : 0);
            Polyhedron mesh = new Polyhedron();
            for (int layer = 0; layer < 2; layer++) {
                for (int i = 0; i < (whole ? count : count + 1); i++) {
                    double angle = 2 * Math.PI * (start + i) / segments;
                    mesh.vertex(Math.cos(angle) * radius, layer == 0 ? -halfHeight : halfHeight,
                            Math.sin(angle) * radius);
                }
                if (needsAxis) mesh.vertex(0, layer == 0 ? -halfHeight : halfHeight, 0);
            }
            int[] bottom = new int[perimeter], top = new int[perimeter];
            for (int i = 0; i < perimeter; i++) {
                bottom[i] = i;
                top[i] = perimeter + i;
                int next = (i + 1) % perimeter;
                mesh.face(i, next, perimeter + next, perimeter + i);
            }
            // Fill mode and surface subdivisions are visual properties; collision is always solid.
            mesh.face(bottom);
            mesh.face(top);
            result.add(mesh);
        }
        return result;
    }

    private static List<Polyhedron> sphere(GeometryValue.Primitive primitive) {
        int segments = clamp(primitive.sphereSegments(), 3, 128);
        int rings = clamp(primitive.sphereRings(), 2, 64);
        double radius = Math.max(0.001, primitive.size().x / 2);
        boolean whole = (long) segments * (rings - 1) + 2 <= 128
                && (long) segments * rings <= 128;
        int step = whole ? segments : Math.max(1,
                Math.min(segments / 2, Math.min(126 / (rings - 1) - 1, 126 / rings)));
        List<Polyhedron> result = new ArrayList<>();
        for (int start = 0; start < segments; start += step) {
            int count = Math.min(step, segments - start);
            int columns = whole ? segments : count + 1;
            Polyhedron mesh = new Polyhedron();
            int top = mesh.vertex(0, radius, 0);
            for (int ring = 1; ring < rings; ring++) {
                double phi = Math.PI * ring / rings;
                for (int column = 0; column < columns; column++) {
                    double theta = 2 * Math.PI * (start + column) / segments;
                    mesh.vertex(Math.cos(theta) * Math.sin(phi) * radius,
                            Math.cos(phi) * radius, Math.sin(theta) * Math.sin(phi) * radius);
                }
            }
            int bottom = mesh.vertex(0, -radius, 0);
            for (int column = 0; column < count; column++) {
                int next = (column + 1) % columns;
                mesh.face(top, 1 + column, 1 + next);
                for (int ring = 0; ring < rings - 2; ring++) {
                    int base = 1 + ring * columns;
                    mesh.face(base + column, base + columns + column,
                            base + columns + next, base + next);
                }
                int base = 1 + (rings - 2) * columns;
                mesh.face(base + column, bottom, base + next);
            }
            if (!whole) {
                for (int column : new int[]{0, columns - 1}) {
                    int[] cap = new int[rings + 1];
                    cap[0] = top;
                    for (int ring = 0; ring < rings - 1; ring++)
                        cap[ring + 1] = 1 + ring * columns + column;
                    cap[rings] = bottom;
                    mesh.face(cap);
                }
            }
            result.add(mesh);
        }
        return result;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static Vec3 finite(Vec3 value, Vec3 fallback) {
        Vec3 resolved = value == null ? fallback : value;
        if (!Double.isFinite(resolved.x) || !Double.isFinite(resolved.y)
                || !Double.isFinite(resolved.z))
            throw new IllegalArgumentException("nonfinite collision transform");
        return resolved;
    }

    private static Quaterniond quaternion(Vec3 value) {
        Vec3 rotation = finite(value, Vec3.ZERO);
        return new Quaterniond().rotationYXZ(Math.toRadians(-rotation.y),
                Math.toRadians(rotation.x), Math.toRadians(rotation.z));
    }

    private record Transform(Quaterniond world, Vec3 pivot, Vec3 translation,
            Quaterniond local, Vec3 scale) {
        Transform(Vec3 world, Vec3 pivot, Vec3 translation, Vec3 local, Vec3 scale) {
            this(quaternion(world), finite(pivot, Vec3.ZERO), finite(translation, Vec3.ZERO),
                    quaternion(local), finite(scale, new Vec3(1, 1, 1)));
            if (Math.abs(this.scale.x) < 1e-6 || Math.abs(this.scale.y) < 1e-6
                    || Math.abs(this.scale.z) < 1e-6)
                throw new IllegalArgumentException("collision scale must be nonzero");
        }

        Vector3d apply(Vector3d value, Vec3 center) {
            value.add(center.x, center.y, center.z).sub(pivot.x, pivot.y, pivot.z)
                    .mul(scale.x, scale.y, scale.z);
            local.transform(value);
            value.add(pivot.x + translation.x, pivot.y + translation.y, pivot.z + translation.z);
            return world.transform(value);
        }
    }

    private static final class Polyhedron {
        final List<Vector3d> vertices = new ArrayList<>();
        final List<int[]> faces = new ArrayList<>();

        int vertex(double x, double y, double z) {
            vertices.add(new Vector3d(x, y, z));
            return vertices.size() - 1;
        }

        void face(int... indices) {
            faces.add(indices);
        }

        ModelCollisionMesh.Part build(Transform transform, Vec3 center) {
            finite(center, Vec3.ZERO);
            Vector3d centroid = new Vector3d();
            double[] coordinates = new double[vertices.size() * 3];
            for (int i = 0; i < vertices.size(); i++) {
                Vector3d vertex = transform.apply(vertices.get(i), center);
                centroid.add(vertex);
                coordinates[3 * i] = vertex.x;
                coordinates[3 * i + 1] = vertex.y;
                coordinates[3 * i + 2] = vertex.z;
            }
            centroid.div(vertices.size());
            List<Integer> triangles = new ArrayList<>();
            double[] planes = new double[faces.size() * 4];
            for (int f = 0; f < faces.size(); f++) {
                int[] face = faces.get(f);
                Vector3d a = vertices.get(face[0]);
                Vector3d normal = new Vector3d(vertices.get(face[1])).sub(a)
                        .cross(new Vector3d(vertices.get(face[2])).sub(a)).normalize();
                boolean reverse = normal.dot(new Vector3d(centroid).sub(a)) > 0;
                if (reverse) normal.negate();
                planes[4 * f] = normal.x;
                planes[4 * f + 1] = normal.y;
                planes[4 * f + 2] = normal.z;
                planes[4 * f + 3] = normal.dot(a);
                for (int i = 1; i < face.length - 1; i++) {
                    triangles.add(face[0]);
                    triangles.add(face[reverse ? i + 1 : i]);
                    triangles.add(face[reverse ? i : i + 1]);
                }
            }
            return new ModelCollisionMesh.Part(coordinates,
                    triangles.stream().mapToInt(Integer::intValue).toArray(), planes);
        }
    }
}
