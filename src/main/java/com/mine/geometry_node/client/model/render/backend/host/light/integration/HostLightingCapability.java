package com.mine.geometry_node.client.model.render.backend.host.light.integration;

/** Stable capabilities used by owner arbitration; pack contributions remain domain-specific. */
public enum HostLightingCapability {
    ENTITY_VERTEX_INPUT(false),
    LABPBR_ATTACHMENTS(false),
    SUN_SHADOW_REPLAY(false),
    PACK_SUN_SKY(true),
    PACK_PLACED_BLOCK(true),
    PACK_HELD_DYNAMIC(true),
    PACK_MODEL_EMISSIVE(true),
    HOST_PLACED_BLOCK_UV2(false),
    HOST_HELD_DYNAMIC_UV2(false),
    HOST_MODEL_EMISSIVE_UV2(false);

    private final boolean packCapability;

    HostLightingCapability(boolean packCapability) {
        this.packCapability = packCapability;
    }

    public boolean packCapability() { return packCapability; }

    public boolean hostUv2Capability() {
        return this == HOST_PLACED_BLOCK_UV2 || this == HOST_HELD_DYNAMIC_UV2
                || this == HOST_MODEL_EMISSIVE_UV2;
    }

    public static HostLightingCapability packCapability(HostLightingDomain domain) {
        return switch (domain) {
            case SUN_SKY -> PACK_SUN_SKY;
            case PLACED_BLOCK -> PACK_PLACED_BLOCK;
            case HELD_DYNAMIC -> PACK_HELD_DYNAMIC;
            case MODEL_EMISSIVE -> PACK_MODEL_EMISSIVE;
        };
    }

    public static HostLightingCapability hostCapability(HostLightingDomain domain) {
        return switch (domain) {
            case SUN_SKY -> throw new IllegalArgumentException("SUN_SKY uses the standard ENTITY contract");
            case PLACED_BLOCK -> HOST_PLACED_BLOCK_UV2;
            case HELD_DYNAMIC -> HOST_HELD_DYNAMIC_UV2;
            case MODEL_EMISSIVE -> HOST_MODEL_EMISSIVE_UV2;
        };
    }
}
