package com.mine.geometry_node.client.model.render.backend.host.material;

/** Asset-level material policy used when compiling model-local occluders. */
public enum HostOcclusionClass {
    OPAQUE_BLOCKER,
    MASK_COVERAGE_REQUIRED,
    TRANSMISSIVE
}
