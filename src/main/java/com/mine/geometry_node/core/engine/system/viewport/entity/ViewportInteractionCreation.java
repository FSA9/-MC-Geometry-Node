package com.mine.geometry_node.core.engine.system.viewport.entity;

import com.mine.geometry_node.core.engine.system.asset.RemoteAssetPermissions;
import com.mine.geometry_node.core.engine.system.entity.InteractionEntityFactory;
import com.mine.geometry_node.core.engine.system.viewport.EditCameraChunkService;
import com.mine.geometry_node.core.network.packet.viewport.PacketViewportInteractionCreate;
import dev.architectury.networking.NetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import java.util.*;

/** Server-thread native entity writes; no parallel scene object or client-only entity. */
public final class ViewportInteractionCreation {
    private static final Map<ServerPlayer, Requests> REQUESTS = new IdentityHashMap<>();
    private ViewportInteractionCreation() {}

    public static void init() {
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, PacketViewportInteractionCreate.TYPE,
                PacketViewportInteractionCreate.STREAM_CODEC, (packet, context) -> context.queue(() -> {
                    if (context.getPlayer() instanceof ServerPlayer player) receive(player, packet);
                }));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> REQUESTS.remove(event.getEntity()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent event) ->
                REQUESTS.keySet().removeIf(player -> player.getUUID().equals(event.getEntity().getUUID())));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) ->
                REQUESTS.keySet().removeIf(player -> player.level().getServer() == event.getServer()));
    }
    private static void receive(ServerPlayer player, PacketViewportInteractionCreate packet) {
        if (!player.isAlive() || !packet.dimension().equals(player.level().dimension().identifier())) return;
        if (!RemoteAssetPermissions.canManageAssets(player)) { failure(player, "permission"); return; }
        Requests requests = REQUESTS.computeIfAbsent(player, ignored -> new Requests());
        if (!requests.seen.add(packet.requestId())) return;
        while (requests.seen.size() > 32) requests.seen.removeFirst();
        long now = player.level().getGameTime();
        if (now - requests.lastTick < 4) { failure(player, "rate_limit"); return; }
        requests.lastTick = now;
        BlockPos block = BlockPos.containing(packet.position());
        boolean nearby = EditCameraChunkService.positions(player).stream()
                .anyMatch(camera -> camera.distanceToSqr(packet.position()) <= 4096.0 * 4096.0);
        if (!nearby || !player.level().hasChunkAt(block) || !player.level().getWorldBorder().isWithinBounds(block)) {
            failure(player, "unavailable"); return;
        }
        if (InteractionEntityFactory.spawn(player.level(), packet.position(), 1, 1, false) == null)
            failure(player, "unavailable");
    }
    private static void failure(ServerPlayer player, String reason) {
        player.sendSystemMessage(Component.translatable("geometry_node.game_view.interaction.create." + reason));
    }
    private static final class Requests {
        final LinkedHashSet<UUID> seen = new LinkedHashSet<>();
        long lastTick = Long.MIN_VALUE / 2;
    }
}
