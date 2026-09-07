package com.mine.geometry_node.client.asset.remote;

import com.mine.geometry_node.client.asset.ClientAssetCallbacks;
import com.mine.geometry_node.client.network.request.ClientRequestTracker;
import com.mine.geometry_node.core.network.packet.asset.repository.PacketRemoteAssetCapabilitiesResponse;
import com.mine.geometry_node.core.network.packet.asset.repository.PacketRemoteAssetFileOperationResponse;
import com.mine.geometry_node.core.network.packet.asset.repository.PacketRemoteAssetListResponse;

import java.util.function.Consumer;

public final class RemoteAssetClient {
    private static final ClientRequestTracker.Group REQUESTS =
            ClientRequestTracker.group("remote-asset", ClientAssetCallbacks::execute);

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

    public static void onCapabilities(int requestId, Consumer<PacketRemoteAssetCapabilitiesResponse> callback,
                                      Runnable onTimeout) {
        REQUESTS.register(requestId, PacketRemoteAssetCapabilitiesResponse.class, callback, onTimeout);
    }

    public static void onList(int requestId, Consumer<PacketRemoteAssetListResponse> callback, Runnable onTimeout) {
        REQUESTS.register(requestId, PacketRemoteAssetListResponse.class, callback, onTimeout);
    }

    public static void onFileOperation(int requestId, Consumer<PacketRemoteAssetFileOperationResponse> callback,
                                       Runnable onTimeout) {
        REQUESTS.register(requestId, PacketRemoteAssetFileOperationResponse.class, callback, onTimeout);
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

}
