package com.mine.geometry_node.client.model.render.backend.host.light.integration;

/** Provenance determines whether evidence is strong enough to select PACK_NATIVE. */
public enum HostLightingEvidenceSource {
    NONE(false),
    STANDARD_ENTITY_CONTRACT(false),
    HOST_IMPLEMENTATION(false),
    STRUCTURAL_OBSERVATION(false),
    PUBLIC_RUNTIME_API(true),
    VERIFIED_ADAPTER_DESCRIPTOR(true),
    MANUAL_ACCEPTANCE(true);

    private final boolean verifiedPackProof;

    HostLightingEvidenceSource(boolean verifiedPackProof) {
        this.verifiedPackProof = verifiedPackProof;
    }

    public boolean verifiedPackProof() { return verifiedPackProof; }
}
