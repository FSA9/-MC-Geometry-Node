package com.mine.geometry_node.core.engine.blueprint.event;

import com.mine.geometry_node.api.EventPayload;
import com.mine.geometry_node.api.GeometryNodeEvents;
import com.mine.geometry_node.core.node.nodes.events.player.OnPlayerKeyEvent;
import com.mine.geometry_node.core.node.port.StandardPorts;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.HashMap;
import java.util.HashSet;

/** Server-authoritative player input state and blueprint-event dispatcher. */
public final class PlayerInputStateManager {
    private static final Set<String> VALID_KEY_IDS = Set.of(OnPlayerKeyEvent.VALID_KEYS);
    private static final int MAX_TRANSITIONS_PER_TICK = VALID_KEY_IDS.size() * 2;

    private final Map<MinecraftServer, ServerState> servers = new WeakHashMap<>();

    public PlayerInputStateManager() {
    }

    public void handleInput(ServerPlayer player, String keyId, String action, Vec3 clientVelocity) {
        if (player == null || !VALID_KEY_IDS.contains(keyId)) {
            return;
        }
        if (!"PRESS".equals(action) && !"RELEASE".equals(action)) {
            return;
        }

        UUID playerId = player.getUUID();
        ServerState state = servers.computeIfAbsent(player.level().getServer(), ignored -> new ServerState());
        long gameTime = player.level().getGameTime();
        Set<String> activeKeys = state.activeKeys.computeIfAbsent(playerId, ignored -> new HashSet<>());
        boolean changesState = "PRESS".equals(action) ? !activeKeys.contains(keyId) : activeKeys.contains(keyId);
        if (!changesState || !tryConsumeTransition(state, playerId, gameTime)) {
            return;
        }

        Map<String, Long> pressStarts = state.pressStartTicks.computeIfAbsent(playerId, ignored -> new HashMap<>());
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

        clientVelocity = clientVelocity != null && clientVelocity.isFinite()
                ? clientVelocity
                : player.getKnownMovement();
        GeometryNodeEvents.dispatch(
                (net.minecraft.server.level.ServerLevel) player.level(),
                player,
                OnPlayerKeyEvent.TYPE_ID,
                EventPayload.builder()
                        .put(StandardPorts.ENTITY.getId(), player)
                        .put(GraphEventFields.KEY_ID, keyId)
                        .put(GraphEventFields.ACTION, action)
                        .put(GraphEventFields.CLIENT_VELOCITY, clientVelocity)
                        .put(GraphEventFields.CLIENT_VELOCITY_GAME_TIME, gameTime)
                        .put(StandardPorts.TICK.getId(), durationTicks)
                        .build()
        );
    }

    public boolean isKeyPressed(Entity player, String keyId) {
        if (player == null || !(player.level() instanceof net.minecraft.server.level.ServerLevel level)
                || !VALID_KEY_IDS.contains(keyId)) return false;
        ServerState state = servers.get(level.getServer());
        Set<String> keys = state != null ? state.activeKeys.get(player.getUUID()) : null;
        return keys != null && keys.contains(keyId);
    }

    public void clearPlayer(Entity player) {
        ServerState state = player != null && player.level() instanceof net.minecraft.server.level.ServerLevel level
                ? servers.get(level.getServer()) : null;
        if (state == null) return;
        UUID playerId = player.getUUID();
        state.activeKeys.remove(playerId);
        state.pressStartTicks.remove(playerId);
        state.transitionBudgets.remove(playerId);
    }

    private boolean tryConsumeTransition(ServerState state, UUID playerId, long gameTime) {
        TransitionBudget budget = state.transitionBudgets.computeIfAbsent(playerId, ignored -> new TransitionBudget());
        return budget.tryConsume(gameTime);
    }

    public void shutdown(MinecraftServer server) {
        servers.remove(server);
    }

    private static final class ServerState {
        private final Map<UUID, Set<String>> activeKeys = new HashMap<>();
        private final Map<UUID, Map<String, Long>> pressStartTicks = new HashMap<>();
        private final Map<UUID, TransitionBudget> transitionBudgets = new HashMap<>();
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
