package com.mine.geometry_node.client.model.runtime;

import com.mine.geometry_node.core.engine.system.model.domain.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModelInstanceBoundsTest {
    @Test
    void transformsEveryCornerForRotationAndNegativeNonUniformScale() {
        ModelBounds local = new ModelBounds(new ModelVector3(0, 0, 0), new ModelVector3(2, 1, 3));
        float half = (float) java.lang.Math.sin(java.lang.Math.PI / 4);

        ModelWorldBounds bounds = ModelInstanceBounds.transform(local, 100_000_000.25, 4, -20,
                0, half, 0, half, -2, 3, 0.5F);

        assertEquals(100_000_000.25, bounds.minX(), 0.0001);
        assertEquals(100_000_001.75, bounds.maxX(), 0.0001);
        assertEquals(4, bounds.minY(), 0.0001);
        assertEquals(7, bounds.maxY(), 0.0001);
        assertEquals(-20, bounds.minZ(), 0.0001);
        assertEquals(-16, bounds.maxZ(), 0.0001);
    }
}
