package com.mine.geometry_node.client.model.render.geometry;

import com.mine.geometry_node.core.engine.system.model.math.ModelTransform;
import java.util.Objects;

/** Immutable input to a render frame; the service owns the asset lease. */
public record ModelRenderInstance(String id, ModelRenderAsset asset, int scene, ModelTransform transform, boolean visible) {
    public ModelRenderInstance { Objects.requireNonNull(id); Objects.requireNonNull(asset); Objects.requireNonNull(transform); }
}
