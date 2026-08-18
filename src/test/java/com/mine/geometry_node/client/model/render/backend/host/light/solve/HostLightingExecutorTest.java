package com.mine.geometry_node.client.model.render.backend.host.light.solve;

import com.mine.geometry_node.client.model.runtime.ModelInstanceId;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
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
}
