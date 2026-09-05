package com.mine.geometry_node.core.engine.system.data.library;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.core.engine.blueprint.runtime.wait.BlueprintExternalWaitHandler;
import com.mine.geometry_node.core.engine.blueprint.runtime.wait.BlueprintExternalWaitRequest;
import com.mine.geometry_node.core.engine.blueprint.runtime.wait.BlueprintExecutionHandle;
import com.mine.geometry_node.core.engine.runtime.ServerEngine;
import com.mine.geometry_node.core.engine.system.asset.transfer.io.AssetTransferIoExecutor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Serializes Blueprint-originated Data Library writes away from the server tick thread. */
public final class DataLibraryWriteRuntime implements ServerEngine, BlueprintExternalWaitHandler {
    public static final String ID = "geometry_node:data_library_write";
    public static final String SUCCESS_PORT = "flow_out";
    public static final String FAILURE_PORT = "failed";
    public static final DataLibraryWriteRuntime INSTANCE = new DataLibraryWriteRuntime();

    private static final int QUEUE_CAPACITY = 256;

    private final Map<MinecraftServer, AssetTransferIoExecutor> executors = new ConcurrentHashMap<>();
    private final Map<BlueprintExecutionHandle, PendingWrite> pendingWrites = new ConcurrentHashMap<>();

    private DataLibraryWriteRuntime() {
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
    public boolean beginExternalWait(BlueprintExecutionHandle handle, BlueprintExternalWaitRequest request) {
        if (!(request instanceof DataLibraryWriteRequest writeRequest)) {
            return false;
        }
        ServerLevel level = handle.level();
        if (level == null) {
            return false;
        }

        MinecraftServer server = level.getServer();
        AssetTransferIoExecutor executor = executors.computeIfAbsent(server,
                ignored -> new AssetTransferIoExecutor("GeometryNode-DataLibrary-Graph-IO", 1, QUEUE_CAPACITY));
        AtomicBoolean cancelled = new AtomicBoolean();
        CompletableFuture<DataLibraryEntry> future = executor.submit(() -> {
            if (cancelled.get()) {
                throw new CancellationException("Blueprint Data Library write was cancelled before execution");
            }
            return RemoteDataLibraryService.INSTANCE.upsert(
                    server,
                    writeRequest.path(),
                    writeRequest.type(),
                    writeRequest.key(),
                    writeRequest.value()
            );
        });
        PendingWrite pending = new PendingWrite(server, future, cancelled);
        PendingWrite replaced = pendingWrites.put(handle, pending);
        if (replaced != null) {
            replaced.cancel();
        }
        future.whenComplete((ignored, error) -> completeAsync(handle, pending, error));
        return true;
    }

    private void completeAsync(BlueprintExecutionHandle handle, PendingWrite pending, @Nullable Throwable error) {
        if (!pendingWrites.remove(handle, pending)) {
            return;
        }
        pending.server().execute(() -> {
            if (!handle.isActive()) {
                return;
            }
            if (error != null) {
                GeometryNode.LOGGER.warn("Blueprint Data Library write failed: graph={}",
                        handle.graphId(), rootCause(error));
            }
            handle.resume(error == null ? SUCCESS_PORT : FAILURE_PORT);
        });
    }

    @Override
    public void completeExternalWait(BlueprintExecutionHandle handle, String outputPortName, Completion completion) {
        pendingWrites.remove(handle);
    }

    @Override
    public void endExternalWait(BlueprintExecutionHandle handle, @Nullable String reason) {
        PendingWrite pending = pendingWrites.remove(handle);
        if (pending != null) {
            pending.cancel();
        }
    }

    @Override
    public void shutdown(MinecraftServer server) {
        pendingWrites.forEach((handle, pending) -> {
            if (pending.server() == server && pendingWrites.remove(handle, pending)) {
                pending.cancel();
            }
        });
        AssetTransferIoExecutor executor = executors.remove(server);
        if (executor != null) {
            executor.close();
        }
    }

    private static Throwable rootCause(Throwable error) {
        Throwable result = error;
        while (result.getCause() != null && result.getCause() != result) {
            result = result.getCause();
        }
        return result;
    }

    private record PendingWrite(
            MinecraftServer server,
            CompletableFuture<?> future,
            AtomicBoolean cancelled
    ) {
        private void cancel() {
            cancelled.set(true);
            future.cancel(false);
        }
    }
}
