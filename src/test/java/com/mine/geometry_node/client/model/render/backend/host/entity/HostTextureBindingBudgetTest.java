package com.mine.geometry_node.client.model.render.backend.host.entity;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HostTextureBindingBudgetTest {
    @Test
    void admitsAndPublishesACompleteBindingSet() {
        HostTextureBindingBudget budget = new HostTextureBindingBudget(20, 30, 5, 8);
        Object artifact = new Object();
        var albedo = new HostTextureBindingBudget.Footprint(6, 1);
        var labPbr = new HostTextureBindingBudget.Footprint(8, 3);

        var batch = budget.tryReserveBatch(artifact, List.of(albedo, labPbr));
        assertNotNull(batch);
        assertEquals(new HostTextureBindingBudget.ArtifactDiagnostics(14, 4),
                budget.artifactDiagnostics(artifact));
        assertEquals(new HostTextureBindingBudget.Diagnostics(0, 14, 30, 0, 4, 8, 0, 1),
                budget.diagnostics());
        var first = batch.claim(albedo);
        var second = batch.claim(labPbr);
        assertNotNull(first);
        assertNotNull(second);
        assertTrue(batch.fullyClaimed());
        first.markResident();
        second.markResident();
        batch.close();
        assertEquals(new HostTextureBindingBudget.Diagnostics(14, 14, 30, 4, 4, 8, 2, 1),
                budget.diagnostics());

        first.close();
        second.close();
        assertEquals(new HostTextureBindingBudget.Diagnostics(0, 0, 30, 0, 0, 8, 0, 0),
                budget.diagnostics());
        assertEquals(new HostTextureBindingBudget.ArtifactDiagnostics(0, 0),
                budget.artifactDiagnostics(artifact));
    }

    @Test
    void rejectedBatchDoesNotPartiallyConsumeCapacity() {
        HostTextureBindingBudget budget = new HostTextureBindingBudget(10, 15, 3, 4);
        Object artifact = new Object();

        assertNull(budget.tryReserveBatch(artifact,
                List.of(new HostTextureBindingBudget.Footprint(6, 2),
                        new HostTextureBindingBudget.Footprint(5, 1))));
        assertEquals(new HostTextureBindingBudget.Diagnostics(0, 0, 15, 0, 0, 4, 0, 0),
                budget.diagnostics());
    }

    @Test
    void closingPartialBatchReleasesOnlyUnclaimedCapacity() {
        HostTextureBindingBudget budget = new HostTextureBindingBudget(20, 30, 5, 8);
        var footprint = new HostTextureBindingBudget.Footprint(5, 1);
        var batch = budget.tryReserveBatch(new Object(), List.of(footprint, footprint));
        assertNotNull(batch);
        var claimed = batch.claim(footprint);
        assertNotNull(claimed);

        batch.close();
        assertEquals(new HostTextureBindingBudget.Diagnostics(0, 5, 30, 0, 1, 8, 0, 1),
                budget.diagnostics());
        claimed.close();
        assertEquals(0, budget.diagnostics().reservedBytes());
        assertEquals(0, budget.diagnostics().artifacts());
    }

    @Test
    void enforcesGlobalCapacityAcrossArtifacts() {
        HostTextureBindingBudget budget = new HostTextureBindingBudget(12, 15, 3, 4);
        var first = budget.tryReserveBatch(new Object(),
                List.of(new HostTextureBindingBudget.Footprint(9, 2)));
        assertNotNull(first);
        assertNull(budget.tryReserveBatch(new Object(),
                List.of(new HostTextureBindingBudget.Footprint(7, 1))));
        first.close();
        assertNotNull(budget.tryReserveBatch(new Object(),
                List.of(new HostTextureBindingBudget.Footprint(7, 1))));
    }
}
