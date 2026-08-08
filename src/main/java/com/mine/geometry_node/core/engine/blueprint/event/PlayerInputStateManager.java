package com.mine.geometry_node.core.engine.blueprint.event;

import com.mine.geometry_node.api.EventPayload;
import com.mine.geometry_node.api.GeometryNodeEvents;
import com.mine.geometry_node.core.network.packet.c2s.PacketPlayerInput;
import com.mine.geometry_node.core.node.nodes.events.player.OnPlayerKeyEvent;
import com.mine.geometry_node.core.node.port.StandardPorts;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Server-authoritative player input state and blueprint-event dispatcher. */
public final class PlayerInputStateManager {
    private static final Set<String> VALID_KEY_IDS = Set.of(OnPlayerKeyEvent.VALID_KEYS);
    private static final int MAX_TRANSITIONS_PER_TICK = VALID_KEY_IDS.size() * 2;

    private static final Map<UUID, Set<String>> ACTIVE_KEYS = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<String, Long>> PRESS_START_TICKS = new ConcurrentHashMap<>();
    private static final Map<UUID, TransitionBudget> TRANSITION_BUDGETS = new ConcurrentHashMap<>();

    private PlayerInputStateManager() {
    }

    public static void handleInput(ServerPlayer player, PacketPlayerInput payload) {
        if (player == null || payload == null || !VALID_KEY_IDS.contains(payload.keyId())) {
            return;
        }
        String action = payload.action();
        if (!"PRESS".equals(action) && !"RELEASE".equals(action)) {
            return;
        }

        UUID playerId = player.getUUID();
        String keyId = payload.keyId();
        long gameTime = player.level().getGameTime();
        Set<String> activeKeys = ACTIVE_KEYS.computeIfAbsent(playerId, ignored -> ConcurrentHashMap.newKeySet());
        boolean changesState = "PRESS".equals(action) ? !activeKeys.contains(keyId) : activeKeys.contains(keyId);
        if (!changesState || !tryConsumeTransition(playerId, gameTime)) {
            return;
        }

        Map<String, Long> pressStarts = PRESS_START_TICKS.computeIfAbsent(playerId, ignored -> new ConcurrentHashMap<>());
        int durationTicks = 0;
        if ("PRESS".equals(action)) {
            activeKeys.add(keyId);
            pressStarts.put(keyId, gameTime);
        } else {
            activeKeys.remove(keyId);
            Long pressStart = pressStarts.remove(keyId);
            if (pressStart != null) {
                durationTicks = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, gameTime - pressStart));
            }
        }

        Vec3 clientVelocity = payload.clientVelocity().isFinite()
                ? payload.clientVelocity()
                : player.getKnownMovement();
        GeometryNodeEvents.dispatch(
                (net.minecraft.server.level.ServerLevel) player.level(),
                player,
                OnPlayerKeyEvent.TYPE_ID,
                EventPayload.builder()
                        .put(StandardPorts.ENTITY.getId(), player)
                        .put(GraphEventFields.KEY_ID, keyId)
                        .put(GraphEventFields.ACTION, action)
                        .put(GraphEventFields.DURATION, durationTicks)
                        .put(GraphEventFields.CLIENT_VELOCITY, clientVelocity)
                        .put(GraphEventFields.CLIENT_VELOCITY_GAME_TIME, gameTime)
                        .put(StandardPorts.TIME.getId(), durationTicks)
                        .build()
        );
    }

    public static boolean isKeyPressed(UUID playerId, String keyId) {
        if (!VALID_KEY_IDS.contains(keyId)) return false;
        Set<String> keys = ACTIVE_KEYS.get(playerId);
        return keys != null && keys.contains(keyId);
    }

    public static void clearPlayer(UUID playerId) {
        ACTIVE_KEYS.remove(playerId);
        PRESS_START_TICKS.remove(playerId);
        TRANSITION_BUDGETS.remove(playerId);
    }

    private static boolean tryConsumeTransition(UUID playerId, long gameTime) {
        TransitionBudget budget = TRANSITION_BUDGETS.computeIfAbsent(playerId, ignored -> new TransitionBudget());
        return budget.tryConsume(gameTime);
    }

    private static final class TransitionBudget {
        private long gameTime = Long.MIN_VALUE;
        private int used;

        private synchronized boolean tryConsume(long currentGameTime) {
            if (gameTime != currentGameTime) {
                gameTime = currentGameTime;
                used = 0;
            }
            if (used >= MAX_TRANSITIONS_PER_TICK) {
                return false;
            }
            used++;
            return true;
        }
    }
}
