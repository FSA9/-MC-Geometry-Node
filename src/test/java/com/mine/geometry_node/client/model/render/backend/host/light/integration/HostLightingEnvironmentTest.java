package com.mine.geometry_node.client.model.render.backend.host.light.integration;

import com.mine.geometry_node.client.model.render.backend.host.iris.entity.IrisEntityTranslucency;
import com.mine.geometry_node.client.model.render.backend.host.iris.labpbr.IrisLabPbrProjector;
import com.mine.geometry_node.client.model.render.backend.host.iris.labpbr.ModelProjectorCapability;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HostLightingEnvironmentTest {
    @Test
    void identicalEvidenceKeepsOneFrameStableGeneration() {
        IrisLabPbrProjector.Snapshot projector = new IrisLabPbrProjector.Snapshot(
                17, ModelProjectorCapability.UNVERIFIED, "test");
        IrisEntityTranslucency.Snapshot translucency = new IrisEntityTranslucency.Snapshot(false, "test");
        HostLightingEnvironmentSnapshot.ShadowEvidence shadow =
                new HostLightingEnvironmentSnapshot.ShadowEvidence(true, null, "", 3, false);

        HostLightingEnvironmentSnapshot first = HostLightingEnvironment.acceptObservation(
                17, 4, true, projector, translucency, shadow);
        HostLightingEnvironmentSnapshot second = HostLightingEnvironment.acceptObservation(
                17, 4, true, projector, translucency, shadow);

        assertSame(first, second);
        assertEquals(ModelProjectorCapability.UNVERIFIED, second.projector().capability());
    }

    @Test
    void changedEvidenceAdvancesEnvironmentGenerationOnce() {
        IrisEntityTranslucency.Snapshot translucency = new IrisEntityTranslucency.Snapshot(false, "test");
        HostLightingEnvironmentSnapshot.ShadowEvidence shadow =
                new HostLightingEnvironmentSnapshot.ShadowEvidence(false, null, "IRIS_ABSENT", 0, false);
        HostLightingEnvironmentSnapshot before = HostLightingEnvironment.acceptObservation(
                30, 8, false, new IrisLabPbrProjector.Snapshot(30, ModelProjectorCapability.ABSENT, "test"),
                translucency, shadow);
        HostLightingEnvironmentSnapshot after = HostLightingEnvironment.acceptObservation(
                31, 8, false, new IrisLabPbrProjector.Snapshot(31, ModelProjectorCapability.ABSENT, "test"),
                translucency, shadow);

        assertTrue(after.generation() > before.generation());
        assertEquals(31, after.resourceReloadGeneration());
    }

    @Test
    void telemetryChangesDoNotAdvanceCapabilityGeneration() {
        IrisLabPbrProjector.Snapshot projector = new IrisLabPbrProjector.Snapshot(
                40, ModelProjectorCapability.UNVERIFIED, "test");
        IrisEntityTranslucency.Snapshot translucency = new IrisEntityTranslucency.Snapshot(false, "test");
        HostLightingEnvironmentSnapshot before = HostLightingEnvironment.acceptObservation(
                40, 12, true, projector, translucency,
                new HostLightingEnvironmentSnapshot.ShadowEvidence(true, null, "", 1, false));
        HostLightingEnvironmentSnapshot after = HostLightingEnvironment.acceptObservation(
                40, 12, true, projector, translucency,
                new HostLightingEnvironmentSnapshot.ShadowEvidence(true, null, "", 19, true));

        assertEquals(before.generation(), after.generation());
        assertEquals(19, after.shadow().submittedDraws());
        assertTrue(after.shadow().translucentPhaseObserved());
    }

    @Test
    void diagnosticTextChangesDoNotAdvanceCapabilityGeneration() {
        HostLightingEnvironmentSnapshot before = HostLightingEnvironment.acceptObservation(
                45, 13, true,
                new IrisLabPbrProjector.Snapshot(45, ModelProjectorCapability.UNVERIFIED, "diagnostic-a"),
                new IrisEntityTranslucency.Snapshot(false, "diagnostic-a"),
                new HostLightingEnvironmentSnapshot.ShadowEvidence(true, null, "failure-a", 0, false));
        HostLightingEnvironmentSnapshot after = HostLightingEnvironment.acceptObservation(
                45, 13, true,
                new IrisLabPbrProjector.Snapshot(45, ModelProjectorCapability.UNVERIFIED, "diagnostic-b"),
                new IrisEntityTranslucency.Snapshot(false, "diagnostic-b"),
                new HostLightingEnvironmentSnapshot.ShadowEvidence(true, null, "failure-b", 0, false));

        assertEquals(before.generation(), after.generation());
        assertEquals("diagnostic-b", after.projector().diagnostic());
        assertEquals("failure-b", after.shadow().failure());
    }

    @Test
    void hostRequirementChangeAdvancesCapabilityGeneration() {
        IrisLabPbrProjector.Snapshot projector = new IrisLabPbrProjector.Snapshot(
                50, ModelProjectorCapability.INACTIVE, "test");
        IrisEntityTranslucency.Snapshot translucency = new IrisEntityTranslucency.Snapshot(false, "test");
        HostLightingEnvironmentSnapshot.ShadowEvidence shadow =
                new HostLightingEnvironmentSnapshot.ShadowEvidence(true, null, "", 0, false);
        HostLightingEnvironmentSnapshot inactive = HostLightingEnvironment.acceptObservation(
                50, 20, false, projector, translucency, shadow);
        HostLightingEnvironmentSnapshot required = HostLightingEnvironment.acceptObservation(
                50, 20, true, projector, translucency, shadow);

        assertTrue(required.generation() > inactive.generation());
        assertTrue(required.hostNativeRequired());
    }

    @Test
    void inactiveHostDropsAllPrivateShadowEvidence() {
        HostLightingEnvironmentSnapshot.ShadowEvidence inactive =
                HostLightingEnvironment.shadowEvidence(false);

        assertFalse(inactive.installed());
        assertNull(inactive.capabilities());
        assertEquals("", inactive.failure());
        assertEquals(0, inactive.submittedDraws());
        assertFalse(inactive.translucentPhaseObserved());
    }
}
