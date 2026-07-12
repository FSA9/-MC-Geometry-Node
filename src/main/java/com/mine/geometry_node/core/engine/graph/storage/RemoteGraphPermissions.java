package com.mine.geometry_node.core.engine.graph.storage;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

public final class RemoteGraphPermissions {
    private RemoteGraphPermissions() {
    }

    public static boolean canBrowseRemoteGraphs(ServerPlayer player) {
        return player != null;
    }

    public static boolean canUploadGraphs(ServerPlayer player) {
        return player != null;
    }

    public static boolean canCreateRemoteFolders(ServerPlayer player) {
        return canUploadGraphs(player);
    }

    public static boolean canDownloadGraphs(ServerPlayer player) {
        return player != null;
    }

    public static boolean canManageGraphs(ServerPlayer player) {
        return player != null && player.permissions().hasPermission(Permissions.COMMANDS_OWNER);
    }
}
