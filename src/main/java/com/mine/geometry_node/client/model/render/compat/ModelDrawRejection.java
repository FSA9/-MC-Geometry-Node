package com.mine.geometry_node.client.model.render.compat;

/** Stable reasons why HOST_NATIVE deliberately did not submit a draw. */
public enum ModelDrawRejection {
    UNSUPPORTED_SKINNING,
    GEOMETRY_PROJECTION_FAILED,
    TEXTURE_PROJECTION_FAILED,
    SINGULAR_TRANSFORM
}
