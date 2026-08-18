package com.mine.geometry_node.client.model.render.backend.host.light.solve;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HostLightingMemoryBudgetTest {
    @Test
    void enforcesKindAndGlobalLimitsAndReleasesIdempotently() {
        HostLightingMemoryBudget budget = new HostLightingMemoryBudget(10, 20, 24);
        var snapshot = budget.tryReserve(HostLightingMemoryBudget.Kind.SNAPSHOT, 10);
        var field = budget.tryReserve(HostLightingMemoryBudget.Kind.FIELD, 14);

        assertNotNull(snapshot);
        assertNotNull(field);
        assertNull(budget.tryReserve(HostLightingMemoryBudget.Kind.FIELD, 1));
        assertEquals(24, budget.diagnostics().residentBytes());
        field.close();
        field.close();
        assertEquals(10, budget.diagnostics().residentBytes());
        assertEquals(1, budget.diagnostics().reservations());
    }
}
