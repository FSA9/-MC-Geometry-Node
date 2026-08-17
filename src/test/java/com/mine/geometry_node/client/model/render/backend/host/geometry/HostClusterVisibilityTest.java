package com.mine.geometry_node.client.model.render.backend.host.geometry;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class HostClusterVisibilityTest {
    @Test
    void smallPrimitiveUsesOneFullRange() {
        HostSpatialClusterPlan plan = new HostEntityGeometry(triangles(8)).clusters();

        HostClusterVisibility.Result result = HostClusterVisibility.evaluate(plan, ignored -> false);

        assertEquals(1, result.drawCalls());
        assertEquals(8, result.submittedTriangles());
        assertEquals(0, result.candidateLeaves());
        assertEquals(0, result.ranges().getFirst().firstIndex());
        assertEquals(24, result.ranges().getFirst().indexCount());
    }

    @Test
    void hierarchyCullsLeavesAndMergesOnlyAdjacentRanges() {
        HostSpatialClusterPlan plan = new HostEntityGeometry(triangles(4096)).clusters();
        Set<Object> visibleBounds = new HashSet<>();
        visibleBounds.add(plan.nodes().get(plan.rootNode()).bounds());
        for (int leaf = 0; leaf < 3; leaf++) visibleBounds.add(plan.leaves().get(leaf).bounds());
        for (HostSpatialClusterPlan.Node node : plan.nodes()) {
            if (!node.leaf() && node.firstChild() == 0) visibleBounds.add(node.bounds());
        }

        HostClusterVisibility.Result result = HostClusterVisibility.evaluate(
                plan, visibleBounds::contains, HostClusterVisibility.DEFAULT_MAX_RANGES);

        assertFalse(result.fullyCulled());
        assertEquals(3, result.visibleLeaves());
        assertEquals(1, result.drawCalls());
        assertEquals(1536, result.submittedTriangles());
    }

    @Test
    void tooManyRangesFallsBackToWholePrimitive() {
        HostSpatialClusterPlan plan = new HostEntityGeometry(triangles(8192)).clusters();
        Set<Object> visibleBounds = new HashSet<>();
        for (HostSpatialClusterPlan.Node node : plan.nodes()) {
            if (!node.leaf() || node.leafIndex() % 2 == 0) visibleBounds.add(node.bounds());
        }

        HostClusterVisibility.Result result = HostClusterVisibility.evaluate(plan, visibleBounds::contains, 2);

        assertTrue(result.rangeLimitFallback());
        assertEquals(1, result.drawCalls());
        assertEquals(plan.triangleCount(), result.submittedTriangles());
    }

    @Test
    void invisibleRootProducesNoRanges() {
        HostSpatialClusterPlan plan = new HostEntityGeometry(triangles(4096)).clusters();

        HostClusterVisibility.Result result = HostClusterVisibility.evaluate(plan, ignored -> false);

        assertTrue(result.fullyCulled());
        assertEquals(1, result.nodesTested());
        assertEquals(plan.leaves().size(), result.culledLeaves());
    }

    private static float[] triangles(int count) {
        float[] vertices = new float[Math.multiplyExact(count, 36)];
        for (int triangle = 0; triangle < count; triangle++) {
            float x = triangle;
            put(vertices, triangle * 36, x, 0, 0);
            put(vertices, triangle * 36 + 12, x + 0.5F, 0, 0);
            put(vertices, triangle * 36 + 24, x, 0.5F, 0);
        }
        return vertices;
    }

    private static void put(float[] vertices, int offset, float x, float y, float z) {
        vertices[offset] = x; vertices[offset + 1] = y; vertices[offset + 2] = z;
        vertices[offset + 4] = 1;
        vertices[offset + 8] = vertices[offset + 9] = vertices[offset + 10] = vertices[offset + 11] = 1;
    }
}
