package com.mine.geometry_node.client.model.render.backend.host.light.integration;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Explicit, version-bound proof supplied by a public adapter or a recorded manual acceptance. */
public record HostPackLightingDescriptor(String descriptorId,
                                         long revision,
                                         long resourceReloadGeneration,
                                         long integrationGeneration,
                                         HostLightingEvidenceSource source,
                                         Map<HostLightingDomain, HostPackDomainEvidence> domains) {
    public HostPackLightingDescriptor {
        if (descriptorId == null || descriptorId.isBlank()) {
            throw new IllegalArgumentException("descriptorId must not be blank");
        }
        if (revision < 0 || resourceReloadGeneration < 0 || integrationGeneration < 0) {
            throw new IllegalArgumentException("descriptor generations must not be negative");
        }
        Objects.requireNonNull(source, "source");
        if (!source.verifiedPackProof()) {
            throw new IllegalArgumentException("descriptor source must be verified pack proof");
        }
        Objects.requireNonNull(domains, "domains");
        EnumMap<HostLightingDomain, HostPackDomainEvidence> copy =
                new EnumMap<>(HostLightingDomain.class);
        copy.putAll(domains);
        if (copy.size() != HostLightingDomain.values().length) {
            throw new IllegalArgumentException("descriptor must classify every lighting domain");
        }
        copy.forEach((domain, evidence) -> {
            if (evidence == null || evidence.domain() != domain) {
                throw new IllegalArgumentException("descriptor domain evidence key mismatch: " + domain);
            }
            for (HostPackLightingRoleEvidence role : evidence.roles().values()) {
                if (role.source() != source) {
                    throw new IllegalArgumentException("descriptor role source mismatch: " + domain);
                }
            }
        });
        domains = Map.copyOf(copy);
    }

    public boolean matches(HostLightingEnvironmentSnapshot environment) {
        return environment.hostNativeRequired()
                && resourceReloadGeneration == environment.resourceReloadGeneration()
                && integrationGeneration == environment.integrationGeneration();
    }

    public HostPackDomainEvidence domain(HostLightingDomain domain) {
        return Objects.requireNonNull(domains.get(domain), "domain evidence");
    }
}
