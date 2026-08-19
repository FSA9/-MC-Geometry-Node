package com.mine.geometry_node.client.model.render.backend.host.light.solve;

import com.mine.geometry_node.client.model.gpu.RenderThreadDispatcher;
import com.mine.geometry_node.client.model.render.backend.host.light.capture.WorldLightSnapshot;
import com.mine.geometry_node.client.model.render.backend.host.light.capture.WorldOccluderSnapshot;
import com.mine.geometry_node.client.model.render.backend.host.light.contract.HostLightFieldIdentity;
import com.mine.geometry_node.client.model.render.backend.host.light.contract.HostScalarLightField;
import com.mine.geometry_node.client.model.render.backend.host.light.instance.HostLocalLightRepository;
import com.mine.geometry_node.client.model.render.backend.host.light.occlusion.HostModelOccluderInstance;
import com.mine.geometry_node.client.model.render.backend.host.light.source.HostLightSourceSnapshot;
import com.mine.geometry_node.client.model.runtime.ModelInstanceId;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.RejectedExecutionException;

/** Owns bounded asynchronous F3 solves and complete-result render-thread publication. */
public final class HostLightingSolveCoordinator {
    private final RenderThreadDispatcher renderThread;
    private final HostLocalLightRepository repository;
    private final HostLightingExecutor executor;
    private final HostLightingMemoryBudget memoryBudget;
    private final HostUv2LightingSolver solver;

    public HostLightingSolveCoordinator(RenderThreadDispatcher renderThread,
                                        HostLocalLightRepository repository,
                                        HostLightingExecutor executor,
                                        HostLightingMemoryBudget memoryBudget,
                                        HostUv2LightingSolver solver) {
        this.renderThread = Objects.requireNonNull(renderThread, "renderThread");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.memoryBudget = Objects.requireNonNull(memoryBudget, "memoryBudget");
        this.solver = Objects.requireNonNull(solver, "solver");
    }

    /** Must be called on the render thread. Returns false when admission rejected the solve. */
    public boolean submit(SolveRequest request) {
        renderThread.assertRenderThread();
        Objects.requireNonNull(request, "request");
        HostLocalLightRepository.Target target = repository.beginTarget(request.identity());
        executor.cancel(request.identity().instanceId());
        long snapshotBytes;
        try {
            snapshotBytes = Math.addExact(request.world().residentBytes(), request.worldOccluder().residentBytes());
            snapshotBytes = Math.addExact(snapshotBytes,
                    solver.estimatedScratchBytes(request.world().cellCount()));
            snapshotBytes = Math.addExact(snapshotBytes,
                    solver.estimatedReceiverBytes(request.receivers().size()));
            snapshotBytes = Math.addExact(snapshotBytes,
                    solver.estimatedSourceIndexBytes(request.sources().sources().size()));
        } catch (ArithmeticException overflow) {
            repository.fail(target, HostLocalLightRepository.FailureKind.BUDGET_REJECTED);
            return false;
        }
        HostLightingMemoryBudget.Reservation scratch = memoryBudget.tryReserve(
                HostLightingMemoryBudget.Kind.SNAPSHOT, snapshotBytes);
        HostLightingMemoryBudget.Reservation field = memoryBudget.tryReserve(
                HostLightingMemoryBudget.Kind.FIELD,
                solver.estimatedFieldBytes(request.receivers().size()));
        if (scratch == null || field == null) {
            close(scratch);
            close(field);
            repository.fail(target, HostLocalLightRepository.FailureKind.BUDGET_REJECTED);
            return false;
        }
        try {
            executor.submit(request.identity().instanceId(), target.generation(), ticket ->
                            solve(request, target, ticket, scratch, field),
                    () -> { scratch.close(); field.close(); });
            return true;
        } catch (RejectedExecutionException rejected) {
            scratch.close();
            field.close();
            repository.fail(target, HostLocalLightRepository.FailureKind.TRANSIENT);
            return false;
        }
    }

    public void cancel(ModelInstanceId instanceId) {
        renderThread.assertRenderThread();
        executor.cancel(Objects.requireNonNull(instanceId, "instanceId"));
        repository.remove(instanceId);
    }

    public boolean pending(ModelInstanceId instanceId) {
        renderThread.assertRenderThread();
        return repository.targeting(Objects.requireNonNull(instanceId, "instanceId"));
    }

    private void solve(SolveRequest request, HostLocalLightRepository.Target target,
                       HostLightingExecutor.Ticket ticket,
                       HostLightingMemoryBudget.Reservation scratch,
                       HostLightingMemoryBudget.Reservation fieldReservation) {
        boolean fieldOwnsReservation = false;
        HostScalarLightField solvedField = null;
        try {
            ticket.checkCancelled();
            int[] packed = solver.solve(request.receivers(), request.sources(), request.world(),
                    request.worldOccluder(), request.modelOccluder(), ticket::checkCancelled);
            ticket.checkCancelled();
            solvedField = new HostScalarLightField(
                    request.identity(), packed, fieldReservation);
            fieldOwnsReservation = true;
            HostScalarLightField publication = solvedField;
            renderThread.execute(() -> publishIfCurrent(ticket, target, publication));
            solvedField = null;
        } catch (java.util.concurrent.CancellationException ignored) {
            // Replacement and reset are expected ownership transitions.
        } catch (RuntimeException failure) {
            renderThread.execute(() -> failIfCurrent(ticket, target));
            throw failure;
        } finally {
            scratch.close();
            if (solvedField != null) solvedField.close();
            if (!fieldOwnsReservation) fieldReservation.close();
        }
    }

    private void publishIfCurrent(HostLightingExecutor.Ticket ticket,
                                  HostLocalLightRepository.Target target,
                                  HostScalarLightField field) {
        if (!ticket.sessionActive()) {
            field.close();
            return;
        }
        repository.publish(target, field);
    }

    private void failIfCurrent(HostLightingExecutor.Ticket ticket,
                               HostLocalLightRepository.Target target) {
        if (ticket.sessionActive()) {
            repository.fail(target, HostLocalLightRepository.FailureKind.TRANSIENT);
        }
    }

    private static void close(AutoCloseable closeable) {
        if (closeable == null) return;
        try {
            closeable.close();
        } catch (Exception exception) {
            throw new IllegalStateException("failed to release lighting reservation", exception);
        }
    }

    public record SolveRequest(HostLightFieldIdentity identity,
                               List<HostUv2LightingSolver.Receiver> receivers,
                               HostLightSourceSnapshot sources,
                               WorldLightSnapshot world,
                               WorldOccluderSnapshot worldOccluder,
                               HostModelOccluderInstance modelOccluder) {
        public SolveRequest {
            Objects.requireNonNull(identity, "identity");
            receivers = List.copyOf(Objects.requireNonNull(receivers, "receivers"));
            Objects.requireNonNull(sources, "sources");
            Objects.requireNonNull(world, "world");
            Objects.requireNonNull(worldOccluder, "worldOccluder");
        }
    }
}
