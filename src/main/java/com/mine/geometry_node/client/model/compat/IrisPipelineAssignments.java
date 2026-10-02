package com.mine.geometry_node.client.model.compat;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

/** Mirrors Iris's process-wide assignment lifetime, independently of reloadable GPU programs. */
final class IrisPipelineAssignments {
    @FunctionalInterface interface Assignment { void run() throws ReflectiveOperationException; }
    private final Map<Object, Object> assigned = new IdentityHashMap<>();

    synchronized void ensure(Object pipeline, Object role, Assignment assignment) throws ReflectiveOperationException {
        Objects.requireNonNull(pipeline); Objects.requireNonNull(role);
        if (assigned.containsKey(pipeline)) {
            if (!Objects.equals(assigned.get(pipeline), role))
                throw new IllegalStateException("Conflicting model Iris pipeline role");
            return;
        }
        assignment.run();
        // Failed assignments remain retryable. Do not suppress Iris errors or claim external ownership.
        assigned.put(pipeline, role);
    }
}
