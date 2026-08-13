package com.mine.geometry_node.core.engine.system.model.domain;

public record ModelTextureTransform(float offsetX, float offsetY, float rotation,
                                    float scaleX, float scaleY) {
    public ModelTextureTransform {
        ModelNumbers.finite(offsetX, "offsetX");
        ModelNumbers.finite(offsetY, "offsetY");
        ModelNumbers.finite(rotation, "rotation");
        ModelNumbers.finite(scaleX, "scaleX");
        ModelNumbers.finite(scaleY, "scaleY");
    }

    public static ModelTextureTransform identity() {
        return new ModelTextureTransform(0, 0, 0, 1, 1);
    }
}
