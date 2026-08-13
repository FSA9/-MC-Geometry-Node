package com.mine.geometry_node.client.model.render.compat;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ModelCompatibilityProjectionTest {
    @Test
    void gpuSkinningLossMakesEntityProjectionUnselectable() {
        assertThrows(IllegalArgumentException.class, () -> new ModelCompatibilityProjection(true,
                ModelCompatibilityProfile.ENTITY, 0, true, true, true, true, false,
                Set.of(ModelCompatibilityLoss.GPU_SKINNING_UNREPRESENTABLE)));
        assertFalse(new ModelCompatibilityProjection(false, ModelCompatibilityProfile.ENTITY,
                0, true, true, true, true, false,
                Set.of(ModelCompatibilityLoss.GPU_SKINNING_UNREPRESENTABLE)).selectable());
    }
}
