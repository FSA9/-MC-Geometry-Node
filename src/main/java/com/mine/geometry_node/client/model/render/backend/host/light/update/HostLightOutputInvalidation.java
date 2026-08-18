package com.mine.geometry_node.client.model.render.backend.host.light.update;

import java.util.Set;

/** Non-spatial shader/layout/output target. */
public record HostLightOutputInvalidation(long environmentGeneration,
                                          long revision) implements HostLightInvalidation {
    public HostLightOutputInvalidation {
        if (environmentGeneration < 0 || revision < 0) {
            throw new IllegalArgumentException("output invalidation generations must not be negative");
        }
    }

    @Override public Set<HostLightInvalidationKind> causes() {
        return Set.of(HostLightInvalidationKind.OUTPUT);
    }
}
