package com.mine.geometry_node.core.network;

import com.mine.geometry_node.core.network.packet.PacketSpawnVisual;
import dev.architectury.networking.NetworkManager;
import net.minecraft.server.level.ServerPlayer;

public class NetworkHandler {

    /**
     * 在模组的主类 (Mod 主类的构造函数或 init 阶段) 中调用此方法进行注册
     */
    public static void init() {

        // ==========================================
        // 注册 S2C (Server to Client) 数据包
        // ==========================================
        NetworkManager.registerReceiver(
                NetworkManager.Side.S2C,          // 这是一个发往客户端的包
                PacketSpawnVisual.TYPE,           // 包的唯一标识
                PacketSpawnVisual.STREAM_CODEC,   // 我们刚才手写的编解码器
                (payload, context) -> {
                    context.queue(() -> {
                        // 根据 effectType 决定怎么处理
                        if ("debug_line".equals(payload.effectType())) {
                            // 调用我们刚写的渲染管理器，把线加进去！
                            com.mine.geometry_node.client.render.ClientVisualManager.addDebugLine(
                                    payload.startPos(),
                                    payload.endPos(),
                                    payload.color(),
                                    payload.durationTicks()
                            );
                        }
                    });
                }
        );

        // 未来如果需要 C2S (客户端发给服务端) 的包，也在这里继续注册...
    }

    // ==========================================
    // 发包辅助工具 API (供 ExecutionContext 和 Node 调用)
    // ==========================================

    /**
     * 向单个玩家发送视觉包
     */
    public static void sendToPlayer(ServerPlayer player, PacketSpawnVisual payload) {
        NetworkManager.sendToPlayer(player, payload);
    }

    /**
     * 向特定范围或集合内的多个玩家广播视觉包
     */
    public static void sendToPlayers(Iterable<ServerPlayer> players, PacketSpawnVisual payload) {
        NetworkManager.sendToPlayers(players, payload);
    }
}