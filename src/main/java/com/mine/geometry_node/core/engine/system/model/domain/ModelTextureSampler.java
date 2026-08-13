package com.mine.geometry_node.core.engine.system.model.domain;

public record ModelTextureSampler(ModelTextureWrap wrapS, ModelTextureWrap wrapT,
                                  ModelTextureFilter minFilter, ModelTextureFilter magFilter) {
    public ModelTextureSampler {
        if (wrapS == null || wrapT == null || minFilter == null || magFilter == null) {
            throw new IllegalArgumentException("texture sampler fields must not be null");
        }
        if (magFilter.mipmapped()) throw new IllegalArgumentException("magnification filter cannot use mipmaps");
    }

    public static ModelTextureSampler gltfDefault() {
        return new ModelTextureSampler(ModelTextureWrap.REPEAT, ModelTextureWrap.REPEAT,
                ModelTextureFilter.LINEAR_MIPMAP_LINEAR, ModelTextureFilter.LINEAR);
    }
}
