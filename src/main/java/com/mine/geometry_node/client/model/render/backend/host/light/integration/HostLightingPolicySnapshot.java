package com.mine.geometry_node.client.model.render.backend.host.light.integration;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Immutable F1 capability and arbitration result. No renderer consumes it before F4. */
public record HostLightingPolicySnapshot(long generation,
                                         long environmentGeneration,
                                         long resourceReloadGeneration,
                                         long integrationGeneration,
                                         String descriptorId,
                                         long descriptorRevision,
                                         Map<HostLightingCapability, HostLightingCapabilityEvidence> capabilities,
                                         Map<HostLightingDomain, HostPackDomainEvidence> packDomains,
                                         Map<HostLightingDomain, HostLightingOwnerDecision> decisions) {
    public HostLightingPolicySnapshot {
        if (generation < 0 || environmentGeneration < 0 || resourceReloadGeneration < 0
                || integrationGeneration < 0 || descriptorRevision < -1) {
            throw new IllegalArgumentException("policy generations are invalid");
        }
        descriptorId = descriptorId == null ? "" : descriptorId;
        boolean hasDescriptor = !descriptorId.isBlank();
        if (hasDescriptor != (descriptorRevision >= 0)) {
            throw new IllegalArgumentException("descriptorId and descriptorRevision must be present together");
        }
        capabilities = completeCapabilities(capabilities);
        packDomains = completePackDomains(packDomains);
        decisions = completeDecisions(decisions);
        validatePackAggregates(capabilities, packDomains);
        validateDecisions(capabilities, decisions, hasDescriptor);
    }

    public HostLightingCapabilityEvidence capability(HostLightingCapability capability) {
        return Objects.requireNonNull(capabilities.get(capability), "capability evidence");
    }

    public HostLightingOwnerDecision decision(HostLightingDomain domain) {
        return Objects.requireNonNull(decisions.get(domain), "owner decision");
    }

    public HostPackDomainEvidence packDomain(HostLightingDomain domain) {
        return Objects.requireNonNull(packDomains.get(domain), "pack domain evidence");
    }

    private static Map<HostLightingCapability, HostLightingCapabilityEvidence> completeCapabilities(
            Map<HostLightingCapability, HostLightingCapabilityEvidence> values) {
        Objects.requireNonNull(values, "capabilities");
        EnumMap<HostLightingCapability, HostLightingCapabilityEvidence> copy =
                new EnumMap<>(HostLightingCapability.class);
        copy.putAll(values);
        if (copy.size() != HostLightingCapability.values().length) {
            throw new IllegalArgumentException("policy must classify every lighting capability");
        }
        copy.forEach((capability, evidence) -> {
            if (evidence == null || evidence.capability() != capability) {
                throw new IllegalArgumentException("capability evidence key mismatch: " + capability);
            }
        });
        return Map.copyOf(copy);
    }

    private static Map<HostLightingDomain, HostLightingOwnerDecision> completeDecisions(
            Map<HostLightingDomain, HostLightingOwnerDecision> values) {
        Objects.requireNonNull(values, "decisions");
        EnumMap<HostLightingDomain, HostLightingOwnerDecision> copy = new EnumMap<>(HostLightingDomain.class);
        copy.putAll(values);
        if (copy.size() != HostLightingDomain.values().length) {
            throw new IllegalArgumentException("policy must decide every lighting domain");
        }
        copy.forEach((domain, decision) -> {
            if (decision == null || decision.domain() != domain) {
                throw new IllegalArgumentException("owner decision key mismatch: " + domain);
            }
        });
        return Map.copyOf(copy);
    }

    private static Map<HostLightingDomain, HostPackDomainEvidence> completePackDomains(
            Map<HostLightingDomain, HostPackDomainEvidence> values) {
        Objects.requireNonNull(values, "packDomains");
        EnumMap<HostLightingDomain, HostPackDomainEvidence> copy = new EnumMap<>(HostLightingDomain.class);
        copy.putAll(values);
        if (copy.size() != HostLightingDomain.values().length) {
            throw new IllegalArgumentException("policy must classify pack roles for every lighting domain");
        }
        copy.forEach((domain, evidence) -> {
            if (evidence == null || evidence.domain() != domain) {
                throw new IllegalArgumentException("pack domain evidence key mismatch: " + domain);
            }
        });
        return Map.copyOf(copy);
    }

    private static void validatePackAggregates(
            Map<HostLightingCapability, HostLightingCapabilityEvidence> capabilities,
            Map<HostLightingDomain, HostPackDomainEvidence> packDomains) {
        for (HostLightingDomain domain : HostLightingDomain.values()) {
            HostLightingCapabilityEvidence aggregate = capabilities.get(
                    HostLightingCapability.packCapability(domain));
            if (aggregate.state() != packDomains.get(domain).aggregateState()) {
                throw new IllegalArgumentException("pack aggregate does not match role evidence: " + domain);
            }
        }
    }

    private static void validateDecisions(
            Map<HostLightingCapability, HostLightingCapabilityEvidence> capabilities,
            Map<HostLightingDomain, HostLightingOwnerDecision> decisions,
            boolean hasDescriptor) {
        for (HostLightingDomain domain : HostLightingDomain.values()) {
            HostLightingOwner effective = decisions.get(domain).effectiveOwner();
            HostLightingCapabilityEvidence pack = capabilities.get(
                    HostLightingCapability.packCapability(domain));
            switch (effective) {
                case PACK_NATIVE -> {
                    requireDescriptor(domain, effective, hasDescriptor);
                    requireState(domain, effective, pack, HostLightingCapabilityState.AVAILABLE);
                }
                case EXTERNAL_CONFLICT -> {
                    requireDescriptor(domain, effective, hasDescriptor);
                    requireState(domain, effective, pack, HostLightingCapabilityState.CONFLICT);
                }
                case HOST_UV2 -> {
                    if (domain == HostLightingDomain.SUN_SKY) {
                        throw new IllegalArgumentException("SUN_SKY cannot use HOST_UV2");
                    }
                    requireState(domain, effective,
                            capabilities.get(HostLightingCapability.hostCapability(domain)),
                            HostLightingCapabilityState.AVAILABLE);
                }
                case ENTITY_NATIVE -> requireState(domain, effective,
                        capabilities.get(HostLightingCapability.ENTITY_VERTEX_INPUT),
                        HostLightingCapabilityState.AVAILABLE);
                case CONSTANT -> {
                }
            }
        }
    }

    private static void requireDescriptor(HostLightingDomain domain,
                                          HostLightingOwner owner,
                                          boolean hasDescriptor) {
        if (!hasDescriptor) {
            throw new IllegalArgumentException(domain + " effective owner " + owner
                    + " requires a verified descriptor");
        }
    }

    private static void requireState(HostLightingDomain domain,
                                     HostLightingOwner owner,
                                     HostLightingCapabilityEvidence evidence,
                                     HostLightingCapabilityState required) {
        if (evidence == null || evidence.state() != required) {
            throw new IllegalArgumentException(domain + " effective owner " + owner
                    + " requires capability state " + required);
        }
    }
}
