package com.mine.geometry_node.client.runtime.model.render.pipeline;

import java.util.*;

/** Worker-built static transparency BSP. Attributes are linearly interpolated, never relit here. */
public final class ModelTransparentBsp {
    public record Vertex(float[] attrs) {
        public Vertex {
            Objects.requireNonNull(attrs);
            if (attrs.length < 3) throw new IllegalArgumentException("position requires three attributes");
            attrs = attrs.clone();
            for (float value : attrs) if (!Float.isFinite(value)) throw new IllegalArgumentException("nonfinite attribute");
        }
        @Override public float[] attrs() { return attrs.clone(); }
        public float attribute(int index) { return attrs[index]; }
        public int attributeCount() { return attrs.length; }
    }
    public record Triangle(Vertex a, Vertex b, Vertex c, int owner) {
        public Triangle {
            Objects.requireNonNull(a); Objects.requireNonNull(b); Objects.requireNonNull(c);
            if (owner < 0 || a.attrs.length != b.attrs.length || a.attrs.length != c.attrs.length)
                throw new IllegalArgumentException("invalid triangle owner or attribute width");
        }
    }
    public record Limits(int maxDepth, int maxFragments, long maxWork, double epsilon) {
        public Limits {
            if (maxDepth < 1 || maxFragments < 1 || maxWork < 1 || !Double.isFinite(epsilon) || epsilon <= 0)
                throw new IllegalArgumentException("invalid BSP limits");
        }
    }
    public static final class BudgetExceededException extends RuntimeException {
        BudgetExceededException(String reason) { super(reason); }
    }
    private record Plane(double x, double y, double z, double offset) {
        double distance(Vertex v) { return x * v.attrs[0] + y * v.attrs[1] + z * v.attrs[2] + offset; }
        double distance(double cx, double cy, double cz) { return x * cx + y * cy + z * cz + offset; }
    }
    private static final class Node {
        Plane plane;
        int[] coplanar;
        Node front, back;
    }
    private record Task(Node node, List<Triangle> triangles, int depth) {}
    public static final class Result {
        private final Node root;
        private final List<Triangle> triangles;
        private final long work;
        private final int splits, depth;
        private Result(Node root, List<Triangle> triangles, long work, int splits, int depth) {
            this.root = root; this.triangles = List.copyOf(triangles); this.work = work; this.splits = splits; this.depth = depth;
        }
        public List<Triangle> triangles() { return triangles; }
        public long work() { return work; }
        public int splitCount() { return splits; }
        /** Order changes only on crossing a partition plane; camera orientation is irrelevant. */
        public int[] triangleOrder(double cameraX, double cameraY, double cameraZ) {
            if (!Double.isFinite(cameraX) || !Double.isFinite(cameraY) || !Double.isFinite(cameraZ))
                throw new IllegalArgumentException("nonfinite camera");
            int[] result = new int[triangles.size()];
            if (root == null) return result;
            // Traversal uses two compact depth-bounded arrays, not per-node Visit allocations.
            Node[] nodes = new Node[Math.addExact(Math.multiplyExact(depth, 2), 1)];
            boolean[] emit = new boolean[nodes.length];
            int size = 1, index = 0; nodes[0] = root;
            while (size > 0) {
                int top = --size; Node node = nodes[top];
                if (emit[top]) { for (int triangle : node.coplanar) result[index++] = triangle; continue; }
                boolean front = node.plane.distance(cameraX, cameraY, cameraZ) >= 0;
                Node near = front ? node.front : node.back, far = front ? node.back : node.front;
                if (near != null) { nodes[size] = near; emit[size++] = false; }
                nodes[size] = node; emit[size++] = true;
                if (far != null) { nodes[size] = far; emit[size++] = false; }
            }
            return result;
        }
    }
    private static final class Builder {
        final Limits limits;
        final Runnable cancellation;
        long work;
        int fragments, splits;
        Builder(Limits limits, Runnable cancellation) { this.limits = limits; this.cancellation = cancellation; }
        void tick() {
            if (++work > limits.maxWork) throw new BudgetExceededException("BSP work limit exceeded");
            if ((work & 255) == 0) cancellation.run();
        }
        void fragment(int count) {
            if ((long) fragments + count > limits.maxFragments) throw new BudgetExceededException("BSP fragment limit exceeded");
            fragments += count;
        }
        List<Triangle> triangulate(List<Vertex> polygon, int owner) {
            List<Triangle> out = new ArrayList<>(2);
            for (int i = 1; i + 1 < polygon.size(); i++) {
                tick(); Triangle triangle = new Triangle(polygon.getFirst(), polygon.get(i), polygon.get(i + 1), owner);
                if (plane(triangle, limits.epsilon) != null) out.add(triangle);
            }
            return out;
        }
        void split(Triangle triangle, Plane plane, List<Triangle> front, List<Triangle> back) {
            List<Vertex> positive = new ArrayList<>(4), negative = new ArrayList<>(4);
            Vertex[] vertices = {triangle.a, triangle.b, triangle.c};
            for (int i = 0; i < 3; i++) {
                Vertex a = vertices[i], b = vertices[(i + 1) % 3];
                double da = plane.distance(a), db = plane.distance(b);
                int sa = side(da, limits.epsilon), sb = side(db, limits.epsilon);
                if (sa >= 0) positive.add(a);
                if (sa <= 0) negative.add(a);
                if (sa * sb < 0) {
                    double t = da / (da - db);
                    float[] attributes = new float[a.attrs.length];
                    for (int k = 0; k < attributes.length; k++) attributes[k] = (float) ((1 - t) * a.attrs[k] + t * b.attrs[k]);
                    Vertex intersection = new Vertex(attributes);
                    positive.add(intersection); negative.add(intersection);
                }
            }
            List<Triangle> f = triangulate(positive, triangle.owner), b = triangulate(negative, triangle.owner);
            fragment(f.size() + b.size() - 1); splits++;
            front.addAll(f); back.addAll(b);
        }
    }
    public static Result build(List<Triangle> input, Limits limits, Runnable cancellationCheck) {
        Objects.requireNonNull(input); Objects.requireNonNull(limits); Objects.requireNonNull(cancellationCheck);
        Builder builder = new Builder(limits, cancellationCheck); cancellationCheck.run();
        if (input.size() > limits.maxFragments) throw new BudgetExceededException("BSP input fragment limit exceeded");
        List<Triangle> valid = new ArrayList<>(input.size()); int width = -1;
        for (Triangle triangle : input) {
            builder.tick(); Objects.requireNonNull(triangle);
            if (width < 0) width = triangle.a.attrs.length;
            if (triangle.a.attrs.length != width) throw new IllegalArgumentException("mixed attribute widths");
            if (plane(triangle, limits.epsilon) != null) valid.add(triangle);
        }
        builder.fragments = valid.size();
        if (valid.isEmpty()) return new Result(null, List.of(), builder.work, 0, 0);
        Node root = new Node(); ArrayDeque<Task> stack = new ArrayDeque<>(); stack.push(new Task(root, valid, 1));
        List<Triangle> output = new ArrayList<>(); int maxDepth = 0;
        while (!stack.isEmpty()) {
            cancellationCheck.run(); Task task = stack.pop();
            if (task.depth > limits.maxDepth) throw new BudgetExceededException("BSP depth limit exceeded");
            maxDepth = Math.max(maxDepth, task.depth);
            Node node = task.node; node.plane = plane(task.triangles.getFirst(), limits.epsilon);
            List<Triangle> front = new ArrayList<>(), back = new ArrayList<>();
            List<Integer> coplanar = new ArrayList<>();
            for (Triangle triangle : task.triangles) {
                builder.tick(); boolean positive = false, negative = false;
                for (Vertex vertex : new Vertex[]{triangle.a, triangle.b, triangle.c}) {
                    int side = side(node.plane.distance(vertex), limits.epsilon);
                    positive |= side > 0; negative |= side < 0;
                }
                if (positive && negative) builder.split(triangle, node.plane, front, back);
                else if (positive) front.add(triangle);
                else if (negative) back.add(triangle);
                else { coplanar.add(output.size()); output.add(triangle); }
            }
            node.coplanar = coplanar.stream().mapToInt(Integer::intValue).toArray();
            if (!front.isEmpty()) { node.front = new Node(); stack.push(new Task(node.front, front, task.depth + 1)); }
            if (!back.isEmpty()) { node.back = new Node(); stack.push(new Task(node.back, back, task.depth + 1)); }
        }
        cancellationCheck.run(); return new Result(root, output, builder.work, builder.splits, maxDepth);
    }
    private static int side(double distance, double epsilon) { return distance > epsilon ? 1 : distance < -epsilon ? -1 : 0; }
    private static Plane plane(Triangle t, double epsilon) {
        double ax = (double) t.b.attrs[0] - t.a.attrs[0], ay = (double) t.b.attrs[1] - t.a.attrs[1], az = (double) t.b.attrs[2] - t.a.attrs[2];
        double bx = (double) t.c.attrs[0] - t.a.attrs[0], by = (double) t.c.attrs[1] - t.a.attrs[1], bz = (double) t.c.attrs[2] - t.a.attrs[2];
        double nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
        double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (length <= epsilon * epsilon) return null;
        nx /= length; ny /= length; nz /= length;
        return new Plane(nx, ny, nz, -(nx * t.a.attrs[0] + ny * t.a.attrs[1] + nz * t.a.attrs[2]));
    }
    private ModelTransparentBsp() {}
}
