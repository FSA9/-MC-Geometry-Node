package com.mine.geometry_node.client.model.render.backend.host.light.integration;

import com.mine.geometry_node.client.model.render.backend.host.iris.entity.IrisEntityTranslucency;
import com.mine.geometry_node.client.model.render.backend.host.iris.labpbr.IrisLabPbrProjector;
import com.mine.geometry_node.client.model.render.backend.host.iris.labpbr.ModelProjectorCapability;
import com.mine.geometry_node.client.model.render.backend.host.iris.shadow.IrisShadowCapabilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HostLightingPolicyTest {
    @BeforeEach
    void clearDescriptorBeforeTest() {
        HostLightingPolicy.clearPackDescriptor();
    }

    @AfterEach
    void clearDescriptorAfterTest() {
        HostLightingPolicy.clearPackDescriptor();
    }

    @Test
    void noShaderpackUsesEntitySunAndDoesNotClaimUnimplementedHostUv2() {
        HostLightingPolicySnapshot snapshot = HostLightingPolicy.capture(environment(1, 10, 20, false, 0));

        assertEquals(HostLightingDomain.values().length, snapshot.decisions().size());
        assertEquals(HostLightingOwner.ENTITY_NATIVE,
                snapshot.decision(HostLightingDomain.SUN_SKY).effectiveOwner());
        HostLightingOwnerDecision placed = snapshot.decision(HostLightingDomain.PLACED_BLOCK);
        assertEquals(HostLightingOwner.HOST_UV2, placed.preferredOwner());
        assertEquals(HostLightingOwner.CONSTANT, placed.effectiveOwner());
        assertEquals(HostLightingCapabilityState.UNAVAILABLE,
                snapshot.capability(HostLightingCapability.HOST_PLACED_BLOCK_UV2).state());
    }

    @Test
    void structuralIrisEvidenceNeverSelectsPackNative() {
        HostLightingPolicySnapshot snapshot = HostLightingPolicy.capture(environment(2, 11, 21, true, 8));

        assertEquals(HostLightingCapabilityState.UNVERIFIED,
                snapshot.capability(HostLightingCapability.SUN_SHADOW_REPLAY).state());
        for (HostLightingDomain domain : HostLightingDomain.values()) {
            assertEquals(HostLightingCapabilityState.UNVERIFIED,
                    snapshot.capability(HostLightingCapability.packCapability(domain)).state());
            assertNotEquals(HostLightingOwner.PACK_NATIVE, snapshot.decision(domain).effectiveOwner());
        }
    }

    @Test
    void matchingVerifiedDescriptorCanSelectPackForOnlyItsExplicitDomain() {
        HostLightingEnvironmentSnapshot environment = environment(3, 12, 22, true, 0);
        HostLightingEnvironment.acceptObservation(environment.resourceReloadGeneration(),
                environment.integrationGeneration(), true, environment.projector(), environment.translucency(),
                environment.shadow());
        HostLightingPolicySnapshot snapshot = HostLightingPolicy.installPackDescriptor(descriptor(
                "fixture-adapter", 1, 12, 22, HostLightingEvidenceSource.VERIFIED_ADAPTER_DESCRIPTOR,
                HostLightingDomain.SUN_SKY, HostLightingCapabilityState.AVAILABLE));

        assertEquals(HostLightingOwner.PACK_NATIVE,
                snapshot.decision(HostLightingDomain.SUN_SKY).effectiveOwner());
        assertEquals(HostLightingOwner.CONSTANT,
                snapshot.decision(HostLightingDomain.PLACED_BLOCK).effectiveOwner());
        assertEquals(HostLightingCapabilityState.UNAVAILABLE,
                snapshot.capability(HostLightingCapability.PACK_PLACED_BLOCK).state());
    }

    @Test
    void verifiedConflictWinsOverFallback() {
        HostLightingEnvironmentSnapshot environment = environment(4, 13, 23, true, 0);
        HostLightingEnvironment.acceptObservation(environment.resourceReloadGeneration(),
                environment.integrationGeneration(), true, environment.projector(), environment.translucency(),
                environment.shadow());
        HostLightingPolicySnapshot snapshot = HostLightingPolicy.installPackDescriptor(descriptor(
                "manual-fixture", 2, 13, 23, HostLightingEvidenceSource.MANUAL_ACCEPTANCE,
                HostLightingDomain.HELD_DYNAMIC, HostLightingCapabilityState.CONFLICT));

        HostLightingOwnerDecision held = snapshot.decision(HostLightingDomain.HELD_DYNAMIC);
        assertEquals(HostLightingOwner.EXTERNAL_CONFLICT, held.preferredOwner());
        assertEquals(HostLightingOwner.EXTERNAL_CONFLICT, held.effectiveOwner());
    }

    @Test
    void receiverOnlyConflictsButCasterOnlyCanRemainAStandardEntitySupplement() {
        HostLightingEnvironmentSnapshot environment = environment(4, 13, 23, true, 0);
        HostLightingEnvironment.acceptObservation(environment.resourceReloadGeneration(),
                environment.integrationGeneration(), true, environment.projector(), environment.translucency(),
                environment.shadow());

        HostLightingPolicySnapshot receiverOnly = HostLightingPolicy.installPackDescriptor(descriptorWithRoles(
                "partial-receiver", 10, 13, 23, HostLightingEvidenceSource.VERIFIED_ADAPTER_DESCRIPTOR,
                HostLightingDomain.PLACED_BLOCK, Map.of(
                        HostPackLightingRole.RECEIVER, HostLightingCapabilityState.AVAILABLE,
                        HostPackLightingRole.OCCLUDER_CASTER, HostLightingCapabilityState.UNAVAILABLE)));
        assertEquals(HostLightingCapabilityState.CONFLICT,
                receiverOnly.capability(HostLightingCapability.PACK_PLACED_BLOCK).state());
        assertEquals(HostLightingOwner.EXTERNAL_CONFLICT,
                receiverOnly.decision(HostLightingDomain.PLACED_BLOCK).effectiveOwner());

        HostLightingPolicySnapshot casterOnly = HostLightingPolicy.installPackDescriptor(descriptorWithRoles(
                "partial-receiver", 10, 13, 23, HostLightingEvidenceSource.VERIFIED_ADAPTER_DESCRIPTOR,
                HostLightingDomain.PLACED_BLOCK, Map.of(
                        HostPackLightingRole.RECEIVER, HostLightingCapabilityState.UNAVAILABLE,
                        HostPackLightingRole.OCCLUDER_CASTER, HostLightingCapabilityState.AVAILABLE)));
        assertTrue(casterOnly.generation() > receiverOnly.generation());
        assertEquals(HostLightingCapabilityState.UNAVAILABLE,
                casterOnly.capability(HostLightingCapability.PACK_PLACED_BLOCK).state());
        assertEquals(HostLightingOwner.CONSTANT,
                casterOnly.decision(HostLightingDomain.PLACED_BLOCK).effectiveOwner());
        assertEquals(HostLightingCapabilityState.AVAILABLE,
                casterOnly.packDomain(HostLightingDomain.PLACED_BLOCK)
                        .role(HostPackLightingRole.OCCLUDER_CASTER).state());
    }

    @Test
    void modelEmissiveRequiresReceiverCasterAndEmitterRoles() {
        HostLightingEnvironmentSnapshot environment = environment(4, 13, 23, true, 0);
        HostLightingEnvironment.acceptObservation(environment.resourceReloadGeneration(),
                environment.integrationGeneration(), true, environment.projector(), environment.translucency(),
                environment.shadow());
        HostLightingPolicySnapshot missingEmitter = HostLightingPolicy.installPackDescriptor(descriptorWithRoles(
                "partial-emissive", 12, 13, 23, HostLightingEvidenceSource.MANUAL_ACCEPTANCE,
                HostLightingDomain.MODEL_EMISSIVE, Map.of(
                        HostPackLightingRole.RECEIVER, HostLightingCapabilityState.AVAILABLE,
                        HostPackLightingRole.OCCLUDER_CASTER, HostLightingCapabilityState.UNAVAILABLE,
                        HostPackLightingRole.SOURCE_EMITTER, HostLightingCapabilityState.AVAILABLE)));

        assertEquals(HostLightingCapabilityState.CONFLICT,
                missingEmitter.capability(HostLightingCapability.PACK_MODEL_EMISSIVE).state());
        assertEquals(HostLightingOwner.EXTERNAL_CONFLICT,
                missingEmitter.decision(HostLightingDomain.MODEL_EMISSIVE).effectiveOwner());
    }

    @Test
    void staleDescriptorAndHostRequirementChangesFailClosed() {
        HostLightingEnvironmentSnapshot active = environment(5, 14, 24, true, 0);
        HostLightingEnvironment.acceptObservation(active.resourceReloadGeneration(),
                active.integrationGeneration(), true, active.projector(), active.translucency(), active.shadow());
        HostLightingPolicySnapshot verified = HostLightingPolicy.installPackDescriptor(descriptor(
                "fixture-adapter", 3, 14, 24, HostLightingEvidenceSource.PUBLIC_RUNTIME_API,
                HostLightingDomain.PLACED_BLOCK, HostLightingCapabilityState.AVAILABLE));
        assertEquals(HostLightingOwner.PACK_NATIVE,
                verified.decision(HostLightingDomain.PLACED_BLOCK).effectiveOwner());

        HostLightingPolicySnapshot reloaded = HostLightingPolicy.capture(environment(6, 15, 24, true, 0));
        assertTrue(reloaded.generation() > verified.generation());
        assertEquals(HostLightingCapabilityState.UNVERIFIED,
                reloaded.capability(HostLightingCapability.PACK_PLACED_BLOCK).state());
        assertEquals(HostLightingOwner.CONSTANT,
                reloaded.decision(HostLightingDomain.PLACED_BLOCK).effectiveOwner());

        HostLightingPolicySnapshot inactive = HostLightingPolicy.capture(environment(7, 15, 25, false, 0));
        assertTrue(inactive.generation() > reloaded.generation());
        assertEquals(HostLightingCapabilityState.UNAVAILABLE,
                inactive.capability(HostLightingCapability.PACK_PLACED_BLOCK).state());
    }

    @Test
    void telemetryDoesNotAdvancePolicyGeneration() {
        HostLightingPolicySnapshot before = HostLightingPolicy.capture(environment(8, 16, 26, true, 1));
        HostLightingPolicySnapshot after = HostLightingPolicy.capture(environment(8, 16, 26, true, 999));

        assertEquals(before.generation(), after.generation());
        assertSame(before, after);
    }

    @Test
    void structuralEvidenceCannotBeConstructedAsAvailablePackProof() {
        assertThrows(IllegalArgumentException.class, () -> new HostLightingCapabilityEvidence(
                HostLightingCapability.PACK_SUN_SKY, HostLightingCapabilityState.AVAILABLE,
                HostLightingEvidenceSource.STRUCTURAL_OBSERVATION, "not proof"));
        assertThrows(IllegalArgumentException.class, () -> new HostPackLightingRoleEvidence(
                HostPackLightingRole.RECEIVER, HostLightingCapabilityState.AVAILABLE,
                HostLightingEvidenceSource.STRUCTURAL_OBSERVATION, "not proof"));
    }

    @Test
    void availableEntityAndHostCapabilitiesRequireCorrectProvenance() {
        assertThrows(IllegalArgumentException.class, () -> new HostLightingCapabilityEvidence(
                HostLightingCapability.HOST_PLACED_BLOCK_UV2, HostLightingCapabilityState.AVAILABLE,
                HostLightingEvidenceSource.NONE, "forged"));
        assertThrows(IllegalArgumentException.class, () -> new HostLightingCapabilityEvidence(
                HostLightingCapability.ENTITY_VERTEX_INPUT, HostLightingCapabilityState.AVAILABLE,
                HostLightingEvidenceSource.NONE, "forged"));
    }

    @Test
    void domainEvidenceRequiresEveryRoleAndSnapshotRejectsMismatchedAggregate() {
        assertThrows(IllegalArgumentException.class, () -> new HostPackDomainEvidence(
                HostLightingDomain.SUN_SKY, Map.of(HostPackLightingRole.RECEIVER,
                new HostPackLightingRoleEvidence(HostPackLightingRole.RECEIVER,
                        HostLightingCapabilityState.UNVERIFIED, HostLightingEvidenceSource.NONE, "partial"))));

        HostLightingPolicySnapshot valid = HostLightingPolicy.capture(environment(9, 17, 27, true, 0));
        EnumMap<HostLightingCapability, HostLightingCapabilityEvidence> capabilities =
                new EnumMap<>(HostLightingCapability.class);
        capabilities.putAll(valid.capabilities());
        capabilities.put(HostLightingCapability.PACK_SUN_SKY, new HostLightingCapabilityEvidence(
                HostLightingCapability.PACK_SUN_SKY, HostLightingCapabilityState.UNAVAILABLE,
                HostLightingEvidenceSource.NONE, "forged aggregate"));
        assertThrows(IllegalArgumentException.class, () -> new HostLightingPolicySnapshot(
                valid.generation(), valid.environmentGeneration(), valid.resourceReloadGeneration(),
                valid.integrationGeneration(), valid.descriptorId(), valid.descriptorRevision(),
                capabilities, valid.packDomains(), valid.decisions()));
    }

    @Test
    void publicSnapshotRejectsEntityOwnerWithoutEntityInputCapability() {
        HostLightingPolicySnapshot valid = HostLightingPolicy.capture(environment(9, 17, 27, false, 0));
        EnumMap<HostLightingCapability, HostLightingCapabilityEvidence> capabilities =
                new EnumMap<>(HostLightingCapability.class);
        capabilities.putAll(valid.capabilities());
        capabilities.put(HostLightingCapability.ENTITY_VERTEX_INPUT, new HostLightingCapabilityEvidence(
                HostLightingCapability.ENTITY_VERTEX_INPUT, HostLightingCapabilityState.UNAVAILABLE,
                HostLightingEvidenceSource.NONE, "forged"));

        assertThrows(IllegalArgumentException.class, () -> new HostLightingPolicySnapshot(
                valid.generation(), valid.environmentGeneration(), valid.resourceReloadGeneration(),
                valid.integrationGeneration(), valid.descriptorId(), valid.descriptorRevision(),
                capabilities, valid.packDomains(), valid.decisions()));
    }

    @Test
    void publicSnapshotRejectsOwnerWithoutMatchingCapability() {
        HostLightingPolicySnapshot valid = HostLightingPolicy.capture(environment(9, 17, 27, true, 0));

        assertThrows(IllegalArgumentException.class, () -> withDecision(valid, HostLightingDomain.PLACED_BLOCK,
                new HostLightingOwnerDecision(HostLightingDomain.PLACED_BLOCK,
                        HostLightingOwner.PACK_NATIVE, HostLightingOwner.PACK_NATIVE, "invalid")));
        assertThrows(IllegalArgumentException.class, () -> withDecision(valid, HostLightingDomain.PLACED_BLOCK,
                new HostLightingOwnerDecision(HostLightingDomain.PLACED_BLOCK,
                        HostLightingOwner.HOST_UV2, HostLightingOwner.HOST_UV2, "invalid")));
        assertThrows(IllegalArgumentException.class, () -> withDecision(valid, HostLightingDomain.HELD_DYNAMIC,
                new HostLightingOwnerDecision(HostLightingDomain.HELD_DYNAMIC,
                        HostLightingOwner.EXTERNAL_CONFLICT, HostLightingOwner.EXTERNAL_CONFLICT, "invalid")));
        assertThrows(IllegalArgumentException.class, () -> withDecision(valid, HostLightingDomain.SUN_SKY,
                new HostLightingOwnerDecision(HostLightingDomain.SUN_SKY,
                        HostLightingOwner.HOST_UV2, HostLightingOwner.HOST_UV2, "invalid")));
    }

    @Test
    void publicSnapshotRequiresDescriptorIdAndRevisionTogether() {
        HostLightingPolicySnapshot valid = HostLightingPolicy.capture(environment(10, 18, 28, false, 0));
        assertThrows(IllegalArgumentException.class, () -> new HostLightingPolicySnapshot(
                valid.generation(), valid.environmentGeneration(), valid.resourceReloadGeneration(),
                valid.integrationGeneration(), "", 0, valid.capabilities(), valid.packDomains(), valid.decisions()));
        assertThrows(IllegalArgumentException.class, () -> new HostLightingPolicySnapshot(
                valid.generation(), valid.environmentGeneration(), valid.resourceReloadGeneration(),
                valid.integrationGeneration(), "descriptor", -1, valid.capabilities(), valid.packDomains(),
                valid.decisions()));
    }

    private static HostLightingEnvironmentSnapshot environment(long generation,
                                                               long reloadGeneration,
                                                               long integrationGeneration,
                                                               boolean hostRequired,
                                                               int submittedDraws) {
        return new HostLightingEnvironmentSnapshot(generation, reloadGeneration, integrationGeneration,
                hostRequired,
                new IrisLabPbrProjector.Snapshot(reloadGeneration,
                        hostRequired ? ModelProjectorCapability.UNVERIFIED : ModelProjectorCapability.INACTIVE,
                        "fixture"),
                new IrisEntityTranslucency.Snapshot(false, "fixture"),
                new HostLightingEnvironmentSnapshot.ShadowEvidence(hostRequired,
                        hostRequired ? new IrisShadowCapabilities(1, 1, List.of("RGBA8"), true) : null,
                        "", submittedDraws, submittedDraws > 0));
    }

    private static HostPackLightingDescriptor descriptor(String id,
                                                         long revision,
                                                         long reloadGeneration,
                                                         long integrationGeneration,
                                                         HostLightingEvidenceSource source,
                                                         HostLightingDomain domain,
                                                         HostLightingCapabilityState state) {
        EnumMap<HostPackLightingRole, HostLightingCapabilityState> roles =
                new EnumMap<>(HostPackLightingRole.class);
        for (HostPackLightingRole role : HostPackLightingRole.required(domain)) roles.put(role, state);
        return descriptorWithRoles(id, revision, reloadGeneration, integrationGeneration, source, domain, roles);
    }

    private static HostPackLightingDescriptor descriptorWithRoles(
            String id,
            long revision,
            long reloadGeneration,
            long integrationGeneration,
            HostLightingEvidenceSource source,
            HostLightingDomain selectedDomain,
            Map<HostPackLightingRole, HostLightingCapabilityState> selectedRoles) {
        EnumMap<HostLightingDomain, HostPackDomainEvidence> domains =
                new EnumMap<>(HostLightingDomain.class);
        for (HostLightingDomain domain : HostLightingDomain.values()) {
            EnumMap<HostPackLightingRole, HostPackLightingRoleEvidence> roles =
                    new EnumMap<>(HostPackLightingRole.class);
            for (HostPackLightingRole role : HostPackLightingRole.required(domain)) {
                HostLightingCapabilityState state = domain == selectedDomain
                        ? selectedRoles.get(role) : HostLightingCapabilityState.UNAVAILABLE;
                if (state == null) throw new IllegalArgumentException("missing selected role " + role);
                roles.put(role, new HostPackLightingRoleEvidence(role, state, source, "fixture"));
            }
            domains.put(domain, new HostPackDomainEvidence(domain, roles));
        }
        return new HostPackLightingDescriptor(id, revision, reloadGeneration, integrationGeneration,
                source, domains);
    }

    private static HostLightingPolicySnapshot withDecision(HostLightingPolicySnapshot source,
                                                           HostLightingDomain domain,
                                                           HostLightingOwnerDecision decision) {
        EnumMap<HostLightingDomain, HostLightingOwnerDecision> decisions =
                new EnumMap<>(HostLightingDomain.class);
        decisions.putAll(source.decisions());
        decisions.put(domain, decision);
        return new HostLightingPolicySnapshot(source.generation(), source.environmentGeneration(),
                source.resourceReloadGeneration(), source.integrationGeneration(), source.descriptorId(),
                source.descriptorRevision(), source.capabilities(), source.packDomains(), Map.copyOf(decisions));
    }
}
