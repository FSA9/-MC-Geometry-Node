package com.mine.geometry_node.client.model.render.backend.host.light.solve;

import com.mine.geometry_node.client.model.gpu.RenderThreadDispatcher;
import com.mine.geometry_node.client.model.render.backend.host.light.capture.WorldLightSnapshot;
import com.mine.geometry_node.client.model.render.backend.host.light.capture.WorldOccluderSnapshot;
import com.mine.geometry_node.client.model.render.backend.host.light.contract.HostLightFieldIdentity;
import com.mine.geometry_node.client.model.render.backend.host.light.contract.HostScalarLightField;
import com.mine.geometry_node.client.model.render.backend.host.light.instance.HostLocalLightRepository;
import com.mine.geometry_node.client.model.render.backend.host.light.source.HostLightSourceSnapshot;
import com.mine.geometry_node.client.model.runtime.ModelDimensionId;
import com.mine.geometry_node.client.model.runtime.ModelInstanceId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class HostLightingSolveCoordinatorTest {
    private static final ModelDimensionId DIMENSION = new ModelDimensionId("minecraft:overworld");

    @Test
    void workerPublishesOnlyCompleteFieldAndReleasesTransientReservation() throws Exception {
        QueuedRenderThread renderThread = new QueuedRenderThread();
        HostLocalLightRepository repository = new HostLocalLightRepository(renderThread,
                (field, completion) -> { field.close(); completion.run(); });
        HostLightingMemoryBudget memory = new HostLightingMemoryBudget(1024, 1024, 2048);
        try (HostLightingExecutor executor = new HostLightingExecutor("test-solve", 1, 2)) {
            HostLightingSolveCoordinator coordinator = new HostLightingSolveCoordinator(renderThread,
                    repository, executor, memory,
                    new HostUv2LightingSolver(HostUv2LightingSolver.Parameters.defaults()));
            ModelInstanceId instance = new ModelInstanceId("test");
            HostLightFieldIdentity identity = new HostLightFieldIdentity(instance, "asset", 1,
                    DIMENSION, 1, 1, 1);

            assertTrue(coordinator.submit(request(identity)));
            assertNull(repository.active(instance));
            awaitCompletion(executor);
            renderThread.runAll();

            HostScalarLightField field = assertInstanceOf(HostScalarLightField.class,
                    repository.active(instance));
            assertEquals(1, field.size());
            assertEquals(0, memory.diagnostics().snapshotBytes());
            assertEquals(Integer.BYTES, memory.diagnostics().fieldBytes());
            repository.close();
            assertEquals(0, memory.diagnostics().residentBytes());
        }
    }

    @Test
    void admissionFailureLeavesNoTargetOrReservation() {
        QueuedRenderThread renderThread = new QueuedRenderThread();
        HostLocalLightRepository repository = new HostLocalLightRepository(renderThread,
                (field, completion) -> { field.close(); completion.run(); });
        HostLightingMemoryBudget memory = new HostLightingMemoryBudget(1, 1024, 1024);
        try (HostLightingExecutor executor = new HostLightingExecutor("test-solve", 1, 1)) {
            HostLightingSolveCoordinator coordinator = new HostLightingSolveCoordinator(renderThread,
                    repository, executor, memory,
                    new HostUv2LightingSolver(HostUv2LightingSolver.Parameters.defaults()));
            ModelInstanceId instance = new ModelInstanceId("rejected");
            HostLightFieldIdentity identity = new HostLightFieldIdentity(instance, "asset", 1,
                    DIMENSION, 1, 1, 1);

            assertFalse(coordinator.submit(request(identity)));
            assertEquals(1, repository.diagnostics().budgetRejected());
            assertEquals(0, repository.diagnostics().targetFields());
            assertEquals(0, memory.diagnostics().residentBytes());
        }
    }

    private static HostLightingSolveCoordinator.SolveRequest request(HostLightFieldIdentity identity) {
        WorldLightSnapshot world = new WorldLightSnapshot(DIMENSION, 1,
                0, 0, 0, 1, 1, 1, new byte[]{0}, new byte[]{7}, new byte[]{0}, new byte[]{0});
        WorldOccluderSnapshot occluders = new WorldOccluderSnapshot(DIMENSION, 1,
                0, 0, 0, 1, 1, 1, List.of(), new short[]{0}, true, false);
        return new HostLightingSolveCoordinator.SolveRequest(identity,
                List.of(new HostUv2LightingSolver.Receiver(0.5, 0.5, 0.5, 0, 1, 0)),
                new HostLightSourceSnapshot(DIMENSION, 1, List.of()), world, occluders, null);
    }

    private static void awaitCompletion(HostLightingExecutor executor) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (executor.diagnostics().completed() == 0 && System.nanoTime() < deadline) Thread.sleep(5);
        assertEquals(1, executor.diagnostics().completed());
    }

    private static final class QueuedRenderThread implements RenderThreadDispatcher {
        private final Queue<Runnable> queue = new ConcurrentLinkedQueue<>();
        @Override public boolean isRenderThread() { return true; }
        @Override public void execute(Runnable task) { queue.add(task); }
        private void runAll() {
            Runnable task;
            while ((task = queue.poll()) != null) task.run();
        }
    }
}
