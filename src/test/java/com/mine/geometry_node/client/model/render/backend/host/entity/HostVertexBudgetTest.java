package com.mine.geometry_node.client.model.render.backend.host.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HostVertexBudgetTest {
    @Test
    void rejectsSingleOrAccumulatedSubmissionBeyondFrameLimit() {
        HostVertexBudget budget = new HostVertexBudget();
        assertFalse(budget.reserve(HostVertexBudget.MAX_VERTICES_PER_FRAME + 1));
        assertEquals(0, budget.submitted());
        assertTrue(budget.reserve(600_000));
        assertFalse(budget.reserve(400_001));
        assertEquals(600_000, budget.submitted());
        assertTrue(budget.reserve(400_000));
        assertEquals(HostVertexBudget.MAX_VERTICES_PER_FRAME, budget.submitted());
    }
}
