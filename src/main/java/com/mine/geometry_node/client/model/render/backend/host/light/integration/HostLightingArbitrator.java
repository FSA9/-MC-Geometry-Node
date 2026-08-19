package com.mine.geometry_node.client.model.render.backend.host.light.integration;

import com.mine.geometry_node.client.model.render.backend.host.iris.labpbr.ModelProjectorCapability;

import java.util.EnumMap;
import java.util.Map;

/** Pure F1 classifier and owner arbitrator. It has no world, renderer or shaderpack-name dependency. */
final class HostLightingArbitrator {
    private HostLightingArbitrator() {
    }

    static Classified classify(HostLightingEnvironmentSnapshot environment,
                               HostPackLightingDescriptor descriptor) {
        EnumMap<HostLightingCapability, HostLightingCapabilityEvidence> capabilities =
                new EnumMap<>(HostLightingCapability.class);
        put(capabilities, HostLightingCapability.ENTITY_VERTEX_INPUT, HostLightingCapabilityState.AVAILABLE,
                HostLightingEvidenceSource.STANDARD_ENTITY_CONTRACT, "STANDARD_ENTITY_VERTEX_FORMAT");
        classifyLabPbr(environment, capabilities);
        classifyShadowReplay(environment, capabilities);
        EnumMap<HostLightingDomain, HostPackDomainEvidence> packDomains =
                classifyPackDomains(environment, descriptor, capabilities);

        put(capabilities, HostLightingCapability.HOST_PLACED_BLOCK_UV2,
                environment.hostNativeRequired() ? HostLightingCapabilityState.AVAILABLE
                        : HostLightingCapabilityState.UNAVAILABLE,
                environment.hostNativeRequired() ? HostLightingEvidenceSource.HOST_IMPLEMENTATION
                        : HostLightingEvidenceSource.NONE,
                environment.hostNativeRequired() ? "F4_RENDER_BINDING_AVAILABLE" : "HOST_RENDERER_INACTIVE");
        put(capabilities, HostLightingCapability.HOST_HELD_DYNAMIC_UV2,
                HostLightingCapabilityState.UNAVAILABLE, HostLightingEvidenceSource.NONE,
                "F7_NOT_IMPLEMENTED");
        put(capabilities, HostLightingCapability.HOST_MODEL_EMISSIVE_UV2,
                HostLightingCapabilityState.UNAVAILABLE, HostLightingEvidenceSource.NONE,
                "F7_NOT_IMPLEMENTED");

        EnumMap<HostLightingDomain, HostLightingOwnerDecision> decisions =
                new EnumMap<>(HostLightingDomain.class);
        decisions.put(HostLightingDomain.SUN_SKY, decideSun(capabilities));
        decisions.put(HostLightingDomain.PLACED_BLOCK,
                decideLocal(HostLightingDomain.PLACED_BLOCK, capabilities));
        decisions.put(HostLightingDomain.HELD_DYNAMIC,
                decideLocal(HostLightingDomain.HELD_DYNAMIC, capabilities));
        decisions.put(HostLightingDomain.MODEL_EMISSIVE,
                decideLocal(HostLightingDomain.MODEL_EMISSIVE, capabilities));
        return new Classified(Map.copyOf(capabilities), Map.copyOf(packDomains), Map.copyOf(decisions));
    }

    private static void classifyLabPbr(
            HostLightingEnvironmentSnapshot environment,
            EnumMap<HostLightingCapability, HostLightingCapabilityEvidence> capabilities) {
        ModelProjectorCapability projector = environment.projector().capability();
        HostLightingCapabilityState state = switch (projector) {
            case ACTIVE, PENDING, UNVERIFIED -> HostLightingCapabilityState.UNVERIFIED;
            case ABSENT, INACTIVE, FAILED -> HostLightingCapabilityState.UNAVAILABLE;
        };
        put(capabilities, HostLightingCapability.LABPBR_ATTACHMENTS, state,
                state == HostLightingCapabilityState.UNVERIFIED
                        ? HostLightingEvidenceSource.STRUCTURAL_OBSERVATION
                        : HostLightingEvidenceSource.NONE,
                "PROJECTOR_" + projector);
    }

    private static void classifyShadowReplay(
            HostLightingEnvironmentSnapshot environment,
            EnumMap<HostLightingCapability, HostLightingCapabilityEvidence> capabilities) {
        HostLightingEnvironmentSnapshot.ShadowEvidence shadow = environment.shadow();
        boolean structurallyObserved = environment.hostNativeRequired() && shadow.installed()
                && shadow.capabilities() != null && shadow.failure().isEmpty();
        boolean verified = structurallyObserved && shadow.replayVerified();
        put(capabilities, HostLightingCapability.SUN_SHADOW_REPLAY,
                verified ? HostLightingCapabilityState.AVAILABLE
                        : structurallyObserved ? HostLightingCapabilityState.UNVERIFIED
                        : HostLightingCapabilityState.UNAVAILABLE,
                verified ? HostLightingEvidenceSource.PUBLIC_RUNTIME_API
                        : structurallyObserved ? HostLightingEvidenceSource.STRUCTURAL_OBSERVATION
                        : HostLightingEvidenceSource.NONE,
                verified ? "PUBLIC_CALLBACK_DRAW_VERIFIED"
                        : structurallyObserved ? "STRUCTURAL_CALLBACK_AND_TARGET_ONLY"
                        : "NO_STABLE_SHADOW_EVIDENCE");
    }

