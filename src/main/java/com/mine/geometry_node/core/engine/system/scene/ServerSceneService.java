package com.mine.geometry_node.core.engine.system.scene;

import com.mine.geometry_node.core.engine.system.asset.RemoteAssetPermissions;
import com.mine.geometry_node.core.network.NetworkHandler;
import com.mine.geometry_node.core.network.packet.scene.PacketSceneEdit;
import com.mine.geometry_node.core.network.packet.scene.PacketSceneSnapshot;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import java.util.*;

/** The only authoritative writer for authored scene objects. Called exclusively on the server thread. */
public final class ServerSceneService {
    private static final Map<ServerPlayer, LinkedHashMap<UUID, Receipt>> RECEIPTS = new IdentityHashMap<>();
    private static boolean initialized;
    private ServerSceneService() {}
    public static void init() {
        if (initialized) return;
        initialized = true;
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) synchronize(player);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) { RECEIPTS.remove(player); synchronize(player); }
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                // Death replaces ServerPlayer while keeping the network connection and in-flight request IDs.
                LinkedHashMap<UUID, Receipt> retained = null;
                for (ServerPlayer previous : List.copyOf(RECEIPTS.keySet())) {
                    if (previous.getUUID().equals(player.getUUID())) retained = RECEIPTS.remove(previous);
                }
                if (retained != null) RECEIPTS.put(player, retained);
                synchronize(player);
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) RECEIPTS.remove(player);
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) ->
                RECEIPTS.keySet().removeIf(player -> player.level().getServer() == event.getServer()));
    }
    public static void synchronize(ServerPlayer player) {
        NetworkHandler.sendToPlayer(player, snapshot(player, PacketSceneSnapshot.BROADCAST, true, ""));
    }
    public static void receive(ServerPlayer player, PacketSceneEdit request) {
        if (!player.level().dimension().identifier().toString().equals(request.dimension())) {
            reply(player, request.requestId(), false, "Scene dimension changed"); return;
        }
        if (request.changes().isEmpty()) { reply(player, request.requestId(), true, ""); return; }
        LinkedHashMap<UUID, Receipt> receipts = RECEIPTS.computeIfAbsent(player, ignored -> new LinkedHashMap<>());
        Receipt receipt = receipts.get(request.requestId());
        if (receipt != null) {
            if (!receipt.request.equals(request)) reply(player, request.requestId(), false, "Request ID reused with different content");
            else NetworkHandler.sendToPlayer(player, receipt.response);
            return;
        }
        boolean success = false;
        String message = "";
        boolean changed = false;
        try {
            if (!RemoteAssetPermissions.canManageAssets(player)) throw new SecurityException("Scene editing requires world management permission");
            changed = SceneSavedData.get(player.level().getServer()).apply(request.dimension(), request.changes());
            success = true;
        } catch (IllegalArgumentException | IllegalStateException | SecurityException | ArithmeticException failure) {
            message = failure.getMessage() == null ? "Scene edit rejected" : failure.getMessage();
        }
        PacketSceneSnapshot response = snapshot(player, request.requestId(), success, message);
        receipts.put(request.requestId(), new Receipt(request, response));
        while (receipts.size() > 32) receipts.remove(receipts.keySet().iterator().next());
        NetworkHandler.sendToPlayer(player, response);
        if (changed) for (ServerPlayer other : player.level().players()) if (other != player) synchronize(other);
    }
    private static void reply(ServerPlayer player, UUID id, boolean success, String message) {
        NetworkHandler.sendToPlayer(player, snapshot(player, id, success, message));
    }
    private static PacketSceneSnapshot snapshot(ServerPlayer player, UUID id, boolean success, String message) {
        String dimension = player.level().dimension().identifier().toString();
        SceneDocument document = SceneSavedData.get(player.level().getServer()).document(dimension);
        return new PacketSceneSnapshot(id, dimension, success, message, RemoteAssetPermissions.canManageAssets(player),
                document.revision(), document.objects(), document.deletedVersions());
    }
    private record Receipt(PacketSceneEdit request, PacketSceneSnapshot response) {}
}
