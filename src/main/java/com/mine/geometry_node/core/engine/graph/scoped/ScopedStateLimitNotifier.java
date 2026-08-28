package com.mine.geometry_node.core.engine.graph.scoped;

import com.mine.geometry_node.GeometryNode;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/** Emits one server-wide capacity warning per affected bucket and cooldown window. */
final class ScopedStateLimitNotifier {
    private static final long NOTIFICATION_COOLDOWN_MILLIS = 60_000L;
    private static final Map<MinecraftServer, Map<String, Long>> LAST_NOTIFICATIONS =
            new WeakHashMap<>();

    private ScopedStateLimitNotifier() {
    }

    static void notifyLimit(ServerLevel level, ScopedStateNamespace namespace,
                            ScopedStateScope scope, String identity, int limit) {
        MinecraftServer server = level.getServer();
        String bucket = namespace.serializedName() + "/" + scope + "/" + identity;
        long now = System.currentTimeMillis();
        synchronized (LAST_NOTIFICATIONS) {
            Map<String, Long> notifications = LAST_NOTIFICATIONS.computeIfAbsent(
                    server, ignored -> new HashMap<>());
            Long previous = notifications.get(bucket);
            if (previous != null && now - previous < NOTIFICATION_COOLDOWN_MILLIS) return;
            notifications.put(bucket, now);
        }

        GeometryNode.LOGGER.warn(
                "[GeometryNode] Scoped-state bucket {}/{}/{} reached its {} entry limit; new keys are rejected.",
                namespace.serializedName(), scope, identity, limit);
        Component message = Component.translatable(
                "geometry_node.scoped_state.limit_reached",
                Component.translatable(namespace.translationKey()),
                scope.name(), identity, limit);
        server.getPlayerList().getPlayers().forEach(player -> player.sendSystemMessage(message));
    }
}
