package com.mine.geometry_node.core.engine.system.model.collision.generation;

import com.mine.geometry_node.core.engine.system.model.collision.ModelCollisionMesh;

import com.mine.geometry_node.core.engine.system.model.io.ModelCancellation;

import org.joml.Vector3d;

import java.io.IOException;
import java.util.*;

/** Exact common-cross-section prism joining. The only new volume is the bounded axial gap. */
final class ModelCollisionPrismMerge {
    private static final double EPS = 1e-7;

    private record Axis(long x, long y, long z) {}

    private record Plane(long x, long y, long z, long d) implements Comparable<Plane> {
        public int compareTo(Plane p) {
            int c = Long.compare(x, p.x);
            if (c == 0) c = Long.compare(y, p.y);
            if (c == 0) c = Long.compare(z, p.z);
            return c == 0 ? Long.compare(d, p.d) : c;
        }
    }

    private record Key(Axis axis, List<Plane> sides) {}

    private record Span(int index, Key key, double min, double max) {}

    private ModelCollisionPrismMerge() {}

    static List<ModelCollisionMesh.Part> merge(
            List<ModelCollisionMesh.Part> source,
            double scale,
            ModelCancellation token,
            long deadline)
            throws IOException {
        return merge(source, scale, 0, token, deadline);
    }

    /** remainingFitError is reserved by the caller, in addition to earlier surface fitting. */
    static List<ModelCollisionMesh.Part> merge(
            List<ModelCollisionMesh.Part> source,
            double scale,
            double remainingFitError,
            ModelCancellation token,
            long deadline)
            throws IOException {
        if (!Double.isFinite(remainingFitError) || remainingFitError < 0)
            throw new IllegalArgumentException("invalid remaining collision fitting error");
        long started = System.nanoTime();
        long exactDeadline = remainingFitError == 0 ? deadline
                : started + Math.max(0, deadline - started) / 2;
        List<ModelCollisionMesh.Part> parts = new ArrayList<>(source);
        try {
        for (int pass = 0; pass < 2; pass++) {
            Map<Key, List<Span>> groups = new HashMap<>();
            int count = 0;
            for (int i = 0; i < parts.size(); i++) {
                if ((i & 255) == 0) check(token, exactDeadline);
                var part = parts.get(i);
                if (part == null) continue;
                for (Span span : spans(part, i))
                    groups.computeIfAbsent(span.key, ignored -> new ArrayList<>()).add(span);
            }
            for (var entry : groups.entrySet()) {
                check(token, exactDeadline);
                List<Span> spans = entry.getValue();
                spans.sort(Comparator.comparingDouble(Span::min));
                Span previous = null;
                int processed = 0;
                for (Span candidate : spans) {
                    if ((processed++ & 255) == 0) check(token, exactDeadline);
                    var part = parts.get(candidate.index);
                    if (part == null) continue;
                    Span current = find(part, candidate.index, entry.getKey());
                    if (current == null) continue;
                    if (previous != null && parts.get(previous.index) != null) {
                        previous = find(parts.get(previous.index), previous.index, entry.getKey());
                        if (previous != null
                                && current.min - previous.max <= scale + EPS
                                && previous.min - current.max <= scale + EPS
                                && sameSection(
                                        parts.get(previous.index), part, entry.getKey().axis)) {
                            List<Vector3d> points = new ArrayList<>();
                            append(points, parts.get(previous.index));
                            append(points, part);
                            var merged = ModelCollisionSolids.hull(points);
                            if (merged != null) {
                                parts.set(previous.index, merged);
                                parts.set(current.index, null);
                                previous = find(merged, previous.index, entry.getKey());
                                count++;
                                continue;
                            }
                        }
                    }
                    previous = current;
                }
            }
            parts.removeIf(Objects::isNull);
            if (count == 0) break;
        }
        } catch (ModelCollisionOptimizationBudget.Exhausted ignored) {
            // Completed joins cover both inputs. All unprocessed parts remain in the list.
            parts.removeIf(Objects::isNull);
        }
        return remainingFitError == 0 ? parts
                : ModelCollisionBoxMerge.merge(parts, scale, remainingFitError, token, deadline);
    }

