package com.mine.geometry_node.core.engine.system.model.collision.generation;

import com.mine.geometry_node.core.engine.system.model.io.ModelCancellation;

import org.joml.Intersectiond;
import org.joml.Vector3d;

import java.io.IOException;
import java.util.*;

/** Bounded Hausdorff surface certificate. An inconclusive budget result never accepts a hull. */
final class ModelCollisionSurfaceCertificate {
    private static final int MAX_VISITS = 16000, MAX_DISTANCE_TESTS = 300000;

    private record Triangle(Vector3d a, Vector3d b, Vector3d c, int depth) {}

    private record Box(double x0, double y0, double z0, double x1, double y1, double z1) {
        double distanceSquared(Vector3d p) {
            double x = Math.max(0, Math.max(x0 - p.x, p.x - x1)),
                    y = Math.max(0, Math.max(y0 - p.y, p.y - y1)),
                    z = Math.max(0, Math.max(z0 - p.z, p.z - z1));
            return x * x + y * y + z * z;
        }
    }

    private record Node(Box box, int start, int end, Node left, Node right) {}

    private final List<Vector3d> vertices;
    private final int[] triangles;
    private final Integer[] order;
    private final Node root;
    private int visits, distanceTests;
    private final ModelCancellation token;
    private final long deadline;

    private ModelCollisionSurfaceCertificate(
            List<Vector3d> vertices, int[] triangles, ModelCancellation token, long deadline) {
        this.vertices = vertices;
        this.triangles = triangles;
        this.token = token;
        this.deadline = deadline;
        order = new Integer[triangles.length / 3];
        Arrays.setAll(order, i -> i);
        root = build(0, order.length);
    }

    static boolean covers(
            List<Vector3d> source,
            int[] sourceTriangles,
            List<Vector3d> target,
            int[] targetTriangles,
            double error,
            ModelCancellation token,
            long deadline)
            throws IOException {
        if (sourceTriangles.length == 0 || targetTriangles.length == 0) return false;
        var index = new ModelCollisionSurfaceCertificate(target, targetTriangles, token, deadline);
        for (int i = 0; i < sourceTriangles.length; i += 3) {
            if (!index.prove(
                    new Triangle(
                            source.get(sourceTriangles[i]),
                            source.get(sourceTriangles[i + 1]),
                            source.get(sourceTriangles[i + 2]),
                            0),
                    error)) return false;
        }
        return true;
    }

    private boolean prove(Triangle initial, double error) throws IOException {
        ArrayDeque<Triangle> work = new ArrayDeque<>();
        work.push(initial);
        while (!work.isEmpty()) {
            if (++visits > MAX_VISITS || distanceTests > MAX_DISTANCE_TESTS) return false;
            if ((visits & 127) == 0) {
                ModelCollisionOptimizationBudget.check(token, deadline);
            }
            Triangle tri = work.pop();
            // Distance to a fixed convex triangle is convex. If one target
            // triangle covers all three vertices, it covers the entire source triangle.
            if (singleCover(root, tri, error * error)) continue;
            Vector3d center = new Vector3d(tri.a).add(tri.b).add(tri.c).div(3);
            double distance = Math.sqrt(nearest(root, center, Double.POSITIVE_INFINITY));
            if (distance > error) return false;
            double radius =
                    Math.sqrt(
                            Math.max(
                                    center.distanceSquared(tri.a),
                                    Math.max(
                                            center.distanceSquared(tri.b),
                                            center.distanceSquared(tri.c))));
            if (distance + radius <= error) continue;
            if (tri.depth >= 18) return false;
            Vector3d ab = new Vector3d(tri.a).lerp(tri.b, .5),
                    bc = new Vector3d(tri.b).lerp(tri.c, .5),
                    ca = new Vector3d(tri.c).lerp(tri.a, .5);
            work.push(new Triangle(tri.a, ab, ca, tri.depth + 1));
            work.push(new Triangle(ab, tri.b, bc, tri.depth + 1));
            work.push(new Triangle(ca, bc, tri.c, tri.depth + 1));
            work.push(new Triangle(ab, bc, ca, tri.depth + 1));
        }
        return true;
    }

    private boolean singleCover(Node node, Triangle source, double errorSquared) {
        if (node.box.distanceSquared(source.a) > errorSquared
                || node.box.distanceSquared(source.b) > errorSquared
                || node.box.distanceSquared(source.c) > errorSquared) return false;
        if (node.left != null)
            return singleCover(node.left, source, errorSquared)
                    || singleCover(node.right, source, errorSquared);
        for (int i = node.start; i < node.end; i++) {
            int t = order[i] * 3;
            if (distance(source.a, t) <= errorSquared
                    && distance(source.b, t) <= errorSquared
                    && distance(source.c, t) <= errorSquared) return true;
            if (distanceTests > MAX_DISTANCE_TESTS) return false;
        }
        return false;
    }

    private double nearest(Node node, Vector3d point, double best) {
        if (node.box.distanceSquared(point) >= best || distanceTests > MAX_DISTANCE_TESTS)
            return best;
        if (node.left != null) {
            Node first = node.left, second = node.right;
            if (first.box.distanceSquared(point) > second.box.distanceSquared(point)) {
                first = node.right;
                second = node.left;
            }
            return nearest(second, point, nearest(first, point, best));
        }
        for (int i = node.start; i < node.end; i++)
            best = Math.min(best, distance(point, order[i] * 3));
        return best;
    }

    private double distance(Vector3d point, int triangle) {
        distanceTests++;
        Vector3d nearest = new Vector3d();
        Intersectiond.findClosestPointOnTriangle(
                vertices.get(triangles[triangle]),
                vertices.get(triangles[triangle + 1]),
                vertices.get(triangles[triangle + 2]),
                point,
                nearest);
        return nearest.distanceSquared(point);
    }

    private Node build(int start, int end) {
        double x0 = Double.POSITIVE_INFINITY,
                y0 = x0,
                z0 = x0,
                x1 = Double.NEGATIVE_INFINITY,
                y1 = x1,
                z1 = x1;
        for (int i = start; i < end; i++)
            for (int j = 0; j < 3; j++) {
                Vector3d p = vertices.get(triangles[order[i] * 3 + j]);
                x0 = Math.min(x0, p.x);
                y0 = Math.min(y0, p.y);
                z0 = Math.min(z0, p.z);
                x1 = Math.max(x1, p.x);
                y1 = Math.max(y1, p.y);
                z1 = Math.max(z1, p.z);
            }
        Box box = new Box(x0, y0, z0, x1, y1, z1);
        if (end - start <= 8) return new Node(box, start, end, null, null);
        double dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        int axis = dx >= dy && dx >= dz ? 0 : dy >= dz ? 1 : 2;
        Arrays.sort(
                order,
                start,
                end,
                Comparator.comparingDouble(
                        t -> {
                            double center = 0;
                            for (int j = 0; j < 3; j++)
                                center += vertices.get(triangles[t * 3 + j]).get(axis);
                            return center;
                        }));
        int mid = (start + end) >>> 1;
        return new Node(box, start, end, build(start, mid), build(mid, end));
    }
}
