package com.mine.geometry_node.client.model.render.backend.host.light.integration;

/** Independently-owned lighting contributions. One capability must never imply another domain. */
public enum HostLightingDomain {
    SUN_SKY,
    PLACED_BLOCK,
    HELD_DYNAMIC,
    MODEL_EMISSIVE
}
