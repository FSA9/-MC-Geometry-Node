package com.mine.geometry_node.client.model.render;

import com.mine.geometry_node.core.engine.system.model.domain.ModelAlphaMode;
import com.mine.geometry_node.core.engine.system.model.domain.ModelVertexLayout;

public record ModelPipelineKey(ModelVertexLayout layout, ModelAlphaMode alphaMode,
                               boolean baseColorTextured, boolean emissiveTextured,
                               boolean doubleSided, boolean mirrored, boolean translucent,
                               boolean skinned) {
    /** Compatibility name used by the M8 draw contract. */
    public boolean textured() {
        return baseColorTextured;
    }
}
