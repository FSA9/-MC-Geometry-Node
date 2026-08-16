package com.mine.geometry_node.client.model.render.backend.standalone;

import com.mine.geometry_node.core.engine.system.model.domain.ModelAlphaMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelShadowPolicyTest {
    @Test
    void everyFullOpacityMaterialMayEnterTheMatchingIrisShadowProgram() {
        assertTrue(ModelShadowPolicy.castsShadow(ModelAlphaMode.OPAQUE, 1, false, ModelShadowPhase.OPAQUE));
        assertTrue(ModelShadowPolicy.castsShadow(ModelAlphaMode.MASK, 1, false, ModelShadowPhase.OPAQUE));
        assertTrue(ModelShadowPolicy.castsShadow(ModelAlphaMode.BLEND, 1, false, ModelShadowPhase.TRANSLUCENT));
        assertFalse(ModelShadowPolicy.castsShadow(ModelAlphaMode.BLEND, 1, false, ModelShadowPhase.OPAQUE));
        assertFalse(ModelShadowPolicy.castsShadow(ModelAlphaMode.MASK, 1, false, ModelShadowPhase.TRANSLUCENT));
        assertFalse(ModelShadowPolicy.castsShadow(ModelAlphaMode.OPAQUE, 0.5F, false, ModelShadowPhase.OPAQUE));
    }

    @Test
    void opaqueMainFallbackAlsoUsesOpaqueShadowSemantics() {
        ModelAlphaMode effective = ModelShadowPolicy.effectiveAlphaMode(ModelAlphaMode.BLEND, 1, true);
        assertTrue(effective == ModelAlphaMode.OPAQUE);
        assertTrue(ModelShadowPolicy.castsShadow(effective, 1, true, ModelShadowPhase.OPAQUE));
        assertFalse(ModelShadowPolicy.castsShadow(effective, 1, true, ModelShadowPhase.TRANSLUCENT));
    }
}
