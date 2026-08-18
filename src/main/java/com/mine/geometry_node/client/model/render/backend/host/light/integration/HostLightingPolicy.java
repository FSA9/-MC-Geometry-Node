package com.mine.geometry_node.client.model.render.backend.host.light.integration;

import java.util.Map;
import java.util.Objects;

/** Owns the atomically-published F1 policy snapshot; render bindings remain unchanged until F4. */
public final class HostLightingPolicy {
    private static long generation;
    private static long descriptorEpoch;
    private static long capturedDescriptorEpoch;
    private static HostPackLightingDescriptor descriptor;
    private static volatile HostLightingPolicySnapshot current = initial();

    private HostLightingPolicy() {
    }

    public static HostLightingPolicySnapshot snapshot() { return current; }

    public static synchronized HostLightingPolicySnapshot capture(HostLightingEnvironmentSnapshot environment) {
        Objects.requireNonNull(environment, "environment");
        if (current.environmentGeneration() == environment.generation()
                && capturedDescriptorEpoch == descriptorEpoch) {
            return current;
        }
        HostLightingArbitrator.Classified classified = HostLightingArbitrator.classify(environment, descriptor);
        String descriptorId = descriptor == null ? "" : descriptor.descriptorId();
        long descriptorRevision = descriptor == null ? -1 : descriptor.revision();
        HostLightingPolicySnapshot observed = new HostLightingPolicySnapshot(
                current.generation(), environment.generation(), environment.resourceReloadGeneration(),
                environment.integrationGeneration(), descriptorId, descriptorRevision,
                classified.capabilities(), classified.packDomains(), classified.decisions());
        long nextGeneration = sameStablePolicy(current, observed) ? current.generation() : ++generation;
        current = new HostLightingPolicySnapshot(nextGeneration, environment.generation(),
                environment.resourceReloadGeneration(), environment.integrationGeneration(),
                descriptorId, descriptorRevision, classified.capabilities(), classified.packDomains(),
                classified.decisions());
        capturedDescriptorEpoch = descriptorEpoch;
        return current;
    }

    public static synchronized HostLightingPolicySnapshot installPackDescriptor(
            HostPackLightingDescriptor next) {
        descriptor = Objects.requireNonNull(next, "descriptor");
        descriptorEpoch++;
        return capture(HostLightingEnvironment.snapshot());
    }

    public static synchronized HostLightingPolicySnapshot clearPackDescriptor() {
        descriptor = null;
        descriptorEpoch++;
        return capture(HostLightingEnvironment.snapshot());
    }

    private static boolean sameStablePolicy(HostLightingPolicySnapshot left,
                                            HostLightingPolicySnapshot right) {
        return left.resourceReloadGeneration() == right.resourceReloadGeneration()
                && left.integrationGeneration() == right.integrationGeneration()
                && left.descriptorId().equals(right.descriptorId())
                && left.descriptorRevision() == right.descriptorRevision()
                && sameCapabilities(left.capabilities(), right.capabilities())
                && samePackDomains(left.packDomains(), right.packDomains())
                && left.decisions().equals(right.decisions());
    }

    /** Diagnostic detail strings are intentionally excluded from generation equality. */
    private static boolean sameCapabilities(
            Map<HostLightingCapability, HostLightingCapabilityEvidence> left,
            Map<HostLightingCapability, HostLightingCapabilityEvidence> right) {
        for (HostLightingCapability capability : HostLightingCapability.values()) {
            HostLightingCapabilityEvidence a = left.get(capability);
            HostLightingCapabilityEvidence b = right.get(capability);
            if (a == null || b == null || a.state() != b.state() || a.source() != b.source()) return false;
        }
        return true;
    }

    private static boolean samePackDomains(Map<HostLightingDomain, HostPackDomainEvidence> left,
                                           Map<HostLightingDomain, HostPackDomainEvidence> right) {
        for (HostLightingDomain domain : HostLightingDomain.values()) {
            HostPackDomainEvidence a = left.get(domain);
            HostPackDomainEvidence b = right.get(domain);
            if (a == null || b == null) return false;
            for (HostPackLightingRole role : HostPackLightingRole.required(domain)) {
                HostPackLightingRoleEvidence ar = a.role(role);
                HostPackLightingRoleEvidence br = b.role(role);
                if (ar.state() != br.state() || ar.source() != br.source()) return false;
            }
        }
        return true;
    }

    private static HostLightingPolicySnapshot initial() {
        HostLightingEnvironmentSnapshot environment = HostLightingEnvironment.snapshot();
        HostLightingArbitrator.Classified classified = HostLightingArbitrator.classify(environment, null);
        return new HostLightingPolicySnapshot(0, environment.generation(), environment.resourceReloadGeneration(),
                environment.integrationGeneration(), "", -1, classified.capabilities(),
                classified.packDomains(), classified.decisions());
    }
}
