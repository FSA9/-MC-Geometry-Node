package com.mine.geometry_node.client.model.render.backend.host.light.capture;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HostWorldLightCaptureBudgetTest {
    @Test
    void admitsOnlyFramePrefixAndRefillsOncePerNewFrame() {
        HostWorldLightCaptureBudget budget = new HostWorldLightCaptureBudget(10);
        budget.beginFrame(1);
        assertEquals(7, budget.claim(7));
        assertEquals(3, budget.claim(7));
        budget.beginFrame(1);
        assertEquals(0, budget.claim(1));
        budget.beginFrame(2);
        assertEquals(10, budget.claim(20));
        assertThrows(IllegalArgumentException.class, () -> budget.beginFrame(1));
    }
}
