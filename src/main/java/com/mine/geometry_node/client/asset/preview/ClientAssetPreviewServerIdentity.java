package com.mine.geometry_node.client.asset.preview;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;
import java.util.Locale;

/** Stable cache namespace for the currently connected multiplayer server or integrated world. */
public final class ClientAssetPreviewServerIdentity {
    private ClientAssetPreviewServerIdentity() {
    }

    public static String current() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.hasSingleplayerServer() && minecraft.getSingleplayerServer() != null) {
            Path world = minecraft.getSingleplayerServer().getWorldPath(LevelResource.ROOT)
                    .toAbsolutePath().normalize();
            return "integrated:" + world;
        }
        ServerData server = minecraft.getCurrentServer();
        if (server != null && server.ip != null && !server.ip.isBlank()) {
            return "multiplayer:" + server.ip.trim().toLowerCase(Locale.ROOT);
        }
        throw new IllegalStateException("No active server identity is available");
    }
}
