package com.mine.geometry_node.client.model.render.backend.host.iris.shadow;

import com.mine.geometry_node.core.engine.system.model.domain.ModelAlphaMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IrisShadowProgramPolicyTest {
    @Test
    void blendUsesTranslucentWhileOpaqueAndMaskUseEntityShadowProgram() {
        assertEquals("SHADOW_ENTITIES", IrisShadowProgramPolicy.programName(ModelAlphaMode.OPAQUE));
        assertEquals("SHADOW_ENTITIES", IrisShadowProgramPolicy.programName(ModelAlphaMode.MASK));
        assertEquals("SHADOW_TRANSLUCENT", IrisShadowProgramPolicy.programName(ModelAlphaMode.BLEND));
    }
}
