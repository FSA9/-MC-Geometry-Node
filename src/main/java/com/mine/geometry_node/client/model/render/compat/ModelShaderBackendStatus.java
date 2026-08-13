package com.mine.geometry_node.client.model.render.compat;

import java.util.List;

public record ModelShaderBackendStatus(String id, Fidelity fidelity, boolean shaderEnvironmentPresent,
                                       boolean selectable, List<String> losses, String diagnostic) {
    public enum Fidelity { FULL, COMPATIBILITY, UNAVAILABLE }

    public ModelShaderBackendStatus {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be blank");
        if (fidelity == null) throw new IllegalArgumentException("fidelity must not be null");
        losses = losses == null ? List.of() : List.copyOf(losses);
        diagnostic = diagnostic == null ? "" : diagnostic;
        if (fidelity == Fidelity.FULL && !losses.isEmpty()) {
            throw new IllegalArgumentException("FULL backend cannot report fidelity losses");
        }
        if (selectable && fidelity == Fidelity.UNAVAILABLE) {
            throw new IllegalArgumentException("UNAVAILABLE backend cannot be selectable");
        }
    }

    public boolean degraded() { return fidelity == Fidelity.COMPATIBILITY; }

    public static ModelShaderBackendStatus full(String id, boolean environmentPresent, String diagnostic) {
        return new ModelShaderBackendStatus(id, Fidelity.FULL, environmentPresent, true, List.of(), diagnostic);
    }

    public static ModelShaderBackendStatus unavailable(String id, boolean environmentPresent, String diagnostic) {
        return new ModelShaderBackendStatus(id, Fidelity.UNAVAILABLE, environmentPresent, false, List.of(), diagnostic);
    }

    public static ModelShaderBackendStatus compatibility(String id, List<String> losses, String diagnostic) {
        return new ModelShaderBackendStatus(id, Fidelity.COMPATIBILITY, true, true, losses, diagnostic);
    }
}