    private static Span find(ModelCollisionMesh.Part part, int index, Key key) {
        for (Span span : spans(part, index)) if (span.key.equals(key)) return span;
        return null;
    }

    private static boolean sameSection(
            ModelCollisionMesh.Part a, ModelCollisionMesh.Part b, Axis axis) {
        Vector3d direction = new Vector3d(axis.x, axis.y, axis.z).normalize();
        for (int side = 0; side < 2; side++) {
            double[] planes = (side == 0 ? a : b).planes(),
                    vertices = (side == 0 ? b : a).vertices();
            for (int p = 0; p < planes.length; p += 4) {
                if (Math.abs(
                                direction.x * planes[p]
                                        + direction.y * planes[p + 1]
                                        + direction.z * planes[p + 2])
                        > 0.5) continue;
                double support = Double.NEGATIVE_INFINITY;
                for (int v = 0; v < vertices.length; v += 3)
                    support =
                            Math.max(
                                    support,
                                    planes[p] * vertices[v]
                                            + planes[p + 1] * vertices[v + 1]
                                            + planes[p + 2] * vertices[v + 2]
                                            - planes[p + 3]);
                if (Math.abs(support) > 1e-8) return false;
            }
        }
        return true;
    }

    private static List<Span> spans(ModelCollisionMesh.Part part, int index) {
        double[] planes = part.planes(), vertices = part.vertices();
        List<Span> result = new ArrayList<>();
        Set<Axis> seen = new HashSet<>();
        for (int p = 0; p < planes.length; p += 4) {
            Vector3d axis = new Vector3d(planes[p], planes[p + 1], planes[p + 2]);
            double major =
                    Math.abs(axis.x) >= Math.abs(axis.y) && Math.abs(axis.x) >= Math.abs(axis.z)
                            ? axis.x
                            : Math.abs(axis.y) >= Math.abs(axis.z) ? axis.y : axis.z;
            if (major < 0) axis.negate();
            Axis direction = new Axis(round(axis.x), round(axis.y), round(axis.z));
            if (!seen.add(direction)) continue;
            List<Plane> sides = new ArrayList<>();
            int ends = 0;
            boolean prism = true;
            for (int q = 0; q < planes.length; q += 4) {
                double dot = axis.x * planes[q] + axis.y * planes[q + 1] + axis.z * planes[q + 2];
                if (Math.abs(Math.abs(dot) - 1) < EPS) {
                    ends++;
                    continue;
                }
                if (Math.abs(dot) > EPS) {
                    prism = false;
                    break;
                }
                sides.add(
                        new Plane(
                                round(planes[q]),
                                round(planes[q + 1]),
                                round(planes[q + 2]),
                                round(planes[q + 3])));
            }
            if (!prism || ends != 2 || sides.size() < 3) continue;
            Collections.sort(sides);
            double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
            for (int v = 0; v < vertices.length; v += 3) {
                double d =
                        axis.x * vertices[v] + axis.y * vertices[v + 1] + axis.z * vertices[v + 2];
                min = Math.min(min, d);
                max = Math.max(max, d);
            }
            result.add(new Span(index, new Key(direction, List.copyOf(sides)), min, max));
        }
        return result;
    }

    private static long round(double value) {
        return Math.round(value * 1e7);
    }

    private static void append(List<Vector3d> points, ModelCollisionMesh.Part part) {
        double[] v = part.vertices();
        for (int i = 0; i < v.length; i += 3) points.add(new Vector3d(v[i], v[i + 1], v[i + 2]));
    }

    private static void check(ModelCancellation token, long deadline) throws IOException {
        ModelCollisionOptimizationBudget.check(token, deadline);
    }
}
