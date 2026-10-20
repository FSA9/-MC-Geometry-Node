package com.mine.geometry_node.client.asset.remote;

import com.mine.geometry_node.client.asset.ClientAssetCallbacks;
import com.mine.geometry_node.client.network.request.ClientRequestTracker;
import com.mine.geometry_node.core.network.NetworkHandler;
import com.mine.geometry_node.core.network.packet.asset.repository.PacketRemoteAssetCapabilitiesRequest;
import com.mine.geometry_node.core.network.packet.asset.repository.PacketRemoteAssetCapabilitiesResponse;
import com.mine.geometry_node.core.network.packet.asset.repository.PacketRemoteAssetFileOperationRequest;
import com.mine.geometry_node.core.network.packet.asset.repository.PacketRemoteAssetFileOperationResponse;
import com.mine.geometry_node.core.network.packet.asset.repository.PacketRemoteAssetListRequest;
import com.mine.geometry_node.core.network.packet.asset.repository.PacketRemoteAssetListResponse;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public final class RemoteAssetClient {
    private static final ClientRequestTracker.Group REQUESTS =
            ClientRequestTracker.group("remote-asset", ClientAssetCallbacks::execute);
    private static final RequestSender SENDER =
            new RequestSender(REQUESTS, NetworkHandler::sendToServer, RemoteGraphPathIndex.INSTANCE);

    private static volatile boolean canBrowse;
    private static volatile boolean canUpload;
    private static volatile boolean canDownload;
    private static volatile boolean canManage;

    private RemoteAssetClient() {
    }

    public static int nextRequestId() {
        return REQUESTS.nextRequestId();
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

    /** Registers before sending; synchronous send failure cancels the registration and propagates. */
    public static void requestCapabilities(int requestId, Consumer<PacketRemoteAssetCapabilitiesResponse> callback,
                                           Runnable onTimeout) {
        SENDER.capabilities(requestId, callback, onTimeout);
    }

    public static void requestList(int requestId, String directory, boolean createIfMissing,
                                   Consumer<PacketRemoteAssetListResponse> callback, Runnable onTimeout) {
        SENDER.list(requestId, directory, createIfMissing, callback, onTimeout);
    }

    public static void requestFileOperation(int requestId, PacketRemoteAssetFileOperationRequest.Operation operation,
                                            List<String> paths, String destinationPath,
                                            Consumer<PacketRemoteAssetFileOperationResponse> callback,
                                            Runnable onTimeout) {
        SENDER.fileOperation(requestId, operation, paths, destinationPath, callback, onTimeout);
    }

    public static void cancel(int requestId) {
        REQUESTS.cancel(requestId);
    }

    public static void reset() {
        REQUESTS.reset();
        canBrowse = false;
        canUpload = false;
        canDownload = false;
        canManage = false;
        RemoteGraphPathIndex.INSTANCE.clear();
    }

    public static void handle(PacketRemoteAssetCapabilitiesResponse response) {
        REQUESTS.complete(response.requestId(), response, accepted -> {
            canBrowse = accepted.canBrowse();
            canUpload = accepted.canUpload();
            canDownload = accepted.canDownload();
            canManage = accepted.canManage();
        });
    }

    public static void handle(PacketRemoteAssetListResponse response) {
        REQUESTS.complete(response.requestId(), response, RemoteGraphPathIndex.INSTANCE::accept);
    }

    public static void handle(PacketRemoteAssetFileOperationResponse response) {
        REQUESTS.complete(response.requestId(), response);
    }

    /** Package-local transport seam; request correlation and callback dispatch stay in the tracker. */
    static final class RequestSender {
        private final ClientRequestTracker.Group requests;
        private final Consumer<CustomPacketPayload> transport;
        private final RemoteGraphPathIndex index;

        RequestSender(ClientRequestTracker.Group requests, Consumer<CustomPacketPayload> transport,
                      RemoteGraphPathIndex index) {
            this.requests = Objects.requireNonNull(requests, "requests");
            this.transport = Objects.requireNonNull(transport, "transport");
            this.index = Objects.requireNonNull(index, "index");
        }

        void capabilities(int requestId, Consumer<PacketRemoteAssetCapabilitiesResponse> callback, Runnable onTimeout) {
            var packet = new PacketRemoteAssetCapabilitiesRequest(requestId);
            requests.register(requestId, PacketRemoteAssetCapabilitiesResponse.class, callback, onTimeout);
            send(requestId, packet);
        }

        void list(int requestId, String directory, boolean createIfMissing,
                  Consumer<PacketRemoteAssetListResponse> callback, Runnable onTimeout) {
            var packet = new PacketRemoteAssetListRequest(requestId, directory, createIfMissing);
            requests.register(requestId, PacketRemoteAssetListResponse.class, callback, onTimeout);
            send(requestId, packet);
        }

        void fileOperation(int requestId, PacketRemoteAssetFileOperationRequest.Operation operation,
                           List<String> paths, String destinationPath,
                           Consumer<PacketRemoteAssetFileOperationResponse> callback, Runnable onTimeout) {
            // Construct/validate first; this packet owns an immutable snapshot of the affected paths.
            var packet = new PacketRemoteAssetFileOperationRequest(requestId, operation, destinationPath, paths);
            requests.register(requestId, PacketRemoteAssetFileOperationResponse.class, response -> {
                if (response.success()) {
                    index.invalidateMutation(packet.operation(), packet.paths(), packet.destinationPath());
                }
                if (callback != null) callback.accept(response);
            }, onTimeout);
            send(requestId, packet);
        }

        private void send(int requestId, CustomPacketPayload packet) {
            try {
                transport.accept(packet);
            } catch (RuntimeException failure) {
                requests.cancel(requestId);
                throw failure;
            }
        }
    }
}
