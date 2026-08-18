package com.mine.geometry_node.client.model.render.backend.host.light.diagnostics;

/** Immutable diagnostics consumed by tests and, later, the model HUD. */
public record HostLocalLightDiagnostics(int instances, int activeFields, int targetFields,
                                        int retiringFields, long activeBytes, long retiringBytes,
                                        long published, long staleCompletions, long cancelled,
                                        long transientFailures, long terminalUnsupported,
                                        long budgetRejected) {
    public HostLocalLightDiagnostics {
        if (instances < 0 || activeFields < 0 || targetFields < 0 || retiringFields < 0
                || activeBytes < 0 || retiringBytes < 0 || published < 0 || staleCompletions < 0
                || cancelled < 0 || transientFailures < 0 || terminalUnsupported < 0 || budgetRejected < 0) {
            throw new IllegalArgumentException("diagnostics values must not be negative");
        }
    }
}
