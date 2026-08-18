package com.mine.geometry_node.client.model.render.backend.host.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HostPreparationMemoryBudgetTest {
    @Test
    void enforcesPerArtifactAndGlobalLimitsAndReleasesExactlyOnce() {
        HostPreparationMemoryBudget budget = new HostPreparationMemoryBudget(10, 15);
        HostPreparationMemoryBudget.Reservation first = budget.reserve(8);
        assertEquals(8, budget.reservedBytes());
        assertEquals(new HostPreparationMemoryBudget.Diagnostics(8, 15, 1), budget.diagnostics());

        assertThrows(HostPreparationMemoryBudget.HostPreparationBudgetExceeded.class,
                () -> budget.reserve(8));
        assertThrows(HostPreparationMemoryBudget.HostPreparationBudgetExceeded.class,
                () -> budget.reserve(11));

        first.close();
        first.close();
        assertEquals(0, budget.reservedBytes());
        assertEquals(0, budget.diagnostics().artifacts());

        HostPreparationMemoryBudget.Reservation full = budget.reserve(10);
        assertEquals(10, full.bytes());
        full.close();
        assertEquals(0, budget.reservedBytes());
    }
}
