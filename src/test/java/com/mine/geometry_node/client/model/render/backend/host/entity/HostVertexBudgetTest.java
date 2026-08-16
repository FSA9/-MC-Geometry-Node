package com.mine.geometry_node.client.model.render.backend.host.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HostVertexBudgetTest {
    @Test
    void rejectsSingleOrAccumulatedSubmissionBeyondFrameLimit() {
        HostVertexBudget budget = new HostVertexBudget();
        assertFalse(budget.reserve(HostVertexBudget.MAX_VERTICES_PER_FRAME + 1));
        assertTrue(budget.reserve(600_000));
        assertFalse(budget.reserve(400_001));
        assertTrue(budget.reserve(400_000));
    }
}
