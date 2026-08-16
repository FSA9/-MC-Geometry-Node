package com.mine.geometry_node.client.model.render.integration;

/** Controls whether HOST_NATIVE may preserve glTF BLEND semantics. */
public enum NativeTransparencyPolicy {
    COMPATIBILITY,
    AUTO,
    EXPERIMENTAL_FORCE;

    public boolean preservesBlend(boolean dedicatedHostProgram) {
        return switch (this) {
            case COMPATIBILITY -> false;
            case AUTO -> dedicatedHostProgram;
            case EXPERIMENTAL_FORCE -> true;
        };
    }
}