    private static EnumMap<HostLightingDomain, HostPackDomainEvidence> classifyPackDomains(
            HostLightingEnvironmentSnapshot environment,
            HostPackLightingDescriptor descriptor,
            EnumMap<HostLightingCapability, HostLightingCapabilityEvidence> capabilities) {
        EnumMap<HostLightingDomain, HostPackDomainEvidence> domains =
                new EnumMap<>(HostLightingDomain.class);
        for (HostLightingDomain domain : HostLightingDomain.values()) {
            HostLightingCapability capability = HostLightingCapability.packCapability(domain);
            HostPackDomainEvidence evidence;
            if (!environment.hostNativeRequired()) {
                evidence = uniformDomain(domain, HostLightingCapabilityState.UNAVAILABLE,
                        HostLightingEvidenceSource.NONE, "NO_ACTIVE_SHADERPACK");
            } else if (descriptor == null) {
                evidence = uniformDomain(domain, HostLightingCapabilityState.UNAVAILABLE,
                        HostLightingEvidenceSource.NONE, "NO_PACK_NATIVE_ADAPTER_CONTRACT");
            } else if (!descriptor.matches(environment)) {
                evidence = uniformDomain(domain, HostLightingCapabilityState.UNVERIFIED,
                        HostLightingEvidenceSource.NONE, "STALE_DESCRIPTOR");
            } else {
                evidence = descriptor.domain(domain);
            }
            domains.put(domain, evidence);
            HostLightingEvidenceSource source = descriptor != null && descriptor.matches(environment)
                    ? descriptor.source() : evidence.roles().values().iterator().next().source();
            put(capabilities, capability, evidence.aggregateState(), source,
                    "DOMAIN_ROLES:" + evidence.aggregateState());
        }
        return domains;
    }

    private static HostPackDomainEvidence uniformDomain(HostLightingDomain domain,
                                                        HostLightingCapabilityState state,
                                                        HostLightingEvidenceSource source,
                                                        String detail) {
        EnumMap<HostPackLightingRole, HostPackLightingRoleEvidence> roles =
                new EnumMap<>(HostPackLightingRole.class);
        for (HostPackLightingRole role : HostPackLightingRole.required(domain)) {
            roles.put(role, new HostPackLightingRoleEvidence(role, state, source, detail));
        }
        return new HostPackDomainEvidence(domain, roles);
    }

    private static HostLightingOwnerDecision decideSun(
            Map<HostLightingCapability, HostLightingCapabilityEvidence> capabilities) {
        HostLightingCapabilityEvidence pack = capabilities.get(HostLightingCapability.PACK_SUN_SKY);
        return switch (pack.state()) {
            case AVAILABLE -> decision(HostLightingDomain.SUN_SKY,
                    HostLightingOwner.PACK_NATIVE, HostLightingOwner.PACK_NATIVE, "VERIFIED_PACK_SUN_SKY");
            case CONFLICT -> decision(HostLightingDomain.SUN_SKY,
                    HostLightingOwner.EXTERNAL_CONFLICT, HostLightingOwner.EXTERNAL_CONFLICT,
                    "VERIFIED_EXTERNAL_SUN_SKY_CONFLICT");
            case UNAVAILABLE, UNVERIFIED -> decision(HostLightingDomain.SUN_SKY,
                    HostLightingOwner.ENTITY_NATIVE, HostLightingOwner.ENTITY_NATIVE,
                    "STANDARD_ENTITY_SUN_SKY");
        };
    }

    private static HostLightingOwnerDecision decideLocal(
            HostLightingDomain domain,
            Map<HostLightingCapability, HostLightingCapabilityEvidence> capabilities) {
        HostLightingCapabilityEvidence pack = capabilities.get(HostLightingCapability.packCapability(domain));
        if (pack.state() == HostLightingCapabilityState.AVAILABLE) {
            return decision(domain, HostLightingOwner.PACK_NATIVE, HostLightingOwner.PACK_NATIVE,
                    "VERIFIED_PACK_" + domain);
        }
        if (pack.state() == HostLightingCapabilityState.CONFLICT) {
            return decision(domain, HostLightingOwner.EXTERNAL_CONFLICT, HostLightingOwner.EXTERNAL_CONFLICT,
                    "VERIFIED_EXTERNAL_CONFLICT_" + domain);
        }
        if (pack.state() == HostLightingCapabilityState.UNVERIFIED) {
            return decision(domain, HostLightingOwner.CONSTANT, HostLightingOwner.CONSTANT,
                    "PACK_DOMAIN_UNVERIFIED_FAIL_CLOSED");
        }

        HostLightingCapabilityEvidence host = capabilities.get(HostLightingCapability.hostCapability(domain));
        if (host.available()) {
            return decision(domain, HostLightingOwner.HOST_UV2, HostLightingOwner.HOST_UV2,
                    "HOST_UV2_AVAILABLE");
        }
        HostLightingOwner preferred = domain == HostLightingDomain.PLACED_BLOCK
                ? HostLightingOwner.HOST_UV2 : HostLightingOwner.CONSTANT;
        return decision(domain, preferred, HostLightingOwner.CONSTANT,
                "HOST_UV2_NOT_IMPLEMENTED");
    }

    private static HostLightingOwnerDecision decision(HostLightingDomain domain,
                                                        HostLightingOwner preferred,
                                                        HostLightingOwner effective,
                                                        String reason) {
        return new HostLightingOwnerDecision(domain, preferred, effective, reason);
    }

    private static void put(
            EnumMap<HostLightingCapability, HostLightingCapabilityEvidence> capabilities,
            HostLightingCapability capability,
            HostLightingCapabilityState state,
            HostLightingEvidenceSource source,
            String detail) {
        capabilities.put(capability, new HostLightingCapabilityEvidence(capability, state, source, detail));
    }

    record Classified(Map<HostLightingCapability, HostLightingCapabilityEvidence> capabilities,
                      Map<HostLightingDomain, HostPackDomainEvidence> packDomains,
                      Map<HostLightingDomain, HostLightingOwnerDecision> decisions) {
    }
}
