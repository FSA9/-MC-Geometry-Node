package com.mine.geometry_node.core.network;

import com.mine.geometry_node.client.render.ClientVisualManager;
import com.mine.geometry_node.core.execution.storage.DynamicGraphManager;
import com.mine.geometry_node.core.execution.storage.GraphResourceManager;
import com.mine.geometry_node.core.network.packet.PacketSpawnVisual;
import com.mine.geometry_node.core.network.packet.c2s.PacketRequestDownload;
import com.mine.geometry_node.core.network.packet.c2s.PacketRequestFileList;
import com.mine.geometry_node.core.network.packet.c2s.PacketSyncUpload;
import com.mine.geometry_node.core.network.packet.s2c.PacketSendFileList;
import com.mine.geometry_node.core.network.packet.s2c.PacketSyncDownload;
import com.mine.geometry_node.core.network.packet.s2c.PacketSyncResponse;
import dev.architectury.networking.NetworkManager;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

public class NetworkHandler {

    public static void init() {
        // [现有的] 注册 S2C: 视觉效果包
        NetworkManager.registerReceiver(
                NetworkManager.Side.S2C,
                PacketSpawnVisual.TYPE,
                PacketSpawnVisual.STREAM_CODEC,
                (payload, context) -> {
                    context.queue(() -> ClientVisualManager.spawnEffectFromPacket(payload));
                }
        );

        // ==========================================
        // 1. 注册 C2S: 客户端上传蓝图 -> 服务端接收
        // ==========================================
        NetworkManager.registerReceiver(
                NetworkManager.Side.C2S,
                PacketSyncUpload.TYPE,
                PacketSyncUpload.STREAM_CODEC,
                (payload, context) -> {
                    context.queue(() -> {
                        if (context.getPlayer() instanceof ServerPlayer player) {
                            MinecraftServer server = player.getServer();
                            String graphId = payload.graphId();
                            String jsonContent = payload.jsonContent();

                            try {
                                DynamicGraphManager.saveAndHotReload(server, graphId, jsonContent);
                                sendToPlayer(player, new PacketSyncResponse(true, graphId, "上传并热更新成功！"));
                            } catch (Exception e) {
                                sendToPlayer(player, new PacketSyncResponse(false, graphId, "上传失败: " + e.getMessage()));
                            }
                        }
                    });
                }
        );

        // ==========================================
        // 2. 注册 S2C: 服务端回执 -> 客户端接收
        // ==========================================
        NetworkManager.registerReceiver(
                NetworkManager.Side.S2C,
                PacketSyncResponse.TYPE,
                PacketSyncResponse.STREAM_CODEC,
                (payload, context) -> {
                    context.queue(() -> {
                        if (context.getPlayer() != null) {
                            String prefix = payload.success() ? "§a[图纸同步成功]§r " : "§c[图纸同步失败]§r ";
                            context.getPlayer().displayClientMessage(
                                    Component.literal(prefix + payload.graphId() + " - " + payload.message()),
                                    false
                            );
                        }
                    });
                }
        );

        // ==========================================
        // 6. 注册 S2C: 服务端下发图纸内容 -> 客户端接收
        // ==========================================
        NetworkManager.registerReceiver(
                NetworkManager.Side.S2C,
                PacketSyncDownload.TYPE,
                PacketSyncDownload.STREAM_CODEC,
                (payload, context) -> {
                    context.queue(() -> {
                        if (context.getPlayer() != null) {
                            String graphId = payload.graphId();
                            String jsonContent = payload.jsonContent();

                            // 调用客户端的本地草稿管理器，把服务端发来的数据直接保存到 C盘
                            com.mine.geometry_node.client.ui.persistence.LocalDraftManager.saveDraft(graphId, jsonContent);

                            // 弹出提示
                            context.getPlayer().displayClientMessage(
                                    Component.literal("§a[☁ 云端下载成功]§r 图纸 " + graphId + " 已保存到你的本地草稿箱！"),
                                    false
                            );
                        }
                    });
                }
        );
    }

    // ==========================================
    // 发包 API 工具方法
    // ==========================================
    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        NetworkManager.sendToPlayer(player, payload);
    }

    public static void sendToPlayers(Iterable<ServerPlayer> players, CustomPacketPayload payload) {
        NetworkManager.sendToPlayers(players, payload);
    }

    public static void sendToServer(CustomPacketPayload payload) {
        NetworkManager.sendToServer(payload);
    }
}