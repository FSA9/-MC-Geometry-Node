package com.mine.geometry_node.client.model.render.backend.host.light.integration;

import java.util.Objects;

/** Immutable classified evidence. It retains no private Iris object or reflection handle. */
public record HostLightingCapabilityEvidence(HostLightingCapability capability,
                                             HostLightingCapabilityState state,
                                             HostLightingEvidenceSource source,
                                             String detail) {
    public HostLightingCapabilityEvidence {
        Objects.requireNonNull(capability, "capability");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(source, "source");
        detail = detail == null ? "" : detail;
        if (capability.packCapability()
                && (state == HostLightingCapabilityState.AVAILABLE
                || state == HostLightingCapabilityState.CONFLICT)
                && !source.verifiedPackProof()) {
            throw new IllegalArgumentException("PACK capability needs verified evidence: " + capability);
        }
        if (state == HostLightingCapabilityState.AVAILABLE && capability.hostUv2Capability()
                && source != HostLightingEvidenceSource.HOST_IMPLEMENTATION) {
            throw new IllegalArgumentException("HOST UV2 capability needs HOST implementation evidence");
        }
        if (state == HostLightingCapabilityState.AVAILABLE
                && capability == HostLightingCapability.ENTITY_VERTEX_INPUT
                && source != HostLightingEvidenceSource.STANDARD_ENTITY_CONTRACT) {
            throw new IllegalArgumentException("ENTITY input needs standard contract evidence");
        }
    }

    public boolean available() { return state == HostLightingCapabilityState.AVAILABLE; }
}
