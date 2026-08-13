package com.mine.geometry_node.core.engine.system.model.domain;

public record ModelTexture(String name, int imageIndex, ModelTextureSampler sampler) {
    public ModelTexture {
        name = name == null ? "" : name;
        if (imageIndex < 0) throw new IllegalArgumentException("imageIndex must not be negative");
        sampler = sampler == null ? ModelTextureSampler.gltfDefault() : sampler;
    }

    public ModelTexture(String name, int imageIndex) {
        this(name, imageIndex, ModelTextureSampler.gltfDefault());
    }
}
