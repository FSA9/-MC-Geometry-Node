package com.mine.geometry_node.client.model.render.backend.host.lod;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HostModelLodPlanTest {
    @Test
    void smallPrimitiveReusesExactSourceAtEveryLevel() {
        HostModelLodPlan plan = HostModelLodPlan.build(triangles(32));

        assertEquals(4, plan.levels().size());
        assertEquals(0, plan.proxyTriangleCount());
        for (int level = 0; level < 4; level++) {
            assertEquals(0, plan.level(level).firstTriangle());
            assertEquals(32, plan.level(level).triangleCount());
            assertEquals(0, plan.level(level).generatedLevel());
        }
        assertEquals(HostModelLodPlan.StopReason.BELOW_MINIMUM, plan.statistics().stopReason());
    }

    @Test
    void generatedLevelsAreDeterministicBoundedAndAddressableInOneStream() {
        float[] source = grid(40);
        HostModelLodPlan first = HostModelLodPlan.build(source);
        HostModelLodPlan second = HostModelLodPlan.build(source);

        assertEquals(first.levels(), second.levels());
        assertArrayEquals(first.proxyVertexData(), second.proxyVertexData());
        assertEquals(40 * 40 * 2, first.statistics().sourceTriangles());
        assertEquals(first.statistics().sourceTriangles() + first.proxyTriangleCount(),
                first.staticTriangleCount());
        int previous = first.statistics().sourceTriangles();
        for (int requested = 1; requested < 4; requested++) {
            HostModelLodPlan.Level level = first.level(requested);
            assertTrue(level.triangleCount() <= previous);
            if (level.generatedLevel() == requested) {
                assertTrue(level.triangleCount() <= (int) Math.floor(
                        first.statistics().sourceTriangles() * HostModelLodPlan.TARGET_RATIOS[requested - 1]));
            }
            assertTrue(level.firstTriangle() + level.triangleCount() <= first.staticTriangleCount());
            previous = level.triangleCount();
        }
        assertTrue(first.statistics().buildNanos() >= 0);
    }

    @Test
    void proxyEstimateCoversIdealTargetSum() {
        long sourceBytes = 1000L * 3 * 12 * Float.BYTES;
        assertEquals(sourceBytes * 130 / 100, HostModelLodPlan.estimatedProxyBytes(1000));
        assertEquals(0, HostModelLodPlan.estimatedProxyBytes(32));
    }

    @Test
    void canonicalTopologyDoesNotTreatAttributeSeamsAsOpenGeometry() {
        int side = 40;
        float[] source = grid(side);
        int[] indices = gridIndices(side);

        HostModelLodPlan plan = HostModelLodPlan.build(source, indices, (side + 1) * (side + 1));

        assertEquals(3, plan.level(3).generatedLevel());
        assertTrue(plan.statistics().lockedRatio() < 0.15);
        assertTrue(plan.level(3).triangleCount() <= plan.statistics().sourceTriangles() * 0.20F);
    }

    @Test
    void disconnectedSmallComponentsReuseExactSourceInsteadOfDisappearing() {
        HostModelLodPlan plan = HostModelLodPlan.build(triangles(300));

        assertEquals(300, plan.statistics().componentCount());
        assertEquals(300, plan.statistics().protectedComponentCount());
        assertEquals(0, plan.proxyTriangleCount());
        for (int level = 0; level < 4; level++) {
            assertEquals(300, plan.level(level).triangleCount());
            assertEquals(0, plan.level(level).generatedLevel());
        }
    }

    @Test
    void largeComponentReducesWhileDisconnectedSmallComponentsRemainExact() {
        float[] large = grid(40);
        int smallTriangles = 12;
        float[] source = new float[large.length + smallTriangles * 36];
        System.arraycopy(large, 0, source, 0, large.length);
        for (int triangle = 0; triangle < smallTriangles; triangle++) {
            int offset = large.length + triangle * 36;
            float x = 1000 + triangle * 2;
            put(source, offset, x, 0, 0);
            put(source, offset + 12, x + 0.5F, 0, 0);
            put(source, offset + 24, x, 0.5F, 0);
        }

        HostModelLodPlan plan = HostModelLodPlan.build(source);

        assertEquals(smallTriangles + 1, plan.statistics().componentCount());
        assertEquals(smallTriangles, plan.statistics().protectedComponentCount());
        assertTrue(plan.proxyTriangleCount() > 0);
        for (int requested = 1; requested < 4; requested++) {
            HostModelLodPlan.Level level = plan.level(requested);
            if (level.generatedLevel() == 0) continue;
            assertEquals(smallTriangles * 3, verticesAtOrBeyondX(plan, level, 1000));
        }
    }

    @Test
    void componentsTouchingOnlyAtOnePositionStaySeparate() {
        int triangles = 300;
        float[] source = new float[triangles * 36];
        for (int triangle = 0; triangle < triangles; triangle++) {
            int offset = triangle * 36;
            put(source, offset, 0, 0, 0);
            put(source, offset + 12, triangle + 1, 1, 0);
            put(source, offset + 24, triangle + 1, 0, 1);
        }

        HostModelLodPlan plan = HostModelLodPlan.build(source);

        assertEquals(triangles, plan.statistics().componentCount());
        assertEquals(triangles, plan.statistics().protectedComponentCount());
        assertEquals(0, plan.proxyTriangleCount());
    }

    @Test
    void positionEquivalentSharedEdgeConnectsAcrossAttributeSeam() {
        int quads = 150;
        float[] source = new float[quads * 2 * 36];
        for (int quad = 0; quad < quads; quad++) {
            int offset = quad * 72;
            float x = quad * 3;
            put(source, offset, x, 0, 0);
            put(source, offset + 12, x + 1, 0, 0);
            put(source, offset + 24, x, 1, 0);
            put(source, offset + 36, x + 1, 0, 0);
            put(source, offset + 48, x + 1, 1, 0);
            put(source, offset + 60, x, 1, 0);
            source[offset + 12 + 6] = 0.25F;
            source[offset + 24 + 6] = 0.25F;
            source[offset + 36 + 6] = 0.75F;
            source[offset + 60 + 6] = 0.75F;
        }

        HostModelLodPlan plan = HostModelLodPlan.build(source);

        assertEquals(quads, plan.statistics().componentCount());
        assertEquals(quads, plan.statistics().protectedComponentCount());
    }

    @Test
    void canonicalOccurrenceAttributeMismatchFallsBackToExactSource() {
        int side = 40;
        float[] source = grid(side);
        int[] indices = gridIndices(side);
        source[3 * 12 + 3] = 1.0F;

        HostModelLodPlan plan = HostModelLodPlan.build(source, indices, (side + 1) * (side + 1));

        assertEquals(HostModelLodPlan.StopReason.ATTRIBUTE_OCCURRENCE_MISMATCH,
                plan.statistics().stopReason());
        assertEquals(0, plan.proxyTriangleCount());
        for (int level = 0; level < 4; level++) assertEquals(0, plan.level(level).generatedLevel());
    }

    private static float[] triangles(int count) {
        float[] result = new float[count * 36];
        for (int triangle = 0; triangle < count; triangle++) {
            put(result, triangle * 36, triangle, 0, 0);
            put(result, triangle * 36 + 12, triangle + 0.5F, 0, 0);
            put(result, triangle * 36 + 24, triangle, 0.5F, 0);
        }
        return result;
    }

    private static float[] grid(int side) {
        float[] result = new float[side * side * 2 * 36];
        int triangle = 0;
        for (int z = 0; z < side; z++) for (int x = 0; x < side; x++) {
            put(result, triangle++ * 36, x, 0, z);
            put(result, (triangle - 1) * 36 + 12, x + 1, 0, z);
            put(result, (triangle - 1) * 36 + 24, x + 1, 0, z + 1);
            put(result, triangle++ * 36, x, 0, z);
            put(result, (triangle - 1) * 36 + 12, x + 1, 0, z + 1);
            put(result, (triangle - 1) * 36 + 24, x, 0, z + 1);
        }
        return result;
    }

    private static int[] gridIndices(int side) {
        int[] result = new int[side * side * 6];
        int cursor = 0;
        for (int z = 0; z < side; z++) for (int x = 0; x < side; x++) {
            int topLeft = z * (side + 1) + x;
            int topRight = topLeft + 1;
            int bottomLeft = topLeft + side + 1;
            int bottomRight = bottomLeft + 1;
            result[cursor++] = topLeft;
            result[cursor++] = topRight;
            result[cursor++] = bottomRight;
            result[cursor++] = topLeft;
            result[cursor++] = bottomRight;
            result[cursor++] = bottomLeft;
        }
        return result;
    }

    private static void put(float[] data, int offset, float x, float y, float z) {
        data[offset] = x;
        data[offset + 1] = y;
        data[offset + 2] = z;
        data[offset + 4] = 1;
        data[offset + 6] = x / 40.0F;
        data[offset + 7] = z / 40.0F;
        data[offset + 8] = data[offset + 9] = data[offset + 10] = data[offset + 11] = 1;
    }

    private static int verticesAtOrBeyondX(HostModelLodPlan plan, HostModelLodPlan.Level level, float minimumX) {
        float[] proxy = plan.proxyVertexData();
        int sourceTriangles = plan.statistics().sourceTriangles();
        int firstVertex = (level.firstTriangle() - sourceTriangles) * 3;
        int endVertex = firstVertex + level.triangleCount() * 3;
        int result = 0;
        for (int vertex = firstVertex; vertex < endVertex; vertex++) {
            if (proxy[vertex * 12] >= minimumX) result++;
        }
        return result;
    }
}
