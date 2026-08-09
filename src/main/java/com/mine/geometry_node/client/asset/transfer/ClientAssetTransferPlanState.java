package com.mine.geometry_node.client.asset.transfer;

import com.mine.geometry_node.core.network.NetworkHandler;
import com.mine.geometry_node.core.network.packet.asset.AssetTransferPlanKind;
import com.mine.geometry_node.core.network.packet.asset.PacketAssetTransferPlanRequest;
import com.mine.geometry_node.core.network.packet.asset.PacketAssetTransferPlanResponse;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public final class ClientAssetTransferPlanState {
    private static final AtomicInteger REQUEST_IDS = new AtomicInteger(1);
    private static final Map<Integer, PendingRequest> REQUESTS = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService TIMEOUT_EXECUTOR =
            Executors.newSingleThreadScheduledExecutor(task -> {
                Thread thread = new Thread(task, "GeometryNode-AssetTransfer-PlanTimeout");
                thread.setDaemon(true);
                return thread;
            });

    private ClientAssetTransferPlanState() {
    }

    public static int request(
            AssetTransferPlanKind kind,
            List<String> paths,
            Consumer<PacketAssetTransferPlanResponse> callback
    ) {
        int requestId = REQUEST_IDS.getAndIncrement();
        PendingRequest pending = new PendingRequest(callback);
        REQUESTS.put(requestId, pending);
        pending.timeout = TIMEOUT_EXECUTOR.schedule(() -> REQUESTS.remove(requestId, pending), 30L, TimeUnit.SECONDS);
        NetworkHandler.sendToServer(new PacketAssetTransferPlanRequest(requestId, kind, paths));
        return requestId;
    }

    public static void handle(PacketAssetTransferPlanResponse response) {
        PendingRequest pending = REQUESTS.remove(response.requestId());
        if (pending == null) return;
        pending.cancelTimeout();
        pending.callback.accept(response);
    }

    public static void cancel(int requestId) {
        PendingRequest pending = REQUESTS.remove(requestId);
        if (pending != null) pending.cancelTimeout();
    }

    public static void reset() {
        for (PendingRequest pending : REQUESTS.values()) pending.cancelTimeout();
        REQUESTS.clear();
    }

    private static final class PendingRequest {
        private final Consumer<PacketAssetTransferPlanResponse> callback;
        private volatile ScheduledFuture<?> timeout;

        private PendingRequest(Consumer<PacketAssetTransferPlanResponse> callback) {
            this.callback = callback != null ? callback : ignored -> { };
        }

        private void cancelTimeout() {
            ScheduledFuture<?> current = timeout;
            if (current != null) current.cancel(false);
        }
    }
}
