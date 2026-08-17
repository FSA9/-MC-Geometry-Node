package com.mine.geometry_node.client.model.render.backend.host.entity;

import org.junit.jupiter.api.Test;

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

        a.close();
        a.close();
        assertEquals(8, budget.reservedBytes());
        HostStaticVariantBudget.Reservation replacement = budget.tryReserve(first, 7);
        assertNotNull(replacement);
        replacement.close();
        b.close();
        assertEquals(0, budget.reservedBytes());
    }

    @Test
    void rejectsInvalidClaims() {
        HostStaticVariantBudget budget = new HostStaticVariantBudget(10, 20);
        assertThrows(NullPointerException.class, () -> budget.tryReserve(null, 1));
        assertThrows(IllegalArgumentException.class, () -> budget.tryReserve(new Object(), 0));
    }
}
