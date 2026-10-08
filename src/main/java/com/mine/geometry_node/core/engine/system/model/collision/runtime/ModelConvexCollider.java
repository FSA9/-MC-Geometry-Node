package com.mine.geometry_node.core.engine.system.model.collision.runtime;

import com.mine.geometry_node.core.engine.system.model.collision.ModelCollisionMesh;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

/** Precomputed separating axes for exact translational AABB/convex-polyhedron sweeps. */
public final class ModelConvexCollider {
    private static final double EPSILON = 1.0e-9;
    private final AABB bounds;
    private final double[] axes;

    public ModelConvexCollider(ModelCollisionMesh.Part part) {
        this(part, true);
    }

    ModelConvexCollider(ModelCollisionMesh.Part part, boolean omitFaceDiagonals) {
        double[] vertices = part.vertices();
        int[] triangles = part.triangles();
        List<Vec3> directions = new ArrayList<>();
        addAxis(directions, 1, 0, 0);
        addAxis(directions, 0, 1, 0);
        addAxis(directions, 0, 0, 1);
        Map<Long, Edge> edges = new HashMap<>();
        for (int i = 0; i < triangles.length; i += 3) {
            Vec3 a = vertex(vertices, triangles[i]);
            Vec3 b = vertex(vertices, triangles[i + 1]);
            Vec3 c = vertex(vertices, triangles[i + 2]);
            Vec3 normal = b.subtract(a).cross(c.subtract(a));
            addAxis(directions, normal.x, normal.y, normal.z);
            if (omitFaceDiagonals) {
                addEdge(edges, triangles[i], triangles[i + 1], normal);
                addEdge(edges, triangles[i + 1], triangles[i + 2], normal);
                addEdge(edges, triangles[i + 2], triangles[i], normal);
            } else {
                addEdgeAxes(directions, b.subtract(a));
                addEdgeAxes(directions, c.subtract(b));
                addEdgeAxes(directions, a.subtract(c));
            }
        }
        for (Map.Entry<Long, Edge> entry : edges.entrySet()) {
            if (entry.getValue().coplanar) continue;
            long key = entry.getKey();
            addEdgeAxes(directions, vertex(vertices, (int) key).subtract(vertex(vertices, (int) (key >>> 32))));
        }
        double minX = Double.POSITIVE_INFINITY, minY = minX, minZ = minX;
        double maxX = Double.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;
        for (int i = 0; i < vertices.length; i += 3) {
            minX = Math.min(minX, vertices[i]); maxX = Math.max(maxX, vertices[i]);
            minY = Math.min(minY, vertices[i + 1]); maxY = Math.max(maxY, vertices[i + 1]);
            minZ = Math.min(minZ, vertices[i + 2]); maxZ = Math.max(maxZ, vertices[i + 2]);
        }
        bounds = new AABB(minX, minY, minZ, maxX, maxY, maxZ);
        axes = new double[directions.size() * 5];
        for (int i = 0; i < directions.size(); i++) {
            Vec3 n = directions.get(i);
            double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
            for (int j = 0; j < vertices.length; j += 3) {
                double p = n.x * vertices[j] + n.y * vertices[j + 1] + n.z * vertices[j + 2];
                min = Math.min(min, p); max = Math.max(max, p);
            }
            int base = i * 5;
            axes[base] = n.x; axes[base + 1] = n.y; axes[base + 2] = n.z;
            axes[base + 3] = min; axes[base + 4] = max;
        }
    }

    public AABB bounds() { return bounds; }
    public long estimatedIndexBytes() { return axes.length * 8L + 128; }

    public boolean intersects(AABB box) {
        double cx = (box.minX + box.maxX) * 0.5, cy = (box.minY + box.maxY) * 0.5, cz = (box.minZ + box.maxZ) * 0.5;
        double hx = (box.maxX - box.minX) * 0.5, hy = (box.maxY - box.minY) * 0.5, hz = (box.maxZ - box.minZ) * 0.5;
        for (int i = 0; i < axes.length; i += 5) {
            double nx = axes[i], ny = axes[i + 1], nz = axes[i + 2];
            double center = cx * nx + cy * ny + cz * nz;
            double radius = hx * Math.abs(nx) + hy * Math.abs(ny) + hz * Math.abs(nz);
            if (center + radius <= axes[i + 3] + EPSILON || center - radius >= axes[i + 4] - EPSILON) return false;
        }
        return true;
    }

