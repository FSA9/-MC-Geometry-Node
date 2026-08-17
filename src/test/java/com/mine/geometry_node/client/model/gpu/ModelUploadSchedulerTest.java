package com.mine.geometry_node.client.model.gpu;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ModelUploadSchedulerTest {
    @Test
    void advancesTransactionAcrossFrameBudgetsAndPublishesOnlyAtEnd() {
        ModelUploadScheduler scheduler = new ModelUploadScheduler(new ImmediateRenderThread(), 10, 2, Long.MAX_VALUE);
        FakeWork work = new FakeWork(List.of(6L, 6L));
        assertTrue(scheduler.enqueue(work));

        scheduler.pump();
        assertEquals(1, work.steps);
        assertFalse(work.completed);
        assertEquals(1, scheduler.diagnostics().queuedItems());

        scheduler.pump();
        assertEquals(2, work.steps);
        assertTrue(work.completed);
        assertEquals(0, scheduler.diagnostics().queuedItems());
        assertEquals(12, scheduler.diagnostics().uploadedBytes());
    }

    @Test
    void oversizedStepRunsAloneSoItCannotStarve() {
        ModelUploadScheduler scheduler = new ModelUploadScheduler(new ImmediateRenderThread(), 10, 1, Long.MAX_VALUE);
        FakeWork oversized = new FakeWork(List.of(20L));
        FakeWork next = new FakeWork(List.of(1L));
        scheduler.enqueue(oversized);
        scheduler.enqueue(next);

        scheduler.pump();

        assertTrue(oversized.completed);
        assertEquals(0, next.steps);
        scheduler.pump();
        assertTrue(next.completed);
    }

    private static final class FakeWork implements ModelUploadScheduler.WorkItem {
        private final List<Long> bytes;
        private int steps;
        private boolean completed;

        private FakeWork(List<Long> bytes) { this.bytes = new ArrayList<>(bytes); }
        @Override public long nextBytes() { return bytes.get(steps); }
        @Override public int nextObjects() { return 1; }
        @Override public boolean cancelled() { return false; }
        @Override public boolean runStep() { return ++steps == bytes.size(); }
        @Override public void completed() { completed = true; }
        @Override public void cancelledByScheduler() { fail("work was unexpectedly cancelled"); }
        @Override public void failed(Throwable failure) { fail(failure); }
    }

    private static final class ImmediateRenderThread implements RenderThreadDispatcher {
        @Override public boolean isRenderThread() { return true; }
        @Override public void execute(Runnable task) { task.run(); }
    }
}
