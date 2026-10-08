package com.mine.geometry_node.core.engine.graph.debug;

import com.mine.geometry_node.core.engine.system.model.collision.ModelCollisionMesh;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;

/** Draw convex facets and their boundaries, not the internal triangulation used by physics. */
final class ModelCollisionDebugGeometry {
    record Surface(double[] vertices, int[] edges, int[] faces) {}
    private record Point(int index, double x, double y) {}

    static Surface of(ModelCollisionMesh.Part part) {
        double[] vertices = part.vertices(), planes = part.planes();
        var edges = new LinkedHashSet<Long>();
        var facets = new LinkedHashSet<java.util.List<Integer>>();
        var faces = new ArrayList<Integer>();
        for (int p = 0; p < planes.length; p += 4) {
            double nx = planes[p], ny = planes[p + 1], nz = planes[p + 2];
            double ux = Math.abs(nx) < 0.8 ? 0 : -ny;
            double uy = Math.abs(nx) < 0.8 ? -nz : nx;
            double uz = Math.abs(nx) < 0.8 ? ny : 0;
            double length = Math.sqrt(ux * ux + uy * uy + uz * uz);
            ux /= length; uy /= length; uz /= length;
            double vx = ny * uz - nz * uy, vy = nz * ux - nx * uz, vz = nx * uy - ny * ux;
            var points = new ArrayList<Point>();
            for (int i = 0; i < vertices.length; i += 3) {
                double x = vertices[i], y = vertices[i + 1], z = vertices[i + 2];
                double distance = nx * x + ny * y + nz * z;
                double tolerance = 1e-7 + 8 * Math.ulp(Math.max(Math.abs(distance), Math.abs(planes[p + 3])));
                if (Math.abs(distance - planes[p + 3]) <= tolerance)
                    points.add(new Point(i / 3, ux * x + uy * y + uz * z, vx * x + vy * y + vz * z));
            }
            points.sort(Comparator.comparingDouble(Point::x).thenComparingDouble(Point::y));
            var hull = new ArrayList<Point>();
            for (Point point : points) {
                while (hull.size() >= 2 && cross(hull.get(hull.size() - 2), hull.getLast(), point) <= 1e-12)
                    hull.removeLast();
                hull.add(point);
            }
            int lower = hull.size();
            for (int i = points.size() - 2; i >= 0; i--) {
                Point point = points.get(i);
                while (hull.size() > lower && cross(hull.get(hull.size() - 2), hull.getLast(), point) <= 1e-12)
                    hull.removeLast();
                hull.add(point);
            }
            if (!hull.isEmpty()) hull.removeLast();
            if (hull.size() < 3 || !facets.add(hull.stream().map(Point::index).sorted().toList())) continue;
            for (int i = 0; i < hull.size(); i++) {
                int a = hull.get(i).index(), b = hull.get((i + 1) % hull.size()).index();
                edges.add(((long) Math.min(a, b) << 32) | Math.max(a, b));
            }
            // Pair adjacent fan triangles into quads; only the perimeter receives lines.
            for (int i = 1; i + 1 < hull.size(); i += 2) {
                faces.add(hull.getFirst().index()); faces.add(hull.get(i).index());
                faces.add(hull.get(i + 1).index()); faces.add(hull.get(Math.min(i + 2, hull.size() - 1)).index());
            }
        }
        int[] lines = new int[edges.size() * 2];
        int next = 0;
        for (long edge : edges) { lines[next++] = (int) (edge >>> 32); lines[next++] = (int) edge; }
        return new Surface(vertices, lines, faces.stream().mapToInt(Integer::intValue).toArray());
    }

    private static double cross(Point a, Point b, Point c) {
        return (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x);
    }
}
