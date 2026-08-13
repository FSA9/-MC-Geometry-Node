package com.mine.geometry_node.core.engine.system.model.domain;

public record ModelMaterial(String name, float red, float green, float blue, float alpha,
                            ModelTextureInfo baseColorTexture, ModelAlphaMode alphaMode, float alphaCutoff,
                            boolean doubleSided, float emissiveRed, float emissiveGreen, float emissiveBlue,
                            ModelTextureInfo emissiveTexture, ModelPbrMetallicRoughness metallicRoughness,
                            ModelNormalTextureInfo normalTexture, ModelOcclusionTextureInfo occlusionTexture) {
    public ModelMaterial {
        name = name == null ? "" : name;
        red = unit(red, "red"); green = unit(green, "green"); blue = unit(blue, "blue"); alpha = unit(alpha, "alpha");
        baseColorTexture = baseColorTexture == null ? ModelTextureInfo.absent() : baseColorTexture;
        if (alphaMode == null) throw new IllegalArgumentException("alphaMode must not be null");
        ModelNumbers.finite(alphaCutoff, "alphaCutoff");
        if (alphaCutoff < 0) throw new IllegalArgumentException("alphaCutoff must not be negative");
        emissiveRed = unit(emissiveRed, "emissiveRed");
        emissiveGreen = unit(emissiveGreen, "emissiveGreen");
        emissiveBlue = unit(emissiveBlue, "emissiveBlue");
        emissiveTexture = emissiveTexture == null ? ModelTextureInfo.absent() : emissiveTexture;
        metallicRoughness = metallicRoughness == null ? ModelPbrMetallicRoughness.gltfDefault() : metallicRoughness;
        normalTexture = normalTexture == null ? ModelNormalTextureInfo.absent() : normalTexture;
        occlusionTexture = occlusionTexture == null ? ModelOcclusionTextureInfo.absent() : occlusionTexture;
    }

    public ModelMaterial(String name, float red, float green, float blue, float alpha,
                         ModelTextureInfo baseColorTexture, ModelAlphaMode alphaMode, float alphaCutoff,
                         boolean doubleSided, float emissiveRed, float emissiveGreen, float emissiveBlue,
                         ModelTextureInfo emissiveTexture) {
        this(name, red, green, blue, alpha, baseColorTexture, alphaMode, alphaCutoff, doubleSided,
                emissiveRed, emissiveGreen, emissiveBlue, emissiveTexture,
                ModelPbrMetallicRoughness.gltfDefault(), ModelNormalTextureInfo.absent(),
                ModelOcclusionTextureInfo.absent());
    }

    public ModelMaterial(String name, float red, float green, float blue, float alpha,
                         int baseColorTexture, ModelAlphaMode alphaMode, float alphaCutoff,
                         boolean doubleSided) {
        this(name, red, green, blue, alpha, new ModelTextureInfo(baseColorTexture, null), alphaMode,
                alphaCutoff, doubleSided, 0, 0, 0, ModelTextureInfo.absent());
    }

    public static ModelMaterial defaultMaterial() {
        return new ModelMaterial("default", 1, 1, 1, 1, ModelTextureInfo.absent(),
                ModelAlphaMode.OPAQUE, 0.5F, false, 0, 0, 0, ModelTextureInfo.absent());
    }

    private static float unit(float value, String name) {
        ModelNumbers.finite(value, name);
        if (value < 0.0F || value > 1.0F) throw new IllegalArgumentException(name + " must be within [0, 1]");
        return value;
    }
}
