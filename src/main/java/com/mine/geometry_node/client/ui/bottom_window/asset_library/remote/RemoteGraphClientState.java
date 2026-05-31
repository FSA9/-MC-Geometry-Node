package com.mine.geometry_node.client.ui.bottom_window.asset_library.remote;

import com.mine.geometry_node.core.network.packet.s2c.PacketRemoteGraphCapabilitiesResponse;
import com.mine.geometry_node.core.network.packet.s2c.PacketRemoteGraphDownloadResponse;
import com.mine.geometry_node.core.network.packet.s2c.PacketRemoteGraphFileOperationResponse;
import com.mine.geometry_node.core.network.packet.s2c.PacketRemoteGraphListResponse;
import com.mine.geometry_node.core.network.packet.s2c.PacketRemoteGraphUploadResponse;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public final class RemoteGraphClientState {
    private static final AtomicInteger REQUEST_IDS = new AtomicInteger(1);
    private static final Map<Integer, Consumer<PacketRemoteGraphCapabilitiesResponse>> CAPABILITY_CALLBACKS = new ConcurrentHashMap<>();
    private static final Map<Integer, Consumer<PacketRemoteGraphListResponse>> LIST_CALLBACKS = new ConcurrentHashMap<>();
    private static final Map<Integer, Consumer<PacketRemoteGraphUploadResponse>> UPLOAD_CALLBACKS = new ConcurrentHashMap<>();
    private static final Map<Integer, Consumer<PacketRemoteGraphDownloadResponse>> DOWNLOAD_CALLBACKS = new ConcurrentHashMap<>();
    private static final Map<Integer, Consumer<PacketRemoteGraphFileOperationResponse>> FILE_OPERATION_CALLBACKS = new ConcurrentHashMap<>();

    private static volatile boolean canBrowse;
    private static volatile boolean canUpload;
    private static volatile boolean canDownload;
    private static volatile boolean canManage;

    private RemoteGraphClientState() {
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

    public static void onCapabilities(int requestId, Consumer<PacketRemoteGraphCapabilitiesResponse> callback) {
        CAPABILITY_CALLBACKS.put(requestId, callback);
    }

    public static void onList(int requestId, Consumer<PacketRemoteGraphListResponse> callback) {
        LIST_CALLBACKS.put(requestId, callback);
    }

    public static void onUpload(int requestId, Consumer<PacketRemoteGraphUploadResponse> callback) {
        UPLOAD_CALLBACKS.put(requestId, callback);
    }

    public static void onDownload(int requestId, Consumer<PacketRemoteGraphDownloadResponse> callback) {
        DOWNLOAD_CALLBACKS.put(requestId, callback);
    }

    public static void onFileOperation(int requestId, Consumer<PacketRemoteGraphFileOperationResponse> callback) {
        FILE_OPERATION_CALLBACKS.put(requestId, callback);
    }

    public static void handle(PacketRemoteGraphCapabilitiesResponse response) {
        canBrowse = response.canBrowse();
        canUpload = response.canUpload();
        canDownload = response.canDownload();
        canManage = response.canManage();
        Consumer<PacketRemoteGraphCapabilitiesResponse> callback = CAPABILITY_CALLBACKS.remove(response.requestId());
        if (callback != null) callback.accept(response);
    }

    public static void handle(PacketRemoteGraphListResponse response) {
        Consumer<PacketRemoteGraphListResponse> callback = LIST_CALLBACKS.remove(response.requestId());
        if (callback != null) callback.accept(response);
    }

    public static void handle(PacketRemoteGraphUploadResponse response) {
        Consumer<PacketRemoteGraphUploadResponse> callback = UPLOAD_CALLBACKS.get(response.requestId());
        if (callback != null) callback.accept(response);
        if (response.preflight() || !response.success() || "上传完成".equals(response.message())) {
            UPLOAD_CALLBACKS.remove(response.requestId());
        }
    }

    public static void handle(PacketRemoteGraphDownloadResponse response) {
        Consumer<PacketRemoteGraphDownloadResponse> callback = DOWNLOAD_CALLBACKS.get(response.requestId());
        if (callback != null) callback.accept(response);
        if (!response.success() || "下载完成".equals(response.message())) {
            DOWNLOAD_CALLBACKS.remove(response.requestId());
        }
    }

    public static void handle(PacketRemoteGraphFileOperationResponse response) {
        Consumer<PacketRemoteGraphFileOperationResponse> callback = FILE_OPERATION_CALLBACKS.remove(response.requestId());
        if (callback != null) callback.accept(response);
    }
}
