package com.mine.geometry_node.core.engine.system.model.collision;

import com.mine.geometry_node.core.engine.system.model.io.ModelCancellation;

import org.joml.Vector3d;

import java.io.IOException;
import java.util.*;

/** Reuses the original cap triangulation to decompose a closed extrusion without sealing holes. */
final class ModelCollisionExtrusions {
    private static final double EPS = 1e-7;

    private record Point(int index, double x, double y) {}

    private record Edge(int a, int b) {
        static Edge of(int a, int b) {
            return a < b ? new Edge(a, b) : new Edge(b, a);
        }
    }

    private record Pair(int a, int b) {}

    private static final class Polygon {
        int parent;
        List<Point> points;

        Polygon(int id, List<Point> points) {
            parent = id;
            this.points = points;
        }
    }

    private ModelCollisionExtrusions() {}

    static List<ModelCollisionMesh.Part> decompose(
            List<Vector3d> points,
            int[] triangles,
            double fittingError,
            ModelCancellation token,
            long deadline)
            throws IOException {
        double tolerance = Math.min(1e-5, fittingError * .1);
        List<Vector3d> axes =
                new ArrayList<>(
                        List.of(
                                new Vector3d(1, 0, 0),
                                new Vector3d(0, 1, 0),
                                new Vector3d(0, 0, 1)));
        for (int t = 0; t < triangles.length; t += 3) {
            Vector3d a = points.get(triangles[t]),
                    normal =
                            new Vector3d(points.get(triangles[t + 1]))
                                    .sub(a)
                                    .cross(new Vector3d(points.get(triangles[t + 2])).sub(a));
            if (normal.lengthSquared() < 1e-24) continue;
            normal.normalize();
            boolean known = false;
            for (Vector3d axis : axes)
                if (Math.abs(axis.dot(normal)) > 1 - 1e-10) {
                    known = true;
                    break;
                }
            if (!known) axes.add(normal);
            if (axes.size() > 256) break;
        }
        for (Vector3d axis : axes) {
            ModelCollisionOptimizationBudget.check(token, deadline);
            double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
            for (Vector3d point : points) {
                double d = axis.dot(point);
                min = Math.min(min, d);
                max = Math.max(max, d);
            }
            if (max - min < EPS) continue;
            boolean extrusion = true;
            for (Vector3d point : points) {
                double d = axis.dot(point);
                if (Math.min(Math.abs(d - min), Math.abs(d - max)) > tolerance) {
                    extrusion = false;
                    break;
                }
            }
            if (!extrusion) continue;
            Vector3d
                    u =
                            (Math.abs(axis.x) < .8 ? new Vector3d(1, 0, 0) : new Vector3d(0, 1, 0))
                                    .cross(axis)
                                    .normalize(),
                    v = new Vector3d(axis).cross(u);
            List<Polygon> polygons = new ArrayList<>();
            Map<Edge, Integer> owners = new HashMap<>();
            List<Pair> adjacency = new ArrayList<>();
            for (int t = 0; t < triangles.length; t += 3) {
                Vector3d a = points.get(triangles[t]),
                        b = points.get(triangles[t + 1]),
                        c = points.get(triangles[t + 2]);
                boolean low =
                        Math.abs(axis.dot(a) - min) <= tolerance
                                && Math.abs(axis.dot(b) - min) <= tolerance
                                && Math.abs(axis.dot(c) - min) <= tolerance;
                boolean high =
                        Math.abs(axis.dot(a) - max) <= tolerance
                                && Math.abs(axis.dot(b) - max) <= tolerance
                                && Math.abs(axis.dot(c) - max) <= tolerance;
                if (!low && !high) {
                    Vector3d normal = new Vector3d(b).sub(a).cross(new Vector3d(c).sub(a));
                    double edge = Math.max(a.distance(b), Math.max(b.distance(c), c.distance(a)));
                    if (normal.lengthSquared() > 1e-24
                            && Math.abs(normal.normalize().dot(axis)) * edge > tolerance) {
                        extrusion = false;
                        break;
                    }
                    continue;
                }
                Vector3d faceNormal = new Vector3d(b).sub(a).cross(new Vector3d(c).sub(a));
                if (low && faceNormal.dot(axis) >= 0 || high && faceNormal.dot(axis) <= 0) {
                    extrusion = false;
                    break;
                }
                if (!low) continue;
                List<Point> projected = new ArrayList<>();
                for (int j = 0; j < 3; j++) {
                    int index = triangles[t + j];
                    Vector3d p = points.get(index);
                    projected.add(new Point(index, u.dot(p), v.dot(p)));
                }
                projected = hull(projected);
                if (projected.size() < 3) continue;
                int id = polygons.size();
                polygons.add(new Polygon(id, projected));
                for (int j = 0; j < 3; j++) {
                    Edge edge = Edge.of(triangles[t + j], triangles[t + (j + 1) % 3]);
                    Integer other = owners.putIfAbsent(edge, id);
                    if (other != null) adjacency.add(new Pair(other, id));
                }
            }
            if (!extrusion || polygons.isEmpty()) continue;
            for (int pass = 0; pass < 4; pass++) {
                int merged = 0;
                for (Pair pair : adjacency) {
                    ModelCollisionOptimizationBudget.check(token, deadline);
                    int a = root(polygons, pair.a), b = root(polygons, pair.b);
                    if (a == b) continue;
                    List<Point> left = polygons.get(a).points,
                            right = polygons.get(b).points,
                            all = new ArrayList<>(left);
                    all.addAll(right);
                    List<Point> combined = hull(all);
                    if (combined.size() > 60) continue;
                    double sum = area(left) + area(right), difference = area(combined) - sum;
                    if (Math.abs(difference) > 1e-10 * Math.max(1, sum)) continue;
                    polygons.get(a).points = combined;
                    polygons.get(b).parent = a;
                    merged++;
                }
                if (merged == 0) break;
            }
            List<ModelCollisionMesh.Part> solids = new ArrayList<>();
            for (int i = 0; i < polygons.size(); i++)
                if (root(polygons, i) == i) {
                    List<Vector3d> prism = new ArrayList<>();
                    for (Point point : polygons.get(i).points) {
                        prism.add(new Vector3d(u).mul(point.x).fma(point.y, v).fma(min, axis));
                        prism.add(new Vector3d(u).mul(point.x).fma(point.y, v).fma(max, axis));
                    }
                    var body = ModelCollisionSolids.hull(prism);
                    if (body == null)
                        throw new IOException(
                                "closed extrusion contains a numerically degenerate cap");
                    solids.add(body);
                }
            return solids;
        }
        return null;
    }

