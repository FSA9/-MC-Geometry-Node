package com.mine.geometry_node.core.engine.system.model.domain;

public record ModelOcclusionTextureInfo(ModelTextureInfo texture, float strength) {
    public ModelOcclusionTextureInfo {
        texture = texture == null ? ModelTextureInfo.absent() : texture;
        ModelNumbers.finite(strength, "occlusionTexture.strength");
        if (strength < 0 || strength > 1) {
            throw new IllegalArgumentException("occlusionTexture.strength must be within [0, 1]");
        }
    }

    public static ModelOcclusionTextureInfo absent() {
        return new ModelOcclusionTextureInfo(ModelTextureInfo.absent(), 1);
    }
}
