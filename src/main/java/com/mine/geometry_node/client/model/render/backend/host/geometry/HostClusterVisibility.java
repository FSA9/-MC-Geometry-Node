package com.mine.geometry_node.client.model.render.backend.host.geometry;

import com.mine.geometry_node.core.engine.system.model.domain.ModelBounds;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/** Conservative hierarchy traversal and contiguous triangle-range compaction for one primitive. */
public final class HostClusterVisibility {
    public static final int DEFAULT_MAX_RANGES = 64;

    private HostClusterVisibility() {}

    public static Result evaluate(HostSpatialClusterPlan plan, Predicate<ModelBounds> visible) {
        return evaluate(plan, visible, DEFAULT_MAX_RANGES);
    }

    public static Result fullRange(TriangleRange range) {
        Objects.requireNonNull(range, "range");
        return new Result(List.of(range), 0, 0, 0, 0, range.triangleCount(), false);
    }

    static Result evaluate(HostSpatialClusterPlan plan, Predicate<ModelBounds> visible, int maxRanges) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(visible, "visible");
        if (maxRanges < 1) throw new IllegalArgumentException("maxRanges must be positive");
        if (plan.triangleCount() == 0) return Result.empty();
        if (!plan.hierarchical()) return Result.full(plan.triangleCount(), 0, 0);

        List<HostSpatialClusterPlan.Node> nodes = plan.nodes();
        int[] stack = new int[nodes.size()];
        int stackSize = 0;
        stack[stackSize++] = plan.rootNode();
        int tested = 0;
        int visibleLeaves = 0;
        List<TriangleRange> leafRanges = new ArrayList<>();
        while (stackSize > 0) {
            HostSpatialClusterPlan.Node node = nodes.get(stack[--stackSize]);
            tested++;
            if (!visible.test(node.bounds())) continue;
            if (node.leaf()) {
                HostSpatialClusterPlan.Leaf leaf = plan.leaves().get(node.leafIndex());
                leafRanges.add(new TriangleRange(leaf.firstTriangle(), leaf.triangleCount()));
                visibleLeaves++;
                continue;
            }
            for (int child = node.childCount() - 1; child >= 0; child--) {
                stack[stackSize++] = node.firstChild() + child;
            }
        }
        int candidateLeaves = plan.leaves().size();
        if (leafRanges.isEmpty()) {
            return new Result(List.of(), tested, candidateLeaves, 0, candidateLeaves, 0, false);
        }
        leafRanges.sort(Comparator.comparingInt(TriangleRange::firstTriangle));
        List<TriangleRange> merged = mergeAdjacent(leafRanges);
        if (merged.size() > maxRanges) {
            return new Result(List.of(new TriangleRange(0, plan.triangleCount())), tested,
                    candidateLeaves, visibleLeaves, candidateLeaves - visibleLeaves,
                    plan.triangleCount(), true);
        }
        long submittedTriangles = 0;
        for (TriangleRange range : merged) submittedTriangles += range.triangleCount();
        return new Result(merged, tested, candidateLeaves, visibleLeaves,
                candidateLeaves - visibleLeaves, submittedTriangles, false);
    }

    private static List<TriangleRange> mergeAdjacent(List<TriangleRange> ranges) {
        List<TriangleRange> merged = new ArrayList<>(ranges.size());
        for (TriangleRange range : ranges) {
            if (merged.isEmpty()) {
                merged.add(range);
                continue;
            }
            TriangleRange previous = merged.getLast();
            if (previous.endTriangle() == range.firstTriangle()) {
                merged.set(merged.size() - 1, new TriangleRange(previous.firstTriangle(),
                        Math.addExact(previous.triangleCount(), range.triangleCount())));
            } else {
                merged.add(range);
            }
        }
        return List.copyOf(merged);
    }

    public record TriangleRange(int firstTriangle, int triangleCount) {
        public TriangleRange {
            if (firstTriangle < 0 || triangleCount < 1) {
                throw new IllegalArgumentException("invalid HOST cluster triangle range");
            }
        }

        public int endTriangle() { return Math.addExact(firstTriangle, triangleCount); }
        public int firstIndex() { return Math.multiplyExact(firstTriangle, 6); }
        public int indexCount() { return Math.multiplyExact(triangleCount, 6); }
    }

    public record Result(List<TriangleRange> ranges, int nodesTested, int candidateLeaves,
                         int visibleLeaves, int culledLeaves, long submittedTriangles,
                         boolean rangeLimitFallback) {
        public Result {
            ranges = List.copyOf(ranges);
            if (nodesTested < 0 || candidateLeaves < 0 || visibleLeaves < 0 || culledLeaves < 0
                    || visibleLeaves + culledLeaves != candidateLeaves || submittedTriangles < 0) {
                throw new IllegalArgumentException("invalid HOST cluster visibility result");
            }
        }

        private static Result empty() { return new Result(List.of(), 0, 0, 0, 0, 0, false); }
        private static Result full(int triangles, int leaves, int tested) {
            return new Result(List.of(new TriangleRange(0, triangles)), tested, leaves, leaves, 0,
                    triangles, false);
        }

        public boolean fullyCulled() { return ranges.isEmpty(); }
        public int drawCalls() { return ranges.size(); }
    }
}
