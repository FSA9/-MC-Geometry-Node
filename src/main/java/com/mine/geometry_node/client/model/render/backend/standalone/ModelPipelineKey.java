package com.mine.geometry_node.client.model.render.backend.standalone;

import com.mine.geometry_node.core.engine.system.model.domain.ModelAlphaMode;
import com.mine.geometry_node.core.engine.system.model.domain.ModelVertexLayout;

public record ModelPipelineKey(ModelVertexLayout layout, ModelAlphaMode alphaMode,
                               boolean doubleSided, boolean mirrored, boolean translucent,
                               boolean skinned) { }
