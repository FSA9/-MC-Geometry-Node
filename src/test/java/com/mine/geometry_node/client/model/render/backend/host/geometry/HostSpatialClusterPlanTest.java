package com.mine.geometry_node.client.model.render.backend.host.geometry;

import com.mine.geometry_node.core.engine.system.model.domain.ModelBounds;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class HostSpatialClusterPlanTest {
    @Test
    void smallGeometryUsesIdentitySingleLeaf() {
        HostSpatialClusterPlan plan = geometry(17).clusters();

        assertEquals(HostSpatialClusterPlan.Mode.SINGLE_SMALL, plan.mode());
        assertEquals(1, plan.leaves().size());
        assertEquals(1, plan.nodes().size());
        assertEquals(0, plan.rootNode());
        for (int triangle = 0; triangle < 17; triangle++) assertEquals(triangle, plan.sourceTriangle(triangle));
    }

    @Test
    void clusteredPlanIsDeterministicConservativeAndTriangleComplete() {
        float[] source = triangleData(4097);
        HostSpatialClusterPlan first = new HostEntityGeometry(source).clusters();
        HostSpatialClusterPlan second = new HostEntityGeometry(source).clusters();

        assertEquals(HostSpatialClusterPlan.Mode.HIERARCHICAL, first.mode());
        assertEquals(first.leaves(), second.leaves());
        assertEquals(first.nodes(), second.nodes());
        assertEquals(first.rootNode(), second.rootNode());
        assertEquals(4097, first.triangleCount());

        Set<Integer> sourceTriangles = new HashSet<>();
        int covered = 0;
        for (HostSpatialClusterPlan.Leaf leaf : first.leaves()) {
            assertEquals(covered, leaf.firstTriangle());
            assertTrue(leaf.triangleCount() <= HostSpatialClusterPlan.TARGET_TRIANGLES_PER_LEAF);
            for (int ordered = leaf.firstTriangle(); ordered < leaf.firstTriangle() + leaf.triangleCount(); ordered++) {
                int sourceTriangle = first.sourceTriangle(ordered);
                assertTrue(sourceTriangles.add(sourceTriangle), "duplicate source triangle " + sourceTriangle);
                assertTriangleInside(sourceTriangle, leaf.bounds(), source);
                assertEquals(sourceTriangle, second.sourceTriangle(ordered));
            }
            covered += leaf.triangleCount();
        }
        assertEquals(4097, covered);
        assertEquals(4097, sourceTriangles.size());

        for (HostSpatialClusterPlan.Node node : first.nodes()) {
            if (node.leaf()) {
                assertEquals(first.leaves().get(node.leafIndex()).bounds(), node.bounds());
                continue;
            }
            assertTrue(node.childCount() <= HostSpatialClusterPlan.HIERARCHY_FANOUT);
            for (int child = 0; child < node.childCount(); child++) {
                assertContains(node.bounds(), first.nodes().get(node.firstChild() + child).bounds());
            }
        }
        ModelBounds root = first.nodes().get(first.rootNode()).bounds();
        for (int triangle = 0; triangle < 4097; triangle++) assertTriangleInside(triangle, root, source);
    }

    @Test
    void metadataEstimateEnforcesIndependentBudget() {
        assertTrue(HostSpatialClusterPlan.estimatedMetadataBytes(
                HostSpatialClusterPlan.MIN_CLUSTERED_TRIANGLES) < HostSpatialClusterPlan.MAX_METADATA_BYTES);
        assertTrue(HostSpatialClusterPlan.estimatedMetadataBytes(Integer.MAX_VALUE)
                > HostSpatialClusterPlan.MAX_METADATA_BYTES);
        assertTrue(HostSpatialClusterPlan.retainedMetadataBytes(Integer.MAX_VALUE)
                < HostSpatialClusterPlan.MAX_METADATA_BYTES);
    }

    @Test
    void rejectsNonFinitePositions() {
        float[] vertices = triangleData(1);
        vertices[0] = Float.NaN;
        assertThrows(IllegalArgumentException.class, () -> new HostEntityGeometry(vertices));
    }

    private static HostEntityGeometry geometry(int triangles) {
        return new HostEntityGeometry(triangleData(triangles));
    }

    private static float[] triangleData(int triangles) {
        float[] vertices = new float[Math.multiplyExact(triangles, 36)];
        for (int triangle = 0; triangle < triangles; triangle++) {
            // Scramble source order along X so deterministic spatial ordering is observable.
            float x = (triangle * 7919 % Math.max(1, triangles)) * 0.01F;
            put(vertices, triangle * 36, x, triangle % 11, triangle % 7);
            put(vertices, triangle * 36 + 12, x + 0.25F, triangle % 11, triangle % 7);
            put(vertices, triangle * 36 + 24, x, triangle % 11 + 0.25F, triangle % 7);
        }
        return vertices;
    }

    private static void put(float[] data, int offset, float x, float y, float z) {
        data[offset] = x; data[offset + 1] = y; data[offset + 2] = z;
        data[offset + 4] = 1;
        data[offset + 8] = data[offset + 9] = data[offset + 10] = data[offset + 11] = 1;
    }

    private static void assertTriangleInside(int triangle, ModelBounds bounds, float[] data) {
        int base = triangle * 36;
        for (int vertex = 0; vertex < 3; vertex++) {
            int offset = base + vertex * 12;
            assertTrue(data[offset] >= bounds.min().x() && data[offset] <= bounds.max().x());
            assertTrue(data[offset + 1] >= bounds.min().y() && data[offset + 1] <= bounds.max().y());
            assertTrue(data[offset + 2] >= bounds.min().z() && data[offset + 2] <= bounds.max().z());
        }
    }

    private static void assertContains(ModelBounds outer, ModelBounds inner) {
        assertTrue(outer.min().x() <= inner.min().x() && outer.min().y() <= inner.min().y()
                && outer.min().z() <= inner.min().z());
        assertTrue(outer.max().x() >= inner.max().x() && outer.max().y() >= inner.max().y()
                && outer.max().z() >= inner.max().z());
    }
}
