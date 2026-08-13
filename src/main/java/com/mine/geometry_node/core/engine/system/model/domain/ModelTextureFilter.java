package com.mine.geometry_node.core.engine.system.model.domain;

public enum ModelTextureFilter {
    NEAREST(false),
    LINEAR(false),
    NEAREST_MIPMAP_NEAREST(true),
    LINEAR_MIPMAP_NEAREST(true),
    NEAREST_MIPMAP_LINEAR(true),
    LINEAR_MIPMAP_LINEAR(true);

    private final boolean mipmapped;

    ModelTextureFilter(boolean mipmapped) {
        this.mipmapped = mipmapped;
    }

    public boolean mipmapped() {
        return mipmapped;
    }
}
