package com.mine.geometry_node.client.model.render.backend.host.light.integration;

/** Evidence classification. UNVERIFIED is intentionally distinct from AVAILABLE. */
public enum HostLightingCapabilityState {
    AVAILABLE,
    UNAVAILABLE,
    UNVERIFIED,
    CONFLICT
}
