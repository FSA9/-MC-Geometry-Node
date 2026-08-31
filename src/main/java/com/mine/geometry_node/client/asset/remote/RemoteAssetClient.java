package com.mine.geometry_node.client.asset.remote;

import com.mine.geometry_node.core.network.packet.s2c.PacketRemoteAssetCapabilitiesResponse;
import com.mine.geometry_node.core.network.packet.s2c.PacketRemoteAssetFileOperationResponse;
import com.mine.geometry_node.core.network.packet.s2c.PacketRemoteAssetListResponse;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public final class RemoteAssetClient {
    private static final long REQUEST_TIMEOUT_SECONDS = 30L;
    private static final AtomicInteger REQUEST_IDS = new AtomicInteger(1);
    private static final ScheduledExecutorService TIMEOUT_EXECUTOR =
            Executors.newSingleThreadScheduledExecutor(task -> {
                Thread thread = new Thread(task, "GeometryNode-RemoteAsset-Timeout");
                thread.setDaemon(true);
                return thread;
            });

    private static final ConcurrentMap<Integer, PendingRequest<PacketRemoteAssetCapabilitiesResponse>> CAPABILITY_CALLBACKS =
            new ConcurrentHashMap<>();
    private static final ConcurrentMap<Integer, PendingRequest<PacketRemoteAssetListResponse>> LIST_CALLBACKS =
            new ConcurrentHashMap<>();
    private static final ConcurrentMap<Integer, PendingRequest<PacketRemoteAssetFileOperationResponse>> FILE_OPERATION_CALLBACKS =
            new ConcurrentHashMap<>();

    private static volatile boolean canBrowse;
    private static volatile boolean canUpload;
    private static volatile boolean canDownload;
    private static volatile boolean canManage;
    private static volatile List<String> clipboardPaths = List.of();
    private static volatile boolean cutOperation;

    private RemoteAssetClient() {
    }

    public static int nextRequestId() {
        return REQUEST_IDS.getAndIncrement();
    }

    public static boolean canBrowse() {
        return canBrowse;
    }

    public static boolean canUpload() {
        return canUpload;
    }

    public static boolean canDownload() {
        return canDownload;
    }

    public static boolean canManage() {
        return canManage;
    }

    public static List<String> clipboardPaths() {
        return clipboardPaths;
    }

    public static boolean isCutOperation() {
        return cutOperation;
    }

    public static void setClipboard(List<String> paths, boolean cut) {
        clipboardPaths = paths == null ? List.of() : List.copyOf(paths);
        cutOperation = cut && !clipboardPaths.isEmpty();
    }

    public static void clearClipboard() {
        clipboardPaths = List.of();
        cutOperation = false;
    }

    public static void onCapabilities(int requestId, Consumer<PacketRemoteAssetCapabilitiesResponse> callback) {
        register(CAPABILITY_CALLBACKS, requestId, callback);
    }

    public static void onList(int requestId, Consumer<PacketRemoteAssetListResponse> callback) {
        register(LIST_CALLBACKS, requestId, callback);
    }

    public static void onFileOperation(int requestId, Consumer<PacketRemoteAssetFileOperationResponse> callback) {
        register(FILE_OPERATION_CALLBACKS, requestId, callback);
    }

    public static void cancel(int requestId) {
        cancel(CAPABILITY_CALLBACKS, requestId);
        cancel(LIST_CALLBACKS, requestId);
        cancel(FILE_OPERATION_CALLBACKS, requestId);
    }

    public static void reset() {
        clear(CAPABILITY_CALLBACKS);
        clear(LIST_CALLBACKS);
        clear(FILE_OPERATION_CALLBACKS);
        canBrowse = false;
        canUpload = false;
        canDownload = false;
        canManage = false;
        clearClipboard();
    }

    public static void handle(PacketRemoteAssetCapabilitiesResponse response) {
        canBrowse = response.canBrowse();
        canUpload = response.canUpload();
        canDownload = response.canDownload();
        canManage = response.canManage();
        dispatch(CAPABILITY_CALLBACKS, response.requestId(), response, true);
    }

    public static void handle(PacketRemoteAssetListResponse response) {
        dispatch(LIST_CALLBACKS, response.requestId(), response, true);
    }

    public static void handle(PacketRemoteAssetFileOperationResponse response) {
        dispatch(FILE_OPERATION_CALLBACKS, response.requestId(), response, true);
    }

    private static <T> void register(
            ConcurrentMap<Integer, PendingRequest<T>> requests,
            int requestId,
            Consumer<T> callback
    ) {
        cancel(requests, requestId);
        PendingRequest<T> pending = new PendingRequest<>(callback);
        requests.put(requestId, pending);
        pending.timeout = TIMEOUT_EXECUTOR.schedule(
                () -> requests.remove(requestId, pending),
                REQUEST_TIMEOUT_SECONDS,
                TimeUnit.SECONDS
        );
    }

    private static <T> void dispatch(
            ConcurrentMap<Integer, PendingRequest<T>> requests,
            int requestId,
            T response,
            boolean terminal
    ) {
        PendingRequest<T> pending = terminal ? requests.remove(requestId) : requests.get(requestId);
        if (pending == null) return;
        if (terminal) pending.cancelTimeout();
        pending.callback.accept(response);
    }

    private static <T> void cancel(ConcurrentMap<Integer, PendingRequest<T>> requests, int requestId) {
        PendingRequest<T> pending = requests.remove(requestId);
        if (pending != null) pending.cancelTimeout();
    }

    private static <T> void clear(Map<Integer, PendingRequest<T>> requests) {
        for (PendingRequest<T> pending : requests.values()) {
            pending.cancelTimeout();
        }
        requests.clear();
    }

    private static final class PendingRequest<T> {
        private final Consumer<T> callback;
        private volatile ScheduledFuture<?> timeout;

        private PendingRequest(Consumer<T> callback) {
            this.callback = callback != null ? callback : ignored -> {
            };
        }

        private void cancelTimeout() {
            ScheduledFuture<?> currentTimeout = timeout;
            if (currentTimeout != null) currentTimeout.cancel(false);
        }
    }
}
