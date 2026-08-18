package com.mine.geometry_node.client.model.render.backend.host.light.integration;

import java.util.EnumSet;
import java.util.Set;

/** Independent pack roles needed for complete ownership of one lighting domain. */
public enum HostPackLightingRole {
    RECEIVER,
    OCCLUDER_CASTER,
    SOURCE_EMITTER;

    public static Set<HostPackLightingRole> required(HostLightingDomain domain) {
        return domain == HostLightingDomain.MODEL_EMISSIVE
                ? Set.copyOf(EnumSet.allOf(HostPackLightingRole.class))
                : Set.copyOf(EnumSet.of(RECEIVER, OCCLUDER_CASTER));
    }
}
