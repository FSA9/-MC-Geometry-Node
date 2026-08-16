package com.mine.geometry_node.client.model.render.backend.standalone;

import com.mine.geometry_node.core.engine.system.model.domain.ModelAlphaMode;

/** Iris shadow-map phase. Translucent casters must run after opaque depth has been copied. */
public enum ModelShadowPhase {
    OPAQUE,
    TRANSLUCENT;

    boolean accepts(ModelAlphaMode alphaMode) {
        return (alphaMode == ModelAlphaMode.BLEND) == (this == TRANSLUCENT);
    }
}
