package com.mine.geometry_node.client.model.render.integration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NativeRenderParametersTest {
    @Test
    void currentParametersFreezeNativeBlendToCompatibilityFallback() {
        NativeRenderParameters parameters = NativeRenderParameters.current();
        assertEquals(NativeTransparencyPolicy.COMPATIBILITY, parameters.transparencyPolicy());
        assertFalse(parameters.preservesBlend(false));
        assertFalse(parameters.preservesBlend(true));
    }

    @Test
    void futurePoliciesHaveExplicitCapabilitySemantics() {
        assertFalse(NativeTransparencyPolicy.AUTO.preservesBlend(false));
        assertTrue(NativeTransparencyPolicy.AUTO.preservesBlend(true));
        assertTrue(NativeTransparencyPolicy.EXPERIMENTAL_FORCE.preservesBlend(false));
    }
}
