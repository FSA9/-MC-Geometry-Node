package com.mine.geometry_node.core.engine.system.faction.service;

import com.mine.geometry_node.core.engine.security.WorldEditPermissions;
import com.mine.geometry_node.core.network.NetworkHandler;
import com.mine.geometry_node.core.network.packet.faction.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.io.IOException;
import java.util.*;

/** Remote authority, bounded request rate and publication; faction rules live in the domain service. */
public final class ServerFactionRequests {
    private static final Map<ServerGamePacketListenerImpl, Session> SESSIONS = new IdentityHashMap<>();
    private static boolean initialized;
    private ServerFactionRequests() {}

    public static void init() {
        if (initialized) return;
        initialized = true;
        ServerFactionService.INSTANCE.init();
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) SESSIONS.remove(player.connection);
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) ->
                SESSIONS.keySet().removeIf(connection -> connection.getPlayer().level().getServer() == event.getServer()));
    }

    public static void receive(ServerPlayer original, PacketFactionRequest request) {
        MinecraftServer server = original.level().getServer();
        if (!server.isSameThread()) {
            server.schedule(server.wrapRunnable(() -> receive(original, request))); return;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(original.getUUID());
        if (player == null || player.connection != original.connection || player.hasDisconnected() || !server.isRunning()) return;
        if (!WorldEditPermissions.canManageWorld(player)) {
            SESSIONS.remove(player.connection);
            send(player, new PacketFactionResult(request.requestId(), false, "permission_denied", "")); return;
        }
        Session session = SESSIONS.computeIfAbsent(player.connection, ignored -> new Session());
        if (!session.acquire()) {
            send(player, new PacketFactionResult(request.requestId(), false, "rate_limited", "")); return;
        }
        PacketFactionResult reply;
        boolean publish = false;
        try {
            long before = ServerFactionService.INSTANCE.snapshot(server).revision();
            var catalog = ServerFactionService.INSTANCE.execute(server, request.expectedRevision(), request.operation(), request.values());
            String encoded = ServerFactionService.INSTANCE.encodedSnapshot(server);
            reply = new PacketFactionResult(request.requestId(), true, "", encoded);
            session.subscribed = true;
            publish = catalog.revision() != before;
        } catch (IllegalStateException conflict) {
            String message = Objects.toString(conflict.getMessage(), "");
            String reason = Set.of("conflict", "faction_unavailable", "rank_unavailable").contains(message)
                    ? message : "failed";
            reply = new PacketFactionResult(request.requestId(), false, reason, readable(server));
        } catch (IllegalArgumentException invalid) {
            String reason = "duplicate_name".equals(invalid.getMessage()) ? "duplicate_name" : "invalid";
            reply = new PacketFactionResult(request.requestId(), false, reason, readable(server));
        } catch (IOException failure) {
            log("Faction storage failed", failure);
            reply = new PacketFactionResult(request.requestId(), false, "storage", "");
        } catch (RuntimeException failure) {
            log("Faction request failed", failure);
            reply = new PacketFactionResult(request.requestId(), false, "failed", "");
        }
        send(player, reply);
        if (publish) publish(server, player, reply.catalogJson());
    }

    private static void publish(MinecraftServer server, ServerPlayer author, String json) {
        PacketFactionResult update = new PacketFactionResult(0, true, "", json);
        SESSIONS.forEach((connection, session) -> {
            ServerPlayer viewer = connection.getPlayer();
            if (viewer == author || !session.subscribed || viewer.level().getServer() != server) return;
            if (viewer.hasDisconnected() || server.getPlayerList().getPlayer(viewer.getUUID()) != viewer
                    || !WorldEditPermissions.canManageWorld(viewer)) { session.subscribed = false; return; }
            send(viewer, update);
        });
    }

    /** Metadata changes from nodes use the same subscribed catalogue stream as authored changes. */
    public static void metadataChanged(MinecraftServer server) {
        try { publish(server, null, ServerFactionService.INSTANCE.encodedSnapshot(server)); }
        catch (IOException | RuntimeException failure) { log("Faction metadata publication failed", failure); }
    }

    private static String readable(MinecraftServer server) {
        try { return ServerFactionService.INSTANCE.encodedSnapshot(server); }
        catch (IOException | RuntimeException unavailable) { return ""; }
    }
    private static void send(ServerPlayer player, PacketFactionResult result) {
        try { NetworkHandler.sendToPlayer(player, result); }
        catch (RuntimeException failure) { log("Faction response delivery failed", failure); }
    }
    private static void log(String message, Exception error) {
        System.getLogger(ServerFactionRequests.class.getName()).log(System.Logger.Level.WARNING, message, error);
    }
    private static final class Session {
        private double tokens = 16;
        private long last = System.nanoTime();
        private boolean subscribed;
        boolean acquire() {
            long now = System.nanoTime();
            tokens = Math.min(16, tokens + Math.max(0, now - last) / 1_000_000_000.0 * 16); last = now;
            if (tokens < 1) return false;
            tokens--; return true;
        }
    }
}
