package com.mine.geometry_node.core.engine.system.model.domain;

public record ModelPbrMetallicRoughness(float metallicFactor, float roughnessFactor,
                                        ModelTextureInfo texture) {
    public ModelPbrMetallicRoughness {
        metallicFactor = unit(metallicFactor, "metallicFactor");
        roughnessFactor = unit(roughnessFactor, "roughnessFactor");
        texture = texture == null ? ModelTextureInfo.absent() : texture;
    }

    public static ModelPbrMetallicRoughness gltfDefault() {
        return new ModelPbrMetallicRoughness(1, 1, ModelTextureInfo.absent());
    }

    private static float unit(float value, String name) {
        ModelNumbers.finite(value, name);
        if (value < 0 || value > 1) throw new IllegalArgumentException(name + " must be within [0, 1]");
        return value;
    }
}