    public Hit sweep(AABB box, Vec3 movement) {
        var sample = ModelCollisionMetrics.currentMovement();
        if (sample != null) sample.satTests++;
        double cx = (box.minX + box.maxX) * 0.5, cy = (box.minY + box.maxY) * 0.5;
        double cz = (box.minZ + box.maxZ) * 0.5;
        double hx = (box.maxX - box.minX) * 0.5, hy = (box.maxY - box.minY) * 0.5;
        double hz = (box.maxZ - box.minZ) * 0.5;
        double enter = Double.NEGATIVE_INFINITY, exit = 1;
        double normalX = 0, normalY = 0, normalZ = 0;
        for (int i = 0; i < axes.length; i += 5) {
            if (sample != null) sample.axisTests++;
            double nx = axes[i], ny = axes[i + 1], nz = axes[i + 2];
            double center = cx * nx + cy * ny + cz * nz;
            double radius = hx * Math.abs(nx) + hy * Math.abs(ny) + hz * Math.abs(nz);
            double low = axes[i + 3] - center - radius, high = axes[i + 4] - center + radius;
            double speed = movement.x * nx + movement.y * ny + movement.z * nz;
            if (Math.abs(speed) < EPSILON) {
                // Mere contact on a stationary separating axis must permit tangential movement.
                if (low >= -EPSILON || high <= EPSILON) return null;
                continue;
            }
            double a = low / speed, b = high / speed;
            double entry = Math.min(a, b), leave = Math.max(a, b);
            if (entry > enter) {
                enter = entry;
                double sign = speed > 0 ? -1 : 1;
                normalX = nx * sign; normalY = ny * sign; normalZ = nz * sign;
            }
            exit = Math.min(exit, leave);
            if (enter > exit + EPSILON) return null;
        }
        // Existing deep overlaps can be exited; a freshly enabled collider must not imprison entities.
        if (enter < -1.0e-7 || enter > 1 || exit < 0 || normalX == 0 && normalY == 0 && normalZ == 0) return null;
        if (sample != null) sample.hits++;
        return new Hit(Math.max(0, enter), new Vec3(normalX, normalY, normalZ));
    }

    private static Vec3 vertex(double[] vertices, int index) {
        int i = index * 3;
        return new Vec3(vertices[i], vertices[i + 1], vertices[i + 2]);
    }

    private static void addEdgeAxes(List<Vec3> axes, Vec3 edge) {
        addAxis(axes, 0, edge.z, -edge.y);
        addAxis(axes, -edge.z, 0, edge.x);
        addAxis(axes, edge.y, -edge.x, 0);
    }

    private static void addEdge(Map<Long, Edge> edges, int a, int b, Vec3 normal) {
        long key = (long) Math.min(a, b) << 32 | Integer.toUnsignedLong(Math.max(a, b));
        Edge previous = edges.get(key);
        if (previous == null) edges.put(key, new Edge(normal, false));
        else {
            double crossSquared = previous.normal.cross(normal).lengthSqr();
            boolean samePlane = crossSquared <= previous.normal.lengthSqr() * normal.lengthSqr() * 1.0e-24;
            edges.put(key, new Edge(previous.normal, samePlane));
        }
    }

    private static void addAxis(List<Vec3> axes, double x, double y, double z) {
        double length = Math.sqrt(x * x + y * y + z * z);
        if (length < 1.0e-12) return;
        Vec3 n = new Vec3(x / length, y / length, z / length);
        for (Vec3 present : axes) if (Math.abs(n.dot(present)) > 1 - 1.0e-10) return;
        axes.add(n);
    }

    public record Hit(double time, Vec3 normal) {}
    private record Edge(Vec3 normal, boolean coplanar) {}
}
