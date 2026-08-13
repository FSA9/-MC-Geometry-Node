package com.mine.geometry_node.client.model.runtime;

import com.mine.geometry_node.core.engine.system.model.domain.ModelTextureSampler;
import com.mine.geometry_node.core.engine.system.model.domain.ModelTextureTransform;

public record StaticModelTexture(int imageIndex, ModelTextureSampler sampler,
                                 int texCoord, ModelTextureTransform transform) {
    public StaticModelTexture {
        if (imageIndex < -1) throw new IllegalArgumentException("imageIndex must be -1 or greater");
        if (texCoord < 0) throw new IllegalArgumentException("texCoord must not be negative");
        if (sampler == null || transform == null) throw new IllegalArgumentException("texture state must not be null");
    }
    public StaticModelTexture(int imageIndex, ModelTextureSampler sampler, ModelTextureTransform transform) {
        this(imageIndex, sampler, 0, transform);
    }
    public static StaticModelTexture absent() {
        return new StaticModelTexture(-1, ModelTextureSampler.gltfDefault(), 0, ModelTextureTransform.identity());
    }

    public boolean present() {
        return imageIndex >= 0;
    }
}
