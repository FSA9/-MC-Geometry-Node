package com.mine.geometry_node.client.model.render.backend.host.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HostStaticCacheMetricsTest {
    @Test
    void separatesLookupBuildRetirementAndBudgetEvents() {
        HostStaticCacheMetrics metrics = new HostStaticCacheMetrics();

        metrics.recordHit();
        metrics.recordHit();
        metrics.recordMiss();
        metrics.recordBuildStarted();
        metrics.recordBuildCompleted();
        metrics.recordBuildStarted();
        metrics.recordBuildFailed();
        metrics.recordRetired(3, 4096);
        metrics.recordBudgetWait();
        metrics.recordBudgetReject();

        assertEquals(new HostStaticCacheMetrics.Diagnostics(
                2, 1, 2, 1, 1, 3, 4096, 1, 1), metrics.diagnostics());
    }

    @Test
    void resetClearsAllCumulativeCounters() {
        HostStaticCacheMetrics metrics = new HostStaticCacheMetrics();
        metrics.recordHit();
        metrics.recordRetired(1, 32);
        metrics.recordBudgetReject();

        metrics.reset();

        assertEquals(new HostStaticCacheMetrics.Diagnostics(
                0, 0, 0, 0, 0, 0, 0, 0, 0), metrics.diagnostics());
    }

    @Test
    void rejectsInvalidRetirementDeltas() {
        HostStaticCacheMetrics metrics = new HostStaticCacheMetrics();
        assertThrows(IllegalArgumentException.class, () -> metrics.recordRetired(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> metrics.recordRetired(0, -1));
    }
}