    private static int root(List<Polygon> polygons, int i) {
        while (polygons.get(i).parent != i) {
            polygons.get(i).parent = polygons.get(polygons.get(i).parent).parent;
            i = polygons.get(i).parent;
        }
        return i;
    }

    private static double area(List<Point> p) {
        double result = 0;
        Point origin = p.getFirst();
        for (int i = 1; i < p.size() - 1; i++) result += cross(origin, p.get(i), p.get(i + 1));
        return result * .5;
    }

    private static double cross(Point a, Point b, Point c) {
        return (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x);
    }

    private static List<Point> hull(List<Point> input) {
        List<Point> p = new ArrayList<>(input);
        p.sort(Comparator.comparingDouble(Point::x).thenComparingDouble(Point::y));
        List<Point> h = new ArrayList<>();
        for (Point v : p) {
            while (h.size() >= 2 && cross(h.get(h.size() - 2), h.getLast(), v) <= 1e-12)
                h.removeLast();
            h.add(v);
        }
        int lower = h.size();
        for (int i = p.size() - 2; i >= 0; i--) {
            Point v = p.get(i);
            while (h.size() > lower && cross(h.get(h.size() - 2), h.getLast(), v) <= 1e-12)
                h.removeLast();
            h.add(v);
        }
        if (h.size() > 1) h.removeLast();
        return h;
    }
}
