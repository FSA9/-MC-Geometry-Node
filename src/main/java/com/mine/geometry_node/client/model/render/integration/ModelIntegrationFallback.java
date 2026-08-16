package com.mine.geometry_node.client.model.render.integration;

/** Why the effective mode differs from the requested mode. */
public enum ModelIntegrationFallback {
    NONE,
    REQUESTED_MODE_UNAVAILABLE,
    SAFETY_CONTRACT_REJECTED,
    RUNTIME_FAILURE
}
