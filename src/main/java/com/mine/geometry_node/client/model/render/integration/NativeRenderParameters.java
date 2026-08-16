package com.mine.geometry_node.client.model.render.integration;

/** Single parameter boundary for experimental HOST_NATIVE rendering behavior. */
public record NativeRenderParameters(NativeTransparencyPolicy transparencyPolicy) {
    private static final NativeRenderParameters FROZEN =
            new NativeRenderParameters(NativeTransparencyPolicy.COMPATIBILITY);

    public NativeRenderParameters {
        if (transparencyPolicy == null) throw new IllegalArgumentException("transparency policy is required");
    }

    public static NativeRenderParameters current() {
        return FROZEN;
    }

    public boolean preservesBlend(boolean dedicatedHostProgram) {
        return transparencyPolicy.preservesBlend(dedicatedHostProgram);
    }
}
