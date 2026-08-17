package com.mine.geometry_node.client.model.render.backend.host.lod;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HostModelLodSelectorTest {
    private static final double[] ERRORS = {0.0, 0.01, 0.03, 0.08};

    @Test
    void selectsCoarsestLevelWithinProjectedPixelError() {
        assertEquals(0, select(2.0, 1080, 70.0, -1));
        assertEquals(1, select(8.0, 1080, 70.0, -1));
        assertEquals(2, select(20.0, 1080, 70.0, -1));
        assertEquals(3, select(50.0, 1080, 70.0, -1));
    }

    @Test
    void fovViewportAndScaleAffectProjectedError() {
        assertEquals(1, select(20.0, 2160, 70.0, -1));
        assertEquals(2, select(20.0, 1080, 70.0, -1));
        assertEquals(1, select(20.0, 1080, 35.0, -1));
        assertEquals(1, select(20.0, 1080, 70.0, -1, 2.0));
    }

    @Test
    void hysteresisPreventsBoundaryOscillation() {
        assertEquals(1, select(13.0, 1080, 70.0, 1));
        assertEquals(1, select(14.0, 1080, 70.0, 1));
        assertEquals(2, select(16.0, 1080, 70.0, 1));
        assertEquals(2, select(13.0, 1080, 70.0, 2));
        assertEquals(1, select(9.0, 1080, 70.0, 2));
    }

    @Test
    void cameraInsideBoundsAlwaysUsesExactSource() {
        assertEquals(0, HostModelLodSelector.select(0, 0, 0, 2, 2, 2,
                1, 1, 1, 70, 1080, 1, ERRORS, 3));
    }

    private static int select(double distance, int height, double fov, int previous) {
        return select(distance, height, fov, previous, 1.0);
    }

    private static int select(double distance, int height, double fov, int previous, double scale) {
        return HostModelLodSelector.select(0, 0, 0, 2, 2, 2,
                2 + distance, 1, 1, fov, height, scale, ERRORS, previous);
    }
}
