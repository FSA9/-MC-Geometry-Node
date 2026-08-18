package com.mine.geometry_node.client.model.render.backend.host.geometry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HostVertexOccurrenceTest {
    @Test
    void immediateAndClusterOrderedSourceUseCanonicalTriangleOccurrence() {
        assertEquals(12, HostVertexOccurrence.source(4, 0, false));
        assertEquals(13, HostVertexOccurrence.source(4, 1, false));
        assertEquals(14, HostVertexOccurrence.source(4, 2, false));

        // A static/ordered slot may map to source triangle 4; its samples remain 12..14,
        // not the slot's own 0..2 occurrence range.
        assertEquals(12, HostVertexOccurrence.source(4, 0, false));
    }

    @Test
    void spatialClusterSlotUsesMappedSourceOccurrenceRatherThanSlotOccurrence() {
        float[] vertices = new float[HostSpatialClusterPlan.MIN_CLUSTERED_TRIANGLES * 36];
        for (int triangle = 0; triangle < HostSpatialClusterPlan.MIN_CLUSTERED_TRIANGLES; triangle++) {
            float x = HostSpatialClusterPlan.MIN_CLUSTERED_TRIANGLES - triangle;
            for (int corner = 0; corner < 3; corner++) {
                int offset = triangle * 36 + corner * 12;
                vertices[offset] = x;
                vertices[offset + 1] = corner == 1 ? 1 : 0;
                vertices[offset + 2] = corner == 2 ? 1 : 0;
            }
        }
        HostSpatialClusterPlan clusters = HostSpatialClusterPlan.build(vertices);
        int mappedSourceTriangle = clusters.sourceTriangle(0);

        assertEquals(HostSpatialClusterPlan.Mode.HIERARCHICAL, clusters.mode());
        assertNotEquals(0, mappedSourceTriangle, "fixture must produce a reordered first cluster slot");
        assertEquals(mappedSourceTriangle * 3,
                HostVertexOccurrence.source(mappedSourceTriangle, 0, false));
    }

    @Test
    void mirroredOutputSwapsBothGeometryAndLightOccurrences() {
        assertEquals(12, HostVertexOccurrence.source(4, 0, true));
        assertEquals(14, HostVertexOccurrence.source(4, 1, true));
        assertEquals(13, HostVertexOccurrence.source(4, 2, true));
    }

    @Test
    void proxyOccurrencesFollowTheCompleteSourceRange() {
        assertEquals(30, HostVertexOccurrence.proxy(10, 0, 0, false));
        assertEquals(37, HostVertexOccurrence.proxy(10, 2, 1, false));
        assertEquals(38, HostVertexOccurrence.proxy(10, 2, 1, true));
        assertEquals(37, HostVertexOccurrence.proxy(10, 2, 2, true));
    }

    @Test
    void rejectsInvalidOccurrenceCoordinates() {
        assertThrows(IllegalArgumentException.class, () -> HostVertexOccurrence.source(-1, 0, false));
        assertThrows(IllegalArgumentException.class, () -> HostVertexOccurrence.source(0, 3, false));
        assertThrows(IllegalArgumentException.class, () -> HostVertexOccurrence.proxy(-1, 0, 0, false));
    }
}
