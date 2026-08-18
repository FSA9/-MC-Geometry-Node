package com.mine.geometry_node.client.model.render.backend.host.light.integration;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Complete role evidence; receiver/emitter roles trigger ownership while caster-only may supplement ENTITY. */
public record HostPackDomainEvidence(HostLightingDomain domain,
                                     Map<HostPackLightingRole, HostPackLightingRoleEvidence> roles) {
    public HostPackDomainEvidence {
        Objects.requireNonNull(domain, "domain");
        Objects.requireNonNull(roles, "roles");
        EnumMap<HostPackLightingRole, HostPackLightingRoleEvidence> copy =
                new EnumMap<>(HostPackLightingRole.class);
        copy.putAll(roles);
        Set<HostPackLightingRole> required = HostPackLightingRole.required(domain);
        if (!copy.keySet().equals(required)) {
            throw new IllegalArgumentException(domain + " requires exact pack roles " + required);
        }
        copy.forEach((role, evidence) -> {
            if (evidence == null || evidence.role() != role) {
                throw new IllegalArgumentException("pack role evidence key mismatch: " + role);
            }
        });
        roles = Map.copyOf(copy);
    }

    public HostPackLightingRoleEvidence role(HostPackLightingRole role) {
        HostPackLightingRoleEvidence evidence = roles.get(role);
        if (evidence == null) throw new IllegalArgumentException(role + " is not required for " + domain);
        return evidence;
    }

    public HostLightingCapabilityState aggregateState() {
        for (HostPackLightingRoleEvidence evidence : roles.values()) {
            if (evidence.state() == HostLightingCapabilityState.CONFLICT) {
                return HostLightingCapabilityState.CONFLICT;
            }
        }
        HostPackLightingRole trigger = domain == HostLightingDomain.MODEL_EMISSIVE
                ? HostPackLightingRole.SOURCE_EMITTER : HostPackLightingRole.RECEIVER;
        HostLightingCapabilityState triggerState = role(trigger).state();
        if (triggerState == HostLightingCapabilityState.UNVERIFIED) {
            return HostLightingCapabilityState.UNVERIFIED;
        }
        if (triggerState == HostLightingCapabilityState.UNAVAILABLE) {
            return HostLightingCapabilityState.UNAVAILABLE;
        }
        for (HostPackLightingRoleEvidence evidence : roles.values()) {
            if (evidence.state() != HostLightingCapabilityState.AVAILABLE) {
                return HostLightingCapabilityState.CONFLICT;
            }
        }
        return HostLightingCapabilityState.AVAILABLE;
    }
}
