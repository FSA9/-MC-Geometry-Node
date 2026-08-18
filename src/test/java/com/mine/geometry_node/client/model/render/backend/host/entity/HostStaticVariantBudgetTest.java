package com.mine.geometry_node.client.model.render.backend.host.entity;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HostStaticVariantBudgetTest {
    @Test
    void enforcesPerArtifactAndGlobalBytesAndReleasesExactlyOnce() {
        HostStaticVariantBudget budget = new HostStaticVariantBudget(10, 15);
        Object first = new Object();
        Object second = new Object();
        HostStaticVariantBudget.Reservation a = budget.tryReserve(first, 7);
        HostStaticVariantBudget.Reservation b = budget.tryReserve(second, 8);

        assertNotNull(a);
        assertNotNull(b);
        assertNull(budget.tryReserve(first, 4));
        assertNull(budget.tryReserve(new Object(), 1));
        assertEquals(15, budget.reservedBytes());
        assertEquals(7, budget.artifactBytes(first));
        assertEquals(new HostStaticVariantBudget.Diagnostics(0, 15, 15, 0), budget.diagnostics());
        a.markResident();
        b.markResident();
        assertEquals(new HostStaticVariantBudget.Diagnostics(15, 15, 15, 2), budget.diagnostics());
        assertEquals(new HostStaticVariantBudget.ArtifactDiagnostics(7, 7, 1),
                budget.artifactDiagnostics(first));

        a.close();
        a.close();
        assertEquals(8, budget.reservedBytes());
        assertEquals(new HostStaticVariantBudget.Diagnostics(8, 8, 15, 1), budget.diagnostics());
        assertEquals(new HostStaticVariantBudget.ArtifactDiagnostics(0, 0, 0),
                budget.artifactDiagnostics(first));
        HostStaticVariantBudget.Reservation replacement = budget.tryReserve(first, 7);
        assertNotNull(replacement);
        assertEquals(new HostStaticVariantBudget.Diagnostics(8, 15, 15, 1), budget.diagnostics());
        replacement.markResident();
        replacement.markResident();
        assertEquals(new HostStaticVariantBudget.Diagnostics(15, 15, 15, 2), budget.diagnostics());
        replacement.close();
        b.close();
        assertEquals(0, budget.reservedBytes());
        assertEquals(new HostStaticVariantBudget.Diagnostics(0, 0, 15, 0), budget.diagnostics());
    }

    @Test
    void rejectsInvalidClaims() {
        HostStaticVariantBudget budget = new HostStaticVariantBudget(10, 20);
        assertThrows(NullPointerException.class, () -> budget.tryReserve(null, 1));
        assertThrows(IllegalArgumentException.class, () -> budget.tryReserve(new Object(), 0));
    }

    @Test
    void batchAdmissionIsAtomicAndClaimsDoNotDoubleCount() {
        HostStaticVariantBudget budget = new HostStaticVariantBudget(10, 20, 10, 10);
        Object artifact = new Object();
        HostStaticVariantBudget.BatchReservation batch = budget.tryReserveBatch(artifact, List.of(2L, 3L, 3L));

        assertNotNull(batch);
        assertEquals(8, budget.reservedBytes());
        assertEquals(8, budget.artifactBytes(artifact));
        HostStaticVariantBudget.Reservation three = batch.claim(3);
        HostStaticVariantBudget.Reservation two = batch.claim(2);
        HostStaticVariantBudget.Reservation secondThree = batch.claim(3);
        assertNotNull(three);
        assertNotNull(two);
        assertNotNull(secondThree);
        assertNull(batch.claim(3));
        assertEquals(0, batch.unclaimedBytes());
        assertEquals(8, budget.reservedBytes(), "claims reuse the atomic batch charge");

        three.markResident();
        two.markResident();
        assertEquals(new HostStaticVariantBudget.Diagnostics(5, 8, 30, 2), budget.diagnostics());
        batch.close();
        assertEquals(8, budget.reservedBytes());
        three.close();
        two.close();
        secondThree.close();
        assertEquals(new HostStaticVariantBudget.Diagnostics(0, 0, 30, 0), budget.diagnostics());
    }

    @Test
    void failedBatchAdmissionAndAbortNeverLeavePartialCharges() {
        HostStaticVariantBudget budget = new HostStaticVariantBudget(10, 20, 10, 10);
        Object artifact = new Object();

        assertNull(budget.tryReserveBatch(artifact, List.of(6L, 5L)));
        assertEquals(0, budget.reservedBytes());
        assertThrows(IllegalArgumentException.class,
                () -> budget.tryReserveBatch(artifact, List.of(2L, 0L, 3L)));
        assertEquals(0, budget.reservedBytes());

        HostStaticVariantBudget.BatchReservation batch = budget.tryReserveBatch(artifact, List.of(2L, 3L, 4L));
        HostStaticVariantBudget.Reservation claimed = assertDoesNotThrow(() -> batch.claim(3));
        assertNotNull(claimed);
        batch.close();
        assertEquals(3, budget.reservedBytes(), "abort releases only the unclaimed portion");
        claimed.close();
        assertEquals(0, budget.reservedBytes());
    }

    @Test
    void onlyReplacementBatchCanUseDedicatedHeadroom() {
        HostStaticVariantBudget budget = new HostStaticVariantBudget(10, 20, 10, 10);
        Object first = new Object();
        Object second = new Object();
        HostStaticVariantBudget.BatchReservation steady = budget.tryReserveBatch(first, List.of(10L));
        assertNotNull(steady);
        assertNull(budget.tryReserveBatch(first, List.of(1L)));

        HostStaticVariantBudget.BatchReservation replacement =
                budget.tryReserveReplacementBatch(first, List.of(6L, 4L), 10L);
        assertNotNull(replacement);
        assertEquals(20, budget.artifactBytes(first));
        assertNull(budget.tryReserveBatch(second, List.of(10L)),
                "steady admission is blocked until replacement promotion");
        HostStaticVariantBudget.Reservation six = replacement.claim(6);
        HostStaticVariantBudget.Reservation four = replacement.claim(4);
        assertNotNull(six);
        assertNotNull(four);
        replacement.close();

        steady.close();
        replacement.promoteReplacementToSteady();
        HostStaticVariantBudget.BatchReservation otherSteady = budget.tryReserveBatch(second, List.of(10L));
        assertNotNull(otherSteady, "promotion reopens only the remaining steady capacity");
        six.close();
        four.close();
        otherSteady.close();
        assertEquals(0, budget.reservedBytes());
    }

    @Test
    void closingPublishedNewBuffersBeforeOldFenceMakesLatePromotionANoOp() {
        HostStaticVariantBudget budget = new HostStaticVariantBudget(10, 20, 10, 10);
        Object artifact = new Object();
        HostStaticVariantBudget.BatchReservation initial = budget.tryReserveBatch(artifact, List.of(8L));
        HostStaticVariantBudget.Reservation old = initial.claim(8);
        assertNotNull(old);
        initial.close();
        HostStaticVariantBudget.BatchReservation replacement =
                budget.tryReserveReplacementBatch(artifact, List.of(8L), 8L);
        HostStaticVariantBudget.Reservation fresh = replacement.claim(8);
        assertNotNull(fresh);
        replacement.close();

        fresh.close();
        old.close();

        assertDoesNotThrow(replacement::promoteReplacementToSteady);
        assertEquals(0, budget.reservedBytes());
    }
}
