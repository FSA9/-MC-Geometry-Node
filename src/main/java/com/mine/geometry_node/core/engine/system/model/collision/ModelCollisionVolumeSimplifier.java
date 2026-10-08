package com.mine.geometry_node.core.engine.system.model.collision;

import com.mine.geometry_node.core.engine.system.model.io.ModelCancellation;
import org.joml.Vector3d;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.util.meshoptimizer.MeshOptimizer.*;

/** Native simplification only proposes indices; double-precision surface proofs decide acceptance. */
final class ModelCollisionVolumeSimplifier {
    private static final int MAX_TRIANGLES = 20_000;
    private ModelCollisionVolumeSimplifier() {}

    static int[] simplify(List<Vector3d> points, int[] triangles, double error,
                          ModelCancellation token, long deadline) throws IOException {
        token.check();
        if (!Double.isFinite(error) || error <= 0 || triangles.length <= 36
                || triangles.length > MAX_TRIANGLES * 3 || points.size() > MAX_TRIANGLES * 3)
            return triangles;
        if (!closed(points, triangles)) return triangles;
        FloatBuffer positions = null;
        IntBuffer source = null, destination = null;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ModelCollisionOptimizationBudget.check(token, deadline);
            Vector3d origin = points.get(triangles[0]);
            positions = MemoryUtil.memAllocFloat(Math.multiplyExact(points.size(), 3));
            for (int i = 0; i < points.size(); i++) {
                if ((i & 1023) == 0) ModelCollisionOptimizationBudget.check(token, deadline);
                Vector3d p = points.get(i);
                float x = (float) (p.x - origin.x), y = (float) (p.y - origin.y), z = (float) (p.z - origin.z);
                if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) return triangles;
                positions.put(x).put(y).put(z);
            }
            positions.flip();
            source = MemoryUtil.memAllocInt(triangles.length).put(triangles).flip();
            destination = MemoryUtil.memAllocInt(triangles.length);
            FloatBuffer reportedError = stack.mallocFloat(1);
            // Meshoptimizer's quadric error is a proposal metric, not a Hausdorff certificate.
            // Retry more conservative proposals from the original mesh, never accumulate collapses.
            for (double factor : new double[]{1, .75, .5, .25}) {
                ModelCollisionOptimizationBudget.check(token, deadline);
                int count = Math.toIntExact(meshopt_simplify(destination, source, positions, points.size(), 12,
                        12, (float) (error * factor), meshopt_SimplifyLockBorder | meshopt_SimplifyErrorAbsolute, reportedError));
                ModelCollisionOptimizationBudget.check(token, deadline);
                if (count < 12 || count >= triangles.length || count % 3 != 0) continue;
                int[] candidate = new int[count];
                destination.get(0, candidate);
                if (!closed(points, candidate) || !volumeChangeBounded(points, triangles, candidate, error)) continue;
                if (ModelCollisionSurfaceCertificate.covers(points, triangles, points, candidate, error, token, deadline)
                        && ModelCollisionSurfaceCertificate.covers(points, candidate, points, triangles, error, token, deadline))
                    return candidate;
            }
            return triangles;
        } catch (ModelCollisionOptimizationBudget.Exhausted | LinkageError unavailable) {
            token.check();
            return triangles;
        } catch (IOException failure) {
            token.check();
            if (System.nanoTime() > deadline) return triangles;
            throw failure;
        } finally {
            MemoryUtil.memFree(destination);
            MemoryUtil.memFree(source);
            MemoryUtil.memFree(positions);
        }
    }

    private static boolean closed(List<Vector3d> points, int[] triangles) {
        if (triangles.length < 12 || triangles.length % 3 != 0) return false;
        Map<Long, Edge> edges = new HashMap<>();
        int[] parent = new int[triangles.length / 3];
        java.util.Arrays.setAll(parent, i -> i);
        for (int index : triangles) if (index < 0 || index >= points.size()) return false;
        Vector3d origin = points.get(triangles[0]);
        double volume = 0;
        for (int i = 0; i < triangles.length; i += 3) {
            int ia = triangles[i], ib = triangles[i + 1], ic = triangles[i + 2];
            if (ia == ib || ib == ic || ia == ic) return false;
            Vector3d a = new Vector3d(points.get(ia)).sub(origin),
                    b = new Vector3d(points.get(ib)).sub(origin), c = new Vector3d(points.get(ic)).sub(origin);
            if (!a.isFinite() || !b.isFinite() || !c.isFinite()
                    || new Vector3d(b).sub(a).cross(new Vector3d(c).sub(a)).lengthSquared() < 1e-24)
                return false;
            volume += a.dot(new Vector3d(b).cross(c));
            for (int j = 0; j < 3; j++) {
                int from = triangles[i + j], to = triangles[i + (j + 1) % 3];
                long key = ((long) Math.min(from, to) << 32) | Math.max(from, to);
                Edge edge = edges.computeIfAbsent(key, ignored -> new Edge());
                if (++edge.count > 2) return false;
                if (edge.count == 1) edge.triangle = i / 3;
                else parent[root(parent, i / 3)] = root(parent, edge.triangle);
                edge.direction += from < to ? 1 : -1;
            }
        }
        // Surface distance alone cannot preserve an inner cavity thinner than the error bound.
        // Restrict simplification to one connected shell; nested shell groups remain untouched.
        for (int i = 1; i < parent.length; i++) if (root(parent, i) != root(parent, 0)) return false;
        return volume > 1e-12 && Double.isFinite(volume)
                && edges.values().stream().allMatch(edge -> edge.count == 2 && edge.direction == 0);
    }

    private static int root(int[] parent, int i) {
        while (parent[i] != i) { parent[i] = parent[parent[i]]; i = parent[i]; }
        return i;
    }

    /** Additional rejection guard, not a general proof of volume equivalence from surface distance. */
    static boolean volumeChangeBounded(List<Vector3d> points, int[] source, int[] candidate, double error) {
        Measure original = measure(points, source), reduced = measure(points, candidate);
        return Double.isFinite(original.area) && Double.isFinite(original.volume) && Double.isFinite(reduced.volume)
                && Math.abs(reduced.volume - original.volume) <= original.area * error;
    }

    private static Measure measure(List<Vector3d> points, int[] triangles) {
        Vector3d origin = points.get(triangles[0]);
        double area = 0, volume = 0;
        for (int i = 0; i < triangles.length; i += 3) {
            Vector3d a = new Vector3d(points.get(triangles[i])).sub(origin),
                    b = new Vector3d(points.get(triangles[i + 1])).sub(origin),
                    c = new Vector3d(points.get(triangles[i + 2])).sub(origin);
            area += new Vector3d(b).sub(a).cross(new Vector3d(c).sub(a)).length() * .5;
            volume += a.dot(b.cross(c));
        }
        return new Measure(area, volume / 6);
    }
    private record Measure(double area, double volume) {}
    private static final class Edge { int count, direction, triangle; }
}
