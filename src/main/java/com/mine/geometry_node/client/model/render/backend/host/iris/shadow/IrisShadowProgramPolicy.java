package com.mine.geometry_node.client.model.render.backend.host.iris.shadow;

import com.mine.geometry_node.core.engine.system.model.domain.ModelAlphaMode;

/** Maps glTF alpha semantics to the closest public Iris shadow program. */
public final class IrisShadowProgramPolicy {
    private IrisShadowProgramPolicy() {}

    public static String programName(ModelAlphaMode alphaMode) {
        return alphaMode == ModelAlphaMode.BLEND ? "SHADOW_TRANSLUCENT" : "SHADOW_ENTITIES";
    }
}
