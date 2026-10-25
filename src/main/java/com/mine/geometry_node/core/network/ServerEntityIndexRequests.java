package com.mine.geometry_node.core.network;

import com.mine.geometry_node.core.engine.system.asset.repository.RemoteAssetPermissions;
import com.mine.geometry_node.core.engine.system.entityindex.ServerEntityIndexService;
import com.mine.geometry_node.core.network.packet.entityindex.PacketEntityIndexRequest;
import com.mine.geometry_node.core.network.packet.entityindex.PacketEntityIndexResult;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/** Connection-bound registration commands. All lookups and mutations run on the server task loop. */
public final class ServerEntityIndexRequests {
    private static final Map<ServerGamePacketListenerImpl, Long> LAST_REQUEST = new IdentityHashMap<>();
    private static boolean initialized;
    private ServerEntityIndexRequests() {}
    public static void init() {
        if (initialized) return;
        initialized = true;
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) LAST_REQUEST.remove(player.connection);
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> LAST_REQUEST.keySet().removeIf(
                connection -> connection.getPlayer().level().getServer() == event.getServer()));
    }
    public static void receive(ServerPlayer original, PacketEntityIndexRequest request) {
        var server = original.level().getServer();
        if (!server.isSameThread()) throw new IllegalStateException("Entity registration requires the server thread");
        var player = server.getPlayerList().getPlayer(original.getUUID());
        if (player == null || player.connection != original.connection || player.hasDisconnected() || !server.isRunning()) return;
        boolean registered = false;
        long revision = 0;
        String message = "";
        try {
            if (!RemoteAssetPermissions.canManageAssets(player)) message = "permission_denied";
            else {
                long now = System.nanoTime();
                Long previous = LAST_REQUEST.get(player.connection);
                if (previous != null && now - previous < 100_000_000L) message = "rate_limited";
                else {
                    LAST_REQUEST.put(player.connection, now);
                    var index = ServerEntityIndexService.INSTANCE;
                    // The dimension is a validated hint only: migration preserves the UUID and cancellation
                    // must work even after the original dimension or live entity has disappeared.
                    if (request.edit()) {
                        var result = index.setRegistered(server, request.entity(), request.registered(), request.expectedRevision());
                        revision = result.revision();
                    } else {
                        revision = index.record(server, request.entity()).map(record -> record.revision()).orElse(0L);
                    }
                    registered = revision > 0;
                }
            }
        } catch (IllegalArgumentException invalid) { message = reason(invalid, "invalid"); }
        catch (IllegalStateException rejected) { message = reason(rejected, "unavailable"); }
        catch (RuntimeException failure) {
            System.getLogger(ServerEntityIndexRequests.class.getName()).log(System.Logger.Level.WARNING, "Entity registration failed", failure);
            message = "failed";
        }
        NetworkHandler.sendToPlayer(player, new PacketEntityIndexResult(request.requestId(), registered, revision, message));
    }
    private static String reason(RuntimeException failure, String fallback) {
        String reason = failure.getMessage();
        return reason != null && Set.of("conflict", "entity_index_full", "entity_unavailable", "unsupported_entity",
                "unavailable", "invalid", "storage_unavailable", "permission_denied").contains(reason) ? reason : fallback;
    }
}
