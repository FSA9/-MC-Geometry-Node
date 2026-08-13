package com.mine.geometry_node.client.model.gpu;

public record ModelGpuTextureKey(int imageIndex, ModelTextureColorSpace colorSpace) {
    public ModelGpuTextureKey {
        if (imageIndex < 0) throw new IllegalArgumentException("imageIndex must be non-negative");
        if (colorSpace == null) throw new IllegalArgumentException("colorSpace must not be null");
    }
}
