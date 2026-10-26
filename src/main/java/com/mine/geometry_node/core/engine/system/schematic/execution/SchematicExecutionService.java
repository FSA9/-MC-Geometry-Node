package com.mine.geometry_node.core.engine.system.schematic.execution;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.core.config.SchematicServerConfig;
import com.mine.geometry_node.core.engine.blueprint.runtime.wait.BlueprintExecutionHandle;
import com.mine.geometry_node.core.engine.blueprint.runtime.wait.BlueprintExternalWaitHandler;
import com.mine.geometry_node.core.engine.blueprint.runtime.wait.BlueprintExternalWaitRequest;
import com.mine.geometry_node.core.engine.lifecycle.ServerEngine;
import com.mine.geometry_node.core.engine.system.schematic.placement.SchematicPlacementManager;
import com.mine.geometry_node.core.utils.io.BoundedIoExecutor;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.engine.system.schematic.SchematicData;
import com.mine.geometry_node.core.engine.system.schematic.SchematicReader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.ArrayDeque;
import java.util.Set;
import java.util.UUID;
import java.nio.file.Path;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Parses schematics off-thread and advances world mutations under a per-level tick budget. */
public final class SchematicExecutionService implements ServerEngine, BlueprintExternalWaitHandler {
    public static final String ID = "geometry_node:schematic_execution";
    public static final SchematicExecutionService INSTANCE = new SchematicExecutionService();

    private static final int IO_QUEUE_CAPACITY = 32;
    private static final int OPERATIONS_PER_LEVEL_TICK = 4_096;
    private static final int OPERATIONS_PER_TASK_QUANTUM = 512;

    private final Map<MinecraftServer, BoundedIoExecutor> executors = new ConcurrentHashMap<>();
    private final Map<BlueprintExecutionHandle, PendingExecution> pending = new ConcurrentHashMap<>();
    private final Map<ServerLevel, ArrayDeque<BlueprintExecutionHandle>> readyQueues = new ConcurrentHashMap<>();
    private final Map<ServerLevel, Set<String>> reservedPlacementKeys = new ConcurrentHashMap<>();

    private SchematicExecutionService() {
    }

