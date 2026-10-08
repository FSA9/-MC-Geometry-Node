package com.mine.geometry_node.core.engine.system.model.collision;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** A compound of closed convex volumes, with outward planes and triangulated boundary faces. */
public record ModelCollisionMesh(
        List<Part> parts,
        int sourceTriangles,
        int skippedTriangles,
        int incompleteObjects,
        List<String> generationIssues) {
    public ModelCollisionMesh(List<Part> parts, int sourceTriangles) {
        this(parts, sourceTriangles, 0, 0);
    }

    public ModelCollisionMesh(
            List<Part> parts, int sourceTriangles, int skippedTriangles, int incompleteObjects) {
        this(parts, sourceTriangles, skippedTriangles, incompleteObjects, List.of());
    }

    public ModelCollisionMesh {
        parts = List.copyOf(parts);
        generationIssues = List.copyOf(generationIssues);
        if (sourceTriangles < 0) throw new IllegalArgumentException("negative triangle count");
        if (skippedTriangles < 0
                || skippedTriangles > sourceTriangles
                || incompleteObjects < 0
                || incompleteObjects > skippedTriangles)
            throw new IllegalArgumentException("invalid partial collision counts");
    }

    public static final class Part {
        private final double[] vertices, planes;
        private final int[] triangles;
        private final double minX, minY, minZ, maxX, maxY, maxZ;

        public Part(double[] vertices, int[] triangles, double[] planes) {
            if (vertices.length < 12
                    || vertices.length % 3 != 0
                    || vertices.length > 384
                    || triangles.length < 12
                    || triangles.length % 3 != 0
                    || triangles.length > 768
                    || planes.length < 16
                    || planes.length % 4 != 0
                    || planes.length > 512)
                throw new IllegalArgumentException("invalid convex collision part dimensions");
            for (double value : vertices)
                if (!Double.isFinite(value))
                    throw new IllegalArgumentException("nonfinite collision vertex");
            for (double value : planes)
                if (!Double.isFinite(value))
                    throw new IllegalArgumentException("nonfinite collision plane");
            for (int index : triangles)
                if (index < 0 || index >= vertices.length / 3)
                    throw new IllegalArgumentException("invalid collision index");
            validateClosedBoundary(vertices, triangles);
            for (int p = 0; p < planes.length; p += 4) {
                double length =
                        Math.sqrt(
                                planes[p] * planes[p]
                                        + planes[p + 1] * planes[p + 1]
                                        + planes[p + 2] * planes[p + 2]);
                if (Math.abs(length - 1) > 1e-6)
                    throw new IllegalArgumentException("unnormalized collision plane");
                for (int v = 0; v < vertices.length; v += 3)
                    if (planes[p] * vertices[v]
                                    + planes[p + 1] * vertices[v + 1]
                                    + planes[p + 2] * vertices[v + 2]
                                    - planes[p + 3]
                            > 1e-6) throw new IllegalArgumentException("nonconvex collision part");
            }
            this.vertices = vertices.clone();
            this.triangles = triangles.clone();
            this.planes = planes.clone();
            double x0 = Double.POSITIVE_INFINITY,
                    y0 = x0,
                    z0 = x0,
                    x1 = Double.NEGATIVE_INFINITY,
                    y1 = x1,
                    z1 = x1;
            for (int i = 0; i < vertices.length; i += 3) {
                x0 = Math.min(x0, vertices[i]);
                y0 = Math.min(y0, vertices[i + 1]);
                z0 = Math.min(z0, vertices[i + 2]);
                x1 = Math.max(x1, vertices[i]);
                y1 = Math.max(y1, vertices[i + 1]);
                z1 = Math.max(z1, vertices[i + 2]);
            }
            minX = x0;
            minY = y0;
            minZ = z0;
            maxX = x1;
            maxY = y1;
            maxZ = z1;
        }

        public double[] vertices() {
            return vertices.clone();
        }

        public long estimatedGeometryBytes() {
            return vertices.length * 8L + planes.length * 8L + triangles.length * 4L + 112;
        }

        public int[] triangles() {
            return triangles.clone();
        }

        /** Normalized outward planes, inside when dot(normal, point) <= distance. */
        public double[] planes() {
            return planes.clone();
        }

        public double minX() {
            return minX;
        }

        public double minY() {
            return minY;
        }

        public double minZ() {
            return minZ;
        }

        public double maxX() {
            return maxX;
        }

        public double maxY() {
            return maxY;
        }

        public double maxZ() {
            return maxZ;
        }

        public int triangleCount() {
            return triangles.length / 3;
        }

        public int faceCount() {
            return planes.length / 4;
        }

        public int vertexCount() {
            return vertices.length / 3;
        }

        private static void validateClosedBoundary(double[] vertices, int[] triangles) {
            Map<Long, Integer> edges = new HashMap<>();
            double volume = 0;
            double ox = vertices[0], oy = vertices[1], oz = vertices[2];
            for (int t = 0; t < triangles.length; t += 3) {
                for (int j = 0; j < 3; j++) {
                    int a = triangles[t + j], b = triangles[t + (j + 1) % 3];
                    if (a == b)
                        throw new IllegalArgumentException("degenerate collision boundary edge");
                    long key = ((long) Math.min(a, b) << 32) | Math.max(a, b);
                    int flag = a < b ? 1 : 2;
                    int previous = edges.getOrDefault(key, 0);
                    if ((previous & flag) != 0)
                        throw new IllegalArgumentException("nonmanifold collision boundary");
                    edges.put(key, previous | flag);
                }
                int a = triangles[t] * 3, b = triangles[t + 1] * 3, c = triangles[t + 2] * 3;
                double ax = vertices[a] - ox, ay = vertices[a + 1] - oy, az = vertices[a + 2] - oz;
                double bx = vertices[b] - ox, by = vertices[b + 1] - oy, bz = vertices[b + 2] - oz;
                double cx = vertices[c] - ox, cy = vertices[c + 1] - oy, cz = vertices[c + 2] - oz;
                volume +=
                        ax * (by * cz - bz * cy)
                                + ay * (bz * cx - bx * cz)
                                + az * (bx * cy - by * cx);
            }
            if (!(volume > 0)
                    || !Double.isFinite(volume)
                    || edges.values().stream().anyMatch(v -> v != 3))
                throw new IllegalArgumentException(
                        "collision boundary must enclose a positive volume");
        }
    }
}
