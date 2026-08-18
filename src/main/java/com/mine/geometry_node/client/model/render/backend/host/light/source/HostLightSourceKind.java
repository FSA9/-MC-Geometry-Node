package com.mine.geometry_node.client.model.render.backend.host.light.source;

/** Source provenance is stable identity, not a shaderpack-specific light type. */
public enum HostLightSourceKind {
    PLACED_BLOCK,
    HELD_DYNAMIC,
    MODEL_EMISSIVE
}
