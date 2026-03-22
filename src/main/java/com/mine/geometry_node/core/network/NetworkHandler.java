package com.mine.geometry_node.core.network;

import com.mine.geometry_node.client.render.ClientVisualManager;
import com.mine.geometry_node.core.network.packet.PacketSpawnVisual;
import dev.architectury.networking.NetworkManager;
import net.minecraft.server.level.ServerPlayer;

public class NetworkHandler {

    public static void init() {
        NetworkManager.registerReceiver(
                NetworkManager.Side.S2C,
                PacketSpawnVisual.TYPE,
                PacketSpawnVisual.STREAM_CODEC,
                (payload, context) -> {
                    context.queue(() -> {
                        ClientVisualManager.spawnEffectFromPacket(payload);
                    });
                }
        );
    }

    // API

    public static void sendToPlayer(ServerPlayer player, PacketSpawnVisual payload) {
        NetworkManager.sendToPlayer(player, payload);
    }

    public static void sendToPlayers(Iterable<ServerPlayer> players, PacketSpawnVisual payload) {
        NetworkManager.sendToPlayers(players, payload);
    }
}