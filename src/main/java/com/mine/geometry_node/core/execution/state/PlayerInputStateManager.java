package com.mine.geometry_node.core.execution.state;

import com.mine.geometry_node.api.EventPayload;
import com.mine.geometry_node.api.GeometryNodeEvents;
import com.mine.geometry_node.core.network.packet.c2s.PacketPlayerInput;
import com.mine.geometry_node.core.node.nodes.events.player.OnPlayerKeyEvent;
import com.mine.geometry_node.core.node.port.StandardPorts;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * [服务端状态管家] 记录玩家的按键状态，并派发蓝图事件
 */
public class PlayerInputStateManager {

    // 内存字典：UUID -> 当前按下的所有 KeyId 集合
    private static final Map<UUID, Set<String>> ACTIVE_KEYS = new ConcurrentHashMap<>();

    public static void handleInput(ServerPlayer player, PacketPlayerInput payload) {
        UUID uuid = player.getUUID();
        // 使用并发集合防止多线程修改引发 ConcurrentModificationException
        Set<String> keys = ACTIVE_KEYS.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet());

        String action = payload.action();
        String keyId = payload.keyId();

        // 1. 维护状态字典
        if ("PRESS".equals(action)) {
            keys.add(keyId);
        } else if ("RELEASE".equals(action)) {
            keys.remove(keyId);
        }
        // 注意：DOUBLE_CLICK 是瞬间动作，不改变按压状态字典

        // 2. 唤醒并派发蓝图事件
        GeometryNodeEvents.dispatch(
                (net.minecraft.server.level.ServerLevel) player.level(),
                player,
                OnPlayerKeyEvent.TYPE_ID,
                EventPayload.builder()
                        .put(StandardPorts.ENTITY.getId(), player)
                        .put("key_id", keyId)
                        .put("action", action)
                        .put("duration", payload.durationMs() / 1000.0f)
                        .build()
        );
    }

    /**
     * 供 IsKeyPressed 数据节点瞬间查询使用的 API
     */
    public static boolean isKeyPressed(UUID uuid, String keyId) {
        Set<String> keys = ACTIVE_KEYS.get(uuid);
        return keys != null && keys.contains(keyId);
    }

    /**
     * 清理玩家数据，防止内存泄漏
     */
    public static void clearPlayer(UUID uuid) {
        ACTIVE_KEYS.remove(uuid);
    }
}
