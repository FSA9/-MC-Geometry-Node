package com.mine.geometry_node.core.engine.security;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

/** Shared authorization for privileged world editing, independent of asset repositories. */
public final class WorldEditPermissions {
    private WorldEditPermissions() {}

    public static boolean canManageWorld(ServerPlayer player) {
        return player != null && player.permissions().hasPermission(Permissions.COMMANDS_OWNER);
    }
}
