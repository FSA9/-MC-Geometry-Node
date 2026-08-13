package com.mine.geometry_node.core.engine.system.model.domain;

public record ModelNormalTextureInfo(ModelTextureInfo texture, float scale) {
    public ModelNormalTextureInfo {
        texture = texture == null ? ModelTextureInfo.absent() : texture;
        ModelNumbers.finite(scale, "normalTexture.scale");
    }

    public static ModelNormalTextureInfo absent() {
        return new ModelNormalTextureInfo(ModelTextureInfo.absent(), 1);
    }
}
