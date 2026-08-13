package com.mine.geometry_node.client.model.runtime;

import org.joml.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class ModelInstancePlacementTest {
    @Test
    void rejectsNonFinitePosition() {
        assertThrows(IllegalArgumentException.class, () -> new ModelInstancePlacement(
                new Vector3d(Double.NaN, 0, 0), new Quaternionf(), new Vector3f(1),
                false, false, 1, 1, 1, 1));
    }

    @Test
    void rejectsZeroQuaternion() {
        assertThrows(IllegalArgumentException.class, () -> new ModelInstancePlacement(
                new Vector3d(), new Quaternionf(0, 0, 0, 0), new Vector3f(1),
                false, false, 1, 1, 1, 1));
    }
}
