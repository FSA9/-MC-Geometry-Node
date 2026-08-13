package com.mine.geometry_node.client.model.render.compat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ModelProjectorCapabilityTest {
    @Test void positiveSamplerEvidenceEnablesUnverifiedLabPbrAuxiliaries() {
        assertTrue(ModelProjectorCapability.ACTIVE.auxiliaryEnabled());
        assertTrue(ModelProjectorCapability.UNVERIFIED.auxiliaryEnabled());
        assertEquals(ModelCompatibilityProfile.HOST_NATIVE_LABPBR,
                ModelProjectorCapability.ACTIVE.profile());
        assertFalse(ModelProjectorCapability.INACTIVE.auxiliaryEnabled());
        assertFalse(ModelProjectorCapability.FAILED.auxiliaryEnabled());
    }

    @Test void probeStateIsSeparateFromMaterialSemanticLosses() {
        assertEquals(ModelIntegrationVerification.PENDING, ModelProjectorCapability.PENDING.verification());
        assertEquals(ModelIntegrationVerification.UNVERIFIED, ModelProjectorCapability.UNVERIFIED.verification());
        assertEquals(ModelIntegrationVerification.NOT_APPLICABLE, ModelProjectorCapability.ABSENT.verification());
        assertEquals(ModelIntegrationVerification.NOT_APPLICABLE, ModelProjectorCapability.INACTIVE.verification());
        assertTrue(ModelProjectorCapability.FAILED.runtimeFault());
        assertFalse(ModelProjectorCapability.UNVERIFIED.runtimeFault());
        assertEquals(ModelCompatibilityProfile.HOST_NATIVE_ENTITY, ModelProjectorCapability.FAILED.profile());
    }
}
