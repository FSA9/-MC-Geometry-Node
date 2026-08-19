package com.mine.geometry_node.client.model.render.backend.host.light.solve;

import com.mine.geometry_node.client.model.runtime.ModelInstanceId;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class HostLightingExecutorTest {
    @Test
    void replacementCancelsOlderInstanceWork() throws Exception {
        try (HostLightingExecutor executor = new HostLightingExecutor("test-light", 1, 2)) {
            ModelInstanceId blocker = new ModelInstanceId("blocker");
            ModelInstanceId target = new ModelInstanceId("target");
            CountDownLatch release = new CountDownLatch(1);
            CountDownLatch newestRan = new CountDownLatch(1);
            executor.submit(blocker, 1, ticket -> release.await(5, TimeUnit.SECONDS));
            HostLightingExecutor.Ticket old = executor.submit(target, 1, ticket -> fail("old work must be coalesced"));
            executor.submit(target, 2, ticket -> newestRan.countDown());

            assertTrue(old.cancelled());
            release.countDown();
            assertTrue(newestRan.await(5, TimeUnit.SECONDS));
            assertTrue(executor.diagnostics().cancelled() >= 1);
        }
    }

    @Test
    void queueIsBoundedAndRejectsExcessWork() throws Exception {
        try (HostLightingExecutor executor = new HostLightingExecutor("test-light", 1, 1)) {
            CountDownLatch running = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            executor.submit(new ModelInstanceId("running"), 1, ticket -> {
                running.countDown();
                release.await(5, TimeUnit.SECONDS);
            });
            assertTrue(running.await(5, TimeUnit.SECONDS));
            executor.submit(new ModelInstanceId("queued"), 1, ticket -> {});
            assertThrows(java.util.concurrent.RejectedExecutionException.class,
                    () -> executor.submit(new ModelInstanceId("rejected"), 1, ticket -> {}));
            release.countDown();
            assertEquals(1, executor.diagnostics().rejected());
        }
    }

    @Test
    void failureIsReportedWithoutRetainingThrowable() throws Exception {
        try (HostLightingExecutor executor = new HostLightingExecutor("test-light", 1, 1)) {
            ModelInstanceId instanceId = new ModelInstanceId("failed");
            CountDownLatch ran = new CountDownLatch(1);
            executor.submit(instanceId, 7, ticket -> {
                ran.countDown();
                throw new IllegalStateException("solve failed");
            });

            assertTrue(ran.await(5, TimeUnit.SECONDS));
            HostLightingExecutor.Diagnostics diagnostics = awaitFailure(executor);
            assertNotNull(diagnostics.lastFailure());
            HostLightingExecutor.Failure failure = diagnostics.lastFailure();
            assertEquals(instanceId, failure.instanceId());
            assertEquals(executor.session(), failure.session());
            assertEquals(7, failure.generation());
            assertEquals(IllegalStateException.class.getName(), failure.type());
            assertEquals("solve failed", failure.message());
        }
    }

    @Test
    void closeInvalidatesTicketsAndSeparatesReplacementSession() throws Exception {
        HostLightingExecutor oldExecutor = new HostLightingExecutor("test-light", 1, 1);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        HostLightingExecutor.Ticket oldTicket = oldExecutor.submit(new ModelInstanceId("old"), 3, ticket -> {
            started.countDown();
            while (ticket.sessionActive()) {
                try {
                    if (release.await(10, TimeUnit.MILLISECONDS)) return;
                } catch (InterruptedException ignored) {
                    // Deliberately continue until the session contract becomes inactive.
                }
            }
        });
        assertTrue(started.await(5, TimeUnit.SECONDS));

        oldExecutor.close();
        assertTrue(oldExecutor.closed());
        assertTrue(oldTicket.cancelled());
        assertFalse(oldTicket.sessionActive());
        assertThrows(RejectedExecutionException.class,
                () -> oldExecutor.submit(new ModelInstanceId("late"), 4, ticket -> {}));

        try (HostLightingExecutor replacement = new HostLightingExecutor("test-light", 1, 1)) {
            assertNotEquals(oldTicket.session(), replacement.session());
            assertFalse(oldTicket.sessionActive());
        } finally {
            release.countDown();
        }
    }

    @Test
    void queuedReplacementReportsAbandonedOwnershipExactlyOnce() throws Exception {
        try (HostLightingExecutor executor = new HostLightingExecutor("test-light", 1, 2)) {
            CountDownLatch blockerStarted = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            CountDownLatch abandoned = new CountDownLatch(1);
            CountDownLatch replacementRan = new CountDownLatch(1);
            executor.submit(new ModelInstanceId("blocker"), 1, ticket -> {
                blockerStarted.countDown();
                release.await(5, TimeUnit.SECONDS);
            });
            assertTrue(blockerStarted.await(5, TimeUnit.SECONDS));
            ModelInstanceId target = new ModelInstanceId("target");
            executor.submit(target, 1, ticket -> fail("replaced work must not run"), abandoned::countDown);
            executor.submit(target, 2, ticket -> replacementRan.countDown());

            assertTrue(abandoned.await(5, TimeUnit.SECONDS));
            release.countDown();
            assertTrue(replacementRan.await(5, TimeUnit.SECONDS));
            assertEquals(0, abandoned.getCount());
        }
    }

    private static HostLightingExecutor.Diagnostics awaitFailure(HostLightingExecutor executor)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        HostLightingExecutor.Diagnostics diagnostics;
        do {
            diagnostics = executor.diagnostics();
            if (diagnostics.failed() > 0) return diagnostics;
            Thread.sleep(5);
        } while (System.nanoTime() < deadline);
        return diagnostics;
    }
}
