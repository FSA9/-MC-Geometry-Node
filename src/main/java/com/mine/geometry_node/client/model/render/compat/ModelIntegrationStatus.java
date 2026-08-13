package com.mine.geometry_node.client.model.render.compat;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Mode-neutral integration result exposed to diagnostics and future settings UI. */
public record ModelIntegrationStatus(ModelIntegrationMode requestedMode,
                                     ModelIntegrationMode effectiveMode,
                                     Fidelity fidelity,
                                     String profileId,
                                     boolean shaderEnvironmentPresent,
                                     Set<ModelIntegrationCapability> capabilities,
                                     Set<ModelCompatibilityLoss> semanticLosses,
                                     ModelIntegrationVerification verification,
                                     List<String> runtimeFaults,
                                     Map<ModelDrawRejection, Integer> rejectedDraws,
                                     long generation,
                                     ModelIntegrationFallback fallback,
                                     String fallbackDetail) {
    public enum Fidelity { FULL, DEGRADED, REJECTED }

    public ModelIntegrationStatus {
        if (requestedMode == null || effectiveMode == null || fidelity == null || fallback == null
                || verification == null) {
            throw new IllegalArgumentException("integration status fields must not be null");
        }
        if (profileId == null || profileId.isBlank()) throw new IllegalArgumentException("profileId must not be blank");
        capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
        semanticLosses = semanticLosses == null ? Set.of() : Set.copyOf(semanticLosses);
        runtimeFaults = runtimeFaults == null ? List.of() : List.copyOf(runtimeFaults);
        rejectedDraws = rejectedDraws == null ? Map.of() : Map.copyOf(rejectedDraws);
        fallbackDetail = fallbackDetail == null ? "" : fallbackDetail;
        if (generation < 0) throw new IllegalArgumentException("generation must not be negative");
        if (rejectedDraws.values().stream().anyMatch(count -> count == null || count <= 0)) {
            throw new IllegalArgumentException("rejected draw counts must be positive");
        }
        if (fidelity == Fidelity.FULL && (!semanticLosses.isEmpty() || !runtimeFaults.isEmpty()
                || !rejectedDraws.isEmpty() || (verification != ModelIntegrationVerification.VERIFIED
                && verification != ModelIntegrationVerification.NOT_REQUIRED))) {
            throw new IllegalArgumentException("FULL integration requires verified or not-required lossless operation");
        }
        if (requestedMode == effectiveMode && fallback != ModelIntegrationFallback.NONE) {
            throw new IllegalArgumentException("fallback requires different requested and effective modes");
        }
        if (requestedMode != effectiveMode && fallback == ModelIntegrationFallback.NONE) {
            throw new IllegalArgumentException("mode change requires a fallback reason");
        }
    }

    public int rejectedDrawCount() {
        return rejectedDraws.values().stream().mapToInt(Integer::intValue).sum();
    }
}
