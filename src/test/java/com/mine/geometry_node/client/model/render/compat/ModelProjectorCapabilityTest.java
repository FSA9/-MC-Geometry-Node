package com.mine.geometry_node.client.model.render.compat;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ModelProjectorCapabilityTest {
    @Test void onlyActiveCapabilityEnablesLabPbrAuxiliaries() {
        assertTrue(ModelProjectorCapability.ACTIVE.auxiliaryEnabled());
        assertEquals(ModelCompatibilityProfile.IRIS_1_11_LABPBR,
                ModelProjectorCapability.ACTIVE.profile());
        assertFalse(ModelProjectorCapability.INACTIVE.auxiliaryEnabled());
        assertFalse(ModelProjectorCapability.RUNTIME_FAILED.auxiliaryEnabled());
    }

    @Test void runtimeFailureFallsBackToEntityWithAnExplicitFrameLoss() {
        assertEquals(ModelCompatibilityProfile.ENTITY, ModelProjectorCapability.RUNTIME_FAILED.profile());
        assertEquals(Set.of(ModelCompatibilityLoss.PROJECTOR_RUNTIME_UNAVAILABLE),
                ModelProjectorCapability.RUNTIME_FAILED.frameLosses());
        assertTrue(ModelProjectorCapability.INACTIVE.frameLosses().isEmpty());
    }
}
