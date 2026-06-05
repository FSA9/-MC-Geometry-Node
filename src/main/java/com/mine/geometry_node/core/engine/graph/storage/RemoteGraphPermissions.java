package com.mine.geometry_node.core.engine.graph.storage;

import net.minecraft.server.level.ServerPlayer;

public final class RemoteGraphPermissions {
    private static final int BROWSE_LEVEL = 1;
    private static final int DOWNLOAD_LEVEL = 2;
    private static final int UPLOAD_LEVEL = 3;
    private static final int MANAGE_LEVEL = 4;

    private RemoteGraphPermissions() {
    }

    public static boolean canBrowseRemoteGraphs(ServerPlayer player) {
        return player != null && player.hasPermissions(BROWSE_LEVEL);
    }

    public static boolean canUploadGraphs(ServerPlayer player) {
        return player != null && player.hasPermissions(UPLOAD_LEVEL);
    }

    public static boolean canCreateRemoteFolders(ServerPlayer player) {
        return canUploadGraphs(player);
    }

    public static boolean canDownloadGraphs(ServerPlayer player) {
        return player != null && player.hasPermissions(DOWNLOAD_LEVEL);
    }

    public static boolean canManageGraphs(ServerPlayer player) {
        return player != null && player.hasPermissions(MANAGE_LEVEL);
    }
}
