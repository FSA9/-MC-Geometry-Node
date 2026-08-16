package com.mine.geometry_node.client.model.gpu;

/** Neutral shadow transmission: black absorption with the source texture's alpha coverage. */
final class ModelShadowOpacityProjection {
    private ModelShadowOpacityProjection() {}

    static DecodedModelImage project(DecodedModelImage source) {
        byte[] rgba = source.rgba();
        for (int offset = 0; offset < rgba.length; offset += 4) {
            rgba[offset] = 0;
            rgba[offset + 1] = 0;
            rgba[offset + 2] = 0;
        }
        return new DecodedModelImage(source.width(), source.height(), rgba);
    }
}
