package com.mine.geometry_node.core.engine.system.asset;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

public final class RemoteAssetPermissions {
    private RemoteAssetPermissions() {
    }

    public static boolean canBrowseRemoteAssets(ServerPlayer player) {
        return player != null;
    }

    public static boolean canUploadAssets(ServerPlayer player) {
        return player != null;
    }

    public static boolean canCreateRemoteFolders(ServerPlayer player) {
        return canUploadAssets(player);
    }

    public static boolean canDownloadAssets(ServerPlayer player) {
        return player != null;
    }

    public static boolean canManageAssets(ServerPlayer player) {
        return player != null && player.permissions().hasPermission(Permissions.COMMANDS_OWNER);
    }
}