    /** Reserves a unique placement key until its incremental task records or cancels the placement. */
    public PlacementKeyReservation reservePlacementKey(ServerLevel level, String requestedKey,
                                                        boolean uniqueIfExists) {
        String baseKey = requestedKey == null ? "" : requestedKey.trim();
        if (!uniqueIfExists || baseKey.isEmpty()) {
            return new PlacementKeyReservation(level, baseKey, false);
        }
        Set<String> reservations = reservedPlacementKeys.computeIfAbsent(level,
                ignored -> ConcurrentHashMap.newKeySet());
        String candidate = baseKey;
        for (int index = 0; index < Integer.MAX_VALUE; index++) {
            if (index > 0) candidate = baseKey + "_" + index;
            if (SchematicPlacementManager.get(level, candidate).isEmpty() && reservations.add(candidate)) {
                return new PlacementKeyReservation(level, candidate, true);
            }
        }
        candidate = baseKey + "_" + UUID.randomUUID();
        reservations.add(candidate);
        return new PlacementKeyReservation(level, candidate, true);
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String externalWaitId() {
        return ID;
    }

    @Override
    public boolean beginExternalWait(BlueprintExecutionHandle handle, BlueprintExternalWaitRequest rawRequest) {
        if (!(rawRequest instanceof SchematicExecutionRequest request)) return false;
        ServerLevel level = handle.level();
        if (level == null) return false;

        MinecraftServer server = level.getServer();
        BoundedIoExecutor executor = executors.computeIfAbsent(server,
                ignored -> new BoundedIoExecutor("GeometryNode-Schematic-IO", 1, IO_QUEUE_CAPACITY));
        AtomicBoolean cancelled = new AtomicBoolean();
        CompletableFuture<SchematicData> future = executor.submit(() -> {
            if (cancelled.get()) throw new CancellationException("Schematic load was cancelled");
            Path path = SchematicPaths.resolveServerPath(server, request.relativePath());
            SchematicData data = SchematicReader.read(path, request.includeAir(),
                    SchematicServerConfig.maxFileBytes());
            if (cancelled.get()) throw new CancellationException("Schematic load was cancelled");
            return data;
        });
        PendingExecution execution = new PendingExecution(server, level, request, future, cancelled);
        PendingExecution replaced = pending.put(handle, execution);
        if (replaced != null) replaced.cancel();
        future.whenComplete((data, error) -> server.execute(() -> finishLoad(handle, execution, data, error)));
        return true;
    }

    private void finishLoad(BlueprintExecutionHandle handle, PendingExecution execution,
                            @Nullable SchematicData data, @Nullable Throwable error) {
        if (pending.get(handle) != execution || !handle.isActive()) return;
        if (error != null || data == null) {
            fail(handle, execution, "load", error);
            return;
        }
        try {
            execution.task = execution.request.taskFactory().create(data);
            if (execution.task == null) {
                fail(handle, execution, "create task", null);
                return;
            }
            readyQueues.computeIfAbsent(execution.level, ignored -> new ArrayDeque<>()).addLast(handle);
        } catch (Exception exception) {
            fail(handle, execution, "create task", exception);
        }
    }

    @Override
    public void tickLevel(ServerLevel level) {
        ArrayDeque<BlueprintExecutionHandle> queue = readyQueues.get(level);
        if (queue == null || queue.isEmpty()) return;
        int remaining = OPERATIONS_PER_LEVEL_TICK;
        int visited = 0;
        int readyAtTickStart = queue.size();
        while (remaining > 0 && visited < readyAtTickStart) {
            BlueprintExecutionHandle handle = queue.pollFirst();
            if (handle == null) break;
            visited++;
            PendingExecution execution = pending.get(handle);
            if (execution == null || execution.level != level || execution.task == null) continue;
            if (!handle.isActive()) {
                if (pending.remove(handle, execution)) execution.cancel();
                continue;
            }
            try {
                int allowance = Math.min(remaining, OPERATIONS_PER_TASK_QUANTUM);
                SchematicExecutionRequest.TickResult result = execution.task.tick(allowance);
                int consumed = Math.min(allowance, result.operations());
                remaining -= Math.max(1, consumed);
                if (result.complete()) {
                    execution.task.complete(handle::setWaitingNodeResult);
                    if (pending.remove(handle, execution)) {
                        handle.resume(StandardPorts.FLOW_OUT.getId());
                    }
                } else if (pending.get(handle) == execution) {
                    queue.addLast(handle);
                }
            } catch (Exception exception) {
                fail(handle, execution, "execute", exception);
            }
        }
        if (queue.isEmpty()) readyQueues.remove(level, queue);
    }

    private void fail(BlueprintExecutionHandle handle, PendingExecution execution,
                      String phase, @Nullable Throwable error) {
        if (!pending.remove(handle, execution)) return;
        try {
            execution.cancel();
            Throwable cause = rootCause(error);
            String detail = cause == null || cause.getMessage() == null ? "unknown error" : cause.getMessage();
            GeometryNode.LOGGER.warn("Schematic action failed during {}: graph={}, path={}, detail={}",
                    phase, handle.graphId(), execution.request.relativePath(), detail);
        } finally {
            if (handle.isActive()) handle.resume(StandardPorts.FLOW_OUT.getId());
        }
    }

    @Override
    public void completeExternalWait(BlueprintExecutionHandle handle, String outputPortName, Completion completion) {
        pending.remove(handle);
    }

    @Override
    public void endExternalWait(BlueprintExecutionHandle handle, @Nullable String reason) {
        PendingExecution execution = pending.remove(handle);
        if (execution != null) execution.cancel();
    }

    @Override
    public String interruptionOutputPort(BlueprintExternalWaitRequest request) {
        return request instanceof SchematicExecutionRequest ? StandardPorts.FLOW_OUT.getId() : null;
    }

    @Override
    public void levelUnloaded(ServerLevel level) {
        readyQueues.remove(level);
        reservedPlacementKeys.remove(level);
        pending.forEach((handle, execution) -> {
            if (execution.level == level && pending.remove(handle, execution)) execution.cancel();
        });
    }

    @Override
    public void shutdown(MinecraftServer server) {
        readyQueues.keySet().removeIf(level -> level.getServer() == server);
        reservedPlacementKeys.keySet().removeIf(level -> level.getServer() == server);
        pending.forEach((handle, execution) -> {
            if (execution.server == server && pending.remove(handle, execution)) execution.cancel();
        });
        BoundedIoExecutor executor = executors.remove(server);
        if (executor != null) executor.close();
    }

    @Nullable
    private static Throwable rootCause(@Nullable Throwable error) {
        Throwable result = error;
        while (result != null && result.getCause() != null && result.getCause() != result) {
            result = result.getCause();
        }
        return result;
    }

    private static final class PendingExecution {
        private final MinecraftServer server;
        private final ServerLevel level;
        private final SchematicExecutionRequest request;
        private final CompletableFuture<?> loadFuture;
        private final AtomicBoolean cancelled;
        private volatile SchematicExecutionRequest.TickTask task;

        private PendingExecution(MinecraftServer server, ServerLevel level, SchematicExecutionRequest request,
                                 CompletableFuture<?> loadFuture, AtomicBoolean cancelled) {
            this.server = server;
            this.level = level;
            this.request = request;
            this.loadFuture = loadFuture;
            this.cancelled = cancelled;
        }

        private void cancel() {
            cancelled.set(true);
            loadFuture.cancel(false);
            SchematicExecutionRequest.TickTask currentTask = task;
            if (currentTask != null) {
                try {
                    currentTask.cancel();
                } catch (RuntimeException exception) {
                    GeometryNode.LOGGER.error("Failed to finalize a cancelled schematic action", exception);
                }
            }
        }
    }

    public final class PlacementKeyReservation implements AutoCloseable {
        private final ServerLevel level;
        private final String key;
        private final boolean reserved;
        private boolean closed;

        private PlacementKeyReservation(ServerLevel level, String key, boolean reserved) {
            this.level = level;
            this.key = key;
            this.reserved = reserved;
        }

        public String key() {
            return key;
        }

        @Override
        public void close() {
            if (closed || !reserved) return;
            closed = true;
            Set<String> reservations = reservedPlacementKeys.get(level);
            if (reservations == null) return;
            reservations.remove(key);
            if (reservations.isEmpty()) reservedPlacementKeys.remove(level, reservations);
        }
    }
}
