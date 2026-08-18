package com.mine.geometry_node.client.model.render.backend.host.light.integration;

import java.util.Objects;

/** One role observation. Structural evidence may only remain UNVERIFIED. */
public record HostPackLightingRoleEvidence(HostPackLightingRole role,
                                           HostLightingCapabilityState state,
                                           HostLightingEvidenceSource source,
                                           String detail) {
    public HostPackLightingRoleEvidence {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(source, "source");
        detail = detail == null ? "" : detail;
        if ((state == HostLightingCapabilityState.AVAILABLE
                || state == HostLightingCapabilityState.CONFLICT)
                && !source.verifiedPackProof()) {
            throw new IllegalArgumentException("available/conflicting pack role needs verified evidence: " + role);
        }
    }
}
