package com.mine.geometry_node.client.model.render.backend.host.light.integration;

/** The single effective producer of a lighting domain. */
public enum HostLightingOwner {
    ENTITY_NATIVE,
    PACK_NATIVE,
    HOST_UV2,
    CONSTANT,
    EXTERNAL_CONFLICT
}
