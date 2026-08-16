package com.mine.geometry_node.client.model.render.integration;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ModelIntegrationStatusTest {
    @Test void fullStatusCannotHideLosses() {
        assertThrows(IllegalArgumentException.class, () -> new ModelIntegrationStatus(
                ModelIntegrationMode.NATIVE, ModelIntegrationMode.NATIVE, ModelNativeProfile.HOST_NATIVE,
                ModelIntegrationStatus.Fidelity.FULL, "host-native-entity", true, Set.of(),
                Set.of(ModelCompatibilityLoss.METALLIC_UNREPRESENTABLE), ModelIntegrationVerification.VERIFIED,
                List.of(), Map.of(), 0, ModelIntegrationFallback.NONE, ""));
    }

    @Test void effectiveModeChangeRequiresFallbackReason() {
        assertThrows(IllegalArgumentException.class, () -> new ModelIntegrationStatus(
                ModelIntegrationMode.TAKEOVER, ModelIntegrationMode.NATIVE, ModelNativeProfile.HOST_NATIVE,
                ModelIntegrationStatus.Fidelity.DEGRADED, "host-native-entity", true, Set.of(),
                Set.of(), ModelIntegrationVerification.UNVERIFIED, List.of(), Map.of(), 0,
                ModelIntegrationFallback.NONE, ""));
    }

    @Test void fallbackCannotBeReportedWithoutModeChange() {
        assertThrows(IllegalArgumentException.class, () -> new ModelIntegrationStatus(
                ModelIntegrationMode.NATIVE, ModelIntegrationMode.NATIVE, ModelNativeProfile.HOST_NATIVE,
                ModelIntegrationStatus.Fidelity.DEGRADED, "host-native-entity", true, Set.of(),
                Set.of(), ModelIntegrationVerification.UNVERIFIED, List.of(), Map.of(), 0,
                ModelIntegrationFallback.RUNTIME_FAILURE, "failure"));
    }

    @Test void degradedStatusMayAwaitPositiveEvidenceWithoutInventingALoss() {
        assertDoesNotThrow(() -> new ModelIntegrationStatus(
                ModelIntegrationMode.NATIVE, ModelIntegrationMode.NATIVE, ModelNativeProfile.HOST_NATIVE,
                ModelIntegrationStatus.Fidelity.DEGRADED, "host-native-entity", true, Set.of(),
                Set.of(), ModelIntegrationVerification.PENDING, List.of(), Map.of(), 1,
                ModelIntegrationFallback.NONE, ""));
    }

    @Test void rejectedDrawCountsMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> new ModelIntegrationStatus(
                ModelIntegrationMode.NATIVE, ModelIntegrationMode.NATIVE, ModelNativeProfile.HOST_NATIVE,
                ModelIntegrationStatus.Fidelity.DEGRADED, "host-native-entity", true, Set.of(),
                Set.of(), ModelIntegrationVerification.UNVERIFIED, List.of(),
                Map.of(ModelDrawRejection.UNSUPPORTED_SKINNING, 0), 1,
                ModelIntegrationFallback.NONE, ""));
    }

    @Test void fullStandaloneMayUseNotRequiredVerification() {
        assertDoesNotThrow(() -> new ModelIntegrationStatus(
                ModelIntegrationMode.NATIVE, ModelIntegrationMode.NATIVE, ModelNativeProfile.STANDALONE,
                ModelIntegrationStatus.Fidelity.FULL, "standalone", false, Set.of(),
                Set.of(), ModelIntegrationVerification.NOT_REQUIRED, List.of(), Map.of(), 2,
                ModelIntegrationFallback.NONE, ""));
    }

    @Test void statusRetainsPartialDrawRejectionCounts() {
        ModelIntegrationStatus status = new ModelIntegrationStatus(
                ModelIntegrationMode.NATIVE, ModelIntegrationMode.NATIVE, ModelNativeProfile.HOST_NATIVE,
                ModelIntegrationStatus.Fidelity.DEGRADED, "host-native-entity", true, Set.of(),
                Set.of(), ModelIntegrationVerification.UNVERIFIED, List.of(),
                Map.of(ModelDrawRejection.UNSUPPORTED_SKINNING, 2), 3,
                ModelIntegrationFallback.NONE, "");
        org.junit.jupiter.api.Assertions.assertEquals(2, status.rejectedDrawCount());
    }
}
