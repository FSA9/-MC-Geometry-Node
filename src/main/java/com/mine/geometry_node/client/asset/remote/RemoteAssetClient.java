package com.mine.geometry_node.client.asset.remote;

import com.mine.geometry_node.client.network.request.ClientRequestTracker;
import com.mine.geometry_node.core.engine.system.asset.AssetTypeCatalog;
import com.mine.geometry_node.core.engine.system.asset.AssetDescriptor;
import com.mine.geometry_node.core.network.packet.asset.repository.PacketRemoteAssetCapabilitiesResponse;
import com.mine.geometry_node.core.network.packet.asset.repository.PacketRemoteAssetFileOperationResponse;
import com.mine.geometry_node.core.network.packet.asset.repository.PacketRemoteAssetListResponse;

import java.util.function.Consumer;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class RemoteAssetClient {
    private static final ClientRequestTracker.Group REQUESTS =
            ClientRequestTracker.group("remote-asset");

    private static volatile boolean canBrowse;
    private static volatile boolean canUpload;
    private static volatile boolean canDownload;
    private static volatile boolean canManage;
    private static final Set<String> KNOWN_GRAPH_PATHS = ConcurrentHashMap.newKeySet();

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

    public static List<String> knownGraphPaths() {
        return KNOWN_GRAPH_PATHS.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    public static void onCapabilities(int requestId, Consumer<PacketRemoteAssetCapabilitiesResponse> callback) {
        REQUESTS.register(requestId, PacketRemoteAssetCapabilitiesResponse.class, callback);
    }

    public static void onList(int requestId, Consumer<PacketRemoteAssetListResponse> callback) {
        REQUESTS.register(requestId, PacketRemoteAssetListResponse.class, callback);
    }

    public static void onFileOperation(int requestId, Consumer<PacketRemoteAssetFileOperationResponse> callback) {
        REQUESTS.register(requestId, PacketRemoteAssetFileOperationResponse.class, callback);
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
        KNOWN_GRAPH_PATHS.clear();
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
        REQUESTS.complete(response.requestId(), response, RemoteAssetClient::updateKnownGraphPaths);
    }

    public static void handle(PacketRemoteAssetFileOperationResponse response) {
        REQUESTS.complete(response.requestId(), response);
    }

    private static void updateKnownGraphPaths(PacketRemoteAssetListResponse response) {
        if (!response.success()) return;
        String directory = normalizeDirectory(response.directory());
        KNOWN_GRAPH_PATHS.removeIf(path -> parentDirectory(path).equals(directory));
        for (AssetDescriptor entry : response.entries()) {
            if (!entry.directory() && AssetTypeCatalog.GRAPH_TYPE_ID.equals(entry.metadata().typeId())) {
                KNOWN_GRAPH_PATHS.add(entry.path());
            }
        }
    }

    private static String parentDirectory(String path) {
        String normalized = path == null ? "" : path.replace('\\', '/');
        int separator = normalized.lastIndexOf('/');
        return separator < 0 ? "" : normalized.substring(0, separator);
    }

    private static String normalizeDirectory(String path) {
        String normalized = path == null ? "" : path.replace('\\', '/');
        while (normalized.startsWith("/")) normalized = normalized.substring(1);
        while (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
        return normalized;
    }
}
