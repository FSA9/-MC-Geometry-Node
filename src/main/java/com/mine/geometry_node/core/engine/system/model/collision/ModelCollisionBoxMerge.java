package com.mine.geometry_node.core.engine.system.model.collision;

import com.mine.geometry_node.core.engine.system.model.io.ModelCancellation;
import org.joml.Vector3d;

import java.io.IOException;
import java.util.*;

/** Joins aligned rectangular prisms with a separately bounded cross-section expansion. */
final class ModelCollisionBoxMerge {
    private static final double EPS = 1e-8;
    private record Orientation(List<Long> values) {}
    private record Cell(long x, long y) {}
    private record Entry(int index, Vector3d[] axes, double[] low, double[] high, double error) {}
    private ModelCollisionBoxMerge() {}

    static List<ModelCollisionMesh.Part> merge(List<ModelCollisionMesh.Part> source, double gap,
                                               double fit, ModelCancellation token, long deadline) throws IOException {
        List<ModelCollisionMesh.Part> parts = new ArrayList<>(source);
        IdentityHashMap<ModelCollisionMesh.Part, Double> errors = new IdentityHashMap<>();
        try {
            for (int pass = 0; pass < 2; pass++) {
                int joined = 0;
                IdentityHashMap<ModelCollisionMesh.Part, Entry> shapes = new IdentityHashMap<>();
                for (int axis = 0; axis < 3; axis++) {
                    Map<Orientation,List<Entry>> groups = new LinkedHashMap<>();
                    for (int i = 0; i < parts.size(); i++) {
                        if ((i & 255) == 0) ModelCollisionOptimizationBudget.check(token, deadline);
                        var part = parts.get(i);
                        if (part == null) continue;
                        Entry entry = shapes.get(part);
                        if (entry == null && !shapes.containsKey(part)) {
                            entry = box(part, i, errors.getOrDefault(part, 0.0));
                            shapes.put(part, entry);
                        }
                        if (entry != null && entry.index != i)
                            entry = new Entry(i, entry.axes, entry.low, entry.high, entry.error);
                        if (entry != null) groups.computeIfAbsent(orientation(entry.axes), ignored -> new ArrayList<>()).add(entry);
                    }
                    for (List<Entry> group : groups.values()) {
                        ModelCollisionOptimizationBudget.check(token, deadline);
                        int along = axis, first = (axis + 1) % 3, second = (axis + 2) % 3;
                        group.sort(Comparator.comparingDouble(e -> e.low[along]));
                        Map<Cell,List<Entry>> nearby = new HashMap<>();
                        for (Entry entry : group) {
                            ModelCollisionOptimizationBudget.check(token, deadline);
                            if (parts.get(entry.index) == null) continue;
                            Entry current = entry;
                            Cell location = cell(current, first, second, fit);
                            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) {
                                List<Entry> candidates = nearby.get(new Cell(location.x + dx, location.y + dy));
                                if (candidates == null) continue;
                                for (int i = candidates.size() - 1, stop = Math.max(0, candidates.size() - 64); i >= stop; i--) {
                                    Entry previous = candidates.get(i);
                                    if (parts.get(previous.index) == null || previous.index == current.index) continue;
                                    Entry merged = combine(previous, current, along, gap, fit);
                                    if (merged == null) continue;
                                    ModelCollisionMesh.Part body;
                                    try { body = body(merged); }
                                    catch (IllegalArgumentException invalid) { continue; }
                                    parts.set(previous.index, null);
                                    parts.set(current.index, body);
                                    errors.put(body, merged.error);
                                    current = merged;
                                    joined++;
                                }
                            }
                            Cell destination = cell(current, first, second, fit);
                            List<Entry> bucket = nearby.computeIfAbsent(destination, ignored -> new ArrayList<>());
                            if (bucket.size() >= 128) bucket.subList(0, 64).clear();
                            bucket.add(current);
                        }
                    }
                }
                parts.removeIf(Objects::isNull);
                if (joined == 0) break;
            }
        } catch (ModelCollisionOptimizationBudget.Exhausted ignored) {
            parts.removeIf(Objects::isNull);
        }
        return parts;
    }

    private static Entry combine(Entry a, Entry b, int axis, double gap, double fit) {
        double basisDifference = 0, magnitude = 0;
        for (int i = 0; i < 3; i++) {
            double difference = a.axes[i].distance(b.axes[i]);
            if (difference > 1e-12) return null;
            basisDifference += difference;
            magnitude += Math.max(Math.max(Math.abs(a.low[i]), Math.abs(a.high[i])),
                    Math.max(Math.abs(b.low[i]), Math.abs(b.high[i])));
        }
        if (b.low[axis] - a.high[axis] > gap + EPS || a.low[axis] - b.high[axis] > gap + EPS) return null;
        double[] low = new double[3], high = new double[3];
        double da = 0, db = 0;
        for (int i = 0; i < 3; i++) {
            low[i] = Math.min(a.low[i], b.low[i]); high[i] = Math.max(a.high[i], b.high[i]);
            if (i == axis) continue;
            double ea = Math.max(a.low[i] - low[i], high[i] - a.high[i]);
            double eb = Math.max(b.low[i] - low[i], high[i] - b.high[i]);
            da += ea * ea; db += eb * eb;
        }
        double error = Math.max(a.error + Math.sqrt(da), b.error + Math.sqrt(db)) + 4 * basisDifference * magnitude;
        if (error > fit) return null;
        return new Entry(b.index, b.axes, low, high, error);
    }

    private static Entry box(ModelCollisionMesh.Part part, int index, double error) {
        double[] planes = part.planes(), vertices = part.vertices();
        if (planes.length != 24 || vertices.length != 24) return null;
        List<Vector3d> directions = new ArrayList<>();
        for (int i = 0; i < planes.length; i += 4) {
            Vector3d n = new Vector3d(planes[i], planes[i + 1], planes[i + 2]).normalize();
            int major = n.maxComponent();
            if (n.get(major) < 0) n.negate();
            if (directions.stream().noneMatch(p -> p.distanceSquared(n) < 1e-24)) directions.add(n);
        }
        if (directions.size() != 3) return null;
        directions.sort(Comparator.comparingDouble((Vector3d v) -> v.x).thenComparingDouble(v -> v.y).thenComparingDouble(v -> v.z));
        Vector3d[] axes = directions.toArray(Vector3d[]::new);
        for (int a = 0; a < 3; a++) for (int b = a + 1; b < 3; b++)
            if (Math.abs(axes[a].dot(axes[b])) > 1e-12) return null;
        axes[0].normalize();
        axes[1].fma(-axes[0].dot(axes[1]), axes[0]).normalize();
        Vector3d third = new Vector3d(axes[0]).cross(axes[1]);
        if (third.dot(axes[2]) < 0) third.negate();
        axes[2] = third;
        double[] low = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY};
        double[] high = {Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
        for (int i = 0; i < vertices.length; i += 3) for (int a = 0; a < 3; a++) {
            double value = axes[a].x * vertices[i] + axes[a].y * vertices[i + 1] + axes[a].z * vertices[i + 2];
            low[a] = Math.min(low[a], value); high[a] = Math.max(high[a], value);
        }
        // A six-plane convex part must also possess every box corner; reject bevels and skew prisms.
        int corners = 0;
        double reconstructionError = 0;
        for (int i = 0; i < vertices.length; i += 3) {
            int corner = 0;
            for (int a = 0; a < 3; a++) {
                double value = axes[a].x * vertices[i] + axes[a].y * vertices[i + 1] + axes[a].z * vertices[i + 2];
                if (Math.abs(value - low[a]) <= EPS) continue;
                if (Math.abs(value - high[a]) > EPS) return null;
                corner |= 1 << a;
            }
            corners |= 1 << corner;
            Vector3d reconstructed = new Vector3d();
            for (int a = 0; a < 3; a++) reconstructed.fma((corner & (1 << a)) == 0 ? low[a] : high[a], axes[a]);
            reconstructionError = Math.max(reconstructionError,
                    reconstructed.distance(vertices[i], vertices[i + 1], vertices[i + 2]));
        }
        return corners == 255 ? new Entry(index, axes, low, high, error + reconstructionError) : null;
    }

    private static Orientation orientation(Vector3d[] axes) {
        List<Long> values = new ArrayList<>(9);
        for (Vector3d axis : axes) for (int i = 0; i < 3; i++) values.add(Math.round(axis.get(i) * 1e9));
        return new Orientation(List.copyOf(values));
    }

    private static Cell cell(Entry part, int a, int b, double size) {
        return new Cell((long) Math.floor((part.low[a] + part.high[a]) * .5 / size),
                (long) Math.floor((part.low[b] + part.high[b]) * .5 / size));
    }

    private static ModelCollisionMesh.Part body(Entry box) {
        double[] vertices = new double[24], planes = new double[24];
        for (int corner = 0; corner < 8; corner++) {
            Vector3d p = new Vector3d();
            for (int a = 0; a < 3; a++) p.fma((corner & (1 << a)) == 0 ? box.low[a] : box.high[a], box.axes[a]);
            vertices[corner * 3] = p.x; vertices[corner * 3 + 1] = p.y; vertices[corner * 3 + 2] = p.z;
        }
        int[] triangles = new int[36];
        int[][] faces = {{0,4,6,2},{1,3,7,5},{0,1,5,4},{2,6,7,3},{0,2,3,1},{4,5,7,6}};
        for (int f = 0; f < 6; f++) {
            int axis = f / 2; double sign = f % 2 == 0 ? -1 : 1;
            Vector3d n = new Vector3d(box.axes[axis]).mul(sign);
            planes[f * 4] = n.x; planes[f * 4 + 1] = n.y; planes[f * 4 + 2] = n.z;
            planes[f * 4 + 3] = sign * (sign < 0 ? box.low[axis] : box.high[axis]);
            int[] face = faces[f];
            Vector3d a = vertex(vertices, face[0]), b = vertex(vertices, face[1]), c = vertex(vertices, face[2]);
            if (b.sub(a).cross(c.sub(a)).dot(n) < 0) {
                int swap = face[1]; face[1] = face[3]; face[3] = swap;
            }
            int offset = f * 6;
            triangles[offset] = face[0]; triangles[offset + 1] = face[1]; triangles[offset + 2] = face[2];
            triangles[offset + 3] = face[0]; triangles[offset + 4] = face[2]; triangles[offset + 5] = face[3];
        }
        return new ModelCollisionMesh.Part(vertices, triangles, planes);
    }

    private static Vector3d vertex(double[] data, int index) { return new Vector3d(data[index*3], data[index*3+1], data[index*3+2]); }
}
