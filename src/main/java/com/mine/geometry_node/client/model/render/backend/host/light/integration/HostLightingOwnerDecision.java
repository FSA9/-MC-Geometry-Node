package com.mine.geometry_node.client.model.render.backend.host.light.integration;

import java.util.Objects;

/** Preferred route and currently usable route are separate to prevent false HOST_UV2 reporting. */
public record HostLightingOwnerDecision(HostLightingDomain domain,
                                        HostLightingOwner preferredOwner,
                                        HostLightingOwner effectiveOwner,
                                        String reason) {
    public HostLightingOwnerDecision {
        Objects.requireNonNull(domain, "domain");
        Objects.requireNonNull(preferredOwner, "preferredOwner");
        Objects.requireNonNull(effectiveOwner, "effectiveOwner");
        reason = reason == null ? "" : reason;
        if (reason.isBlank()) throw new IllegalArgumentException("owner decision reason must not be blank");
    }
}
