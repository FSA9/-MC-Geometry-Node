package com.mine.geometry_node.core.engine.system.model.domain;

public record ModelTextureInfo(int textureIndex, int texCoordSet, ModelTextureTransform transform) {
    public ModelTextureInfo {
        if (textureIndex < -1) throw new IllegalArgumentException("textureIndex must be -1 or greater");
        if (texCoordSet < 0) throw new IllegalArgumentException("texCoordSet must not be negative");
        transform = transform == null ? ModelTextureTransform.identity() : transform;
    }

    public ModelTextureInfo(int textureIndex, ModelTextureTransform transform) { this(textureIndex, 0, transform); }

    public static ModelTextureInfo absent() {
        return new ModelTextureInfo(-1, 0, ModelTextureTransform.identity());
    }
}
