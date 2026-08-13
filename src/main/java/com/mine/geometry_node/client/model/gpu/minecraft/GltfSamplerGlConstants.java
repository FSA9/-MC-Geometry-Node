package com.mine.geometry_node.client.model.gpu.minecraft;

import com.mine.geometry_node.core.engine.system.model.domain.*;

/** Pure numeric mapping kept separate so the exact OpenGL adapter contract is testable without a GL context. */
public final class GltfSamplerGlConstants {
    private GltfSamplerGlConstants() {}

    public static int wrap(ModelTextureWrap wrap) {
        return switch (wrap) {
            case CLAMP_TO_EDGE -> 33071;
            case MIRRORED_REPEAT -> 33648;
            case REPEAT -> 10497;
        };
    }

    public static int min(ModelTextureFilter filter) {
        return switch (filter) {
            case NEAREST -> 9728;
            case LINEAR -> 9729;
            case NEAREST_MIPMAP_NEAREST -> 9984;
            case LINEAR_MIPMAP_NEAREST -> 9985;
            case NEAREST_MIPMAP_LINEAR -> 9986;
            case LINEAR_MIPMAP_LINEAR -> 9987;
        };
    }

    public static int mag(ModelTextureFilter filter) {
        return switch (filter) {
            case NEAREST -> 9728;
            case LINEAR -> 9729;
            default -> throw new IllegalArgumentException("magnification filter cannot use mipmaps");
        };
    }
}
