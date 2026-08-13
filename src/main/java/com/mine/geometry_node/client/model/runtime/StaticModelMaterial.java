package com.mine.geometry_node.client.model.runtime;

import com.mine.geometry_node.core.engine.system.model.domain.ModelAlphaMode;

public record StaticModelMaterial(float red, float green, float blue, float alpha,
                                  StaticModelTexture baseColorTexture, ModelAlphaMode alphaMode,
                                  float alphaCutoff, boolean doubleSided,
                                  float emissiveRed, float emissiveGreen, float emissiveBlue,
                                  StaticModelTexture emissiveTexture) {
    public StaticModelMaterial(float red, float green, float blue, float alpha, int imageIndex,
                               ModelAlphaMode alphaMode, float alphaCutoff, boolean doubleSided) {
        this(red, green, blue, alpha,
                imageIndex < 0 ? StaticModelTexture.absent() : new StaticModelTexture(imageIndex,
                        com.mine.geometry_node.core.engine.system.model.domain.ModelTextureSampler.gltfDefault(),
                        com.mine.geometry_node.core.engine.system.model.domain.ModelTextureTransform.identity()),
                alphaMode, alphaCutoff, doubleSided, 0, 0, 0, StaticModelTexture.absent());
    }
}
