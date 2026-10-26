package com.mine.geometry_node.core.engine.system.entity.preview;

import com.mine.geometry_node.core.engine.system.entity.appearance.EntityAppearanceCapture;
import com.mine.geometry_node.core.engine.system.entity.appearance.EntityAppearanceSpec;
import com.mine.geometry_node.core.engine.system.entity.appearance.EntityAppearances;
import com.mine.geometry_node.core.engine.system.entity.preview.protocol.EntityPreviewPayload;
import com.mine.geometry_node.core.engine.system.entity.index.ServerEntityIndexService;
import com.mine.geometry_node.core.engine.system.entity.preview.protocol.EntityPreviewTarget;

import com.mine.geometry_node.core.engine.security.WorldEditPermissions;
import com.mine.geometry_node.core.network.NetworkHandler;
import com.mine.geometry_node.core.network.packet.entitypreview.*;
import com.mine.geometry_node.core.node.value.entity.EntityTemplateValue;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import java.util.*;

/** Immediate server tasks; registered unloaded entities use the directory's last safe appearance. */
public final class ServerEntityPreviewService {
    private static final Map<ServerGamePacketListenerImpl, EntityPreviewRateLimit> LIMITS = new IdentityHashMap<>();
    private static boolean initialized;
    private ServerEntityPreviewService() {}
    public static void init() {
        if (initialized) return;
        initialized = true;
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) LIMITS.remove(player.connection);
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) ->
                LIMITS.keySet().removeIf(connection -> connection.getPlayer().level().getServer() == event.getServer()));
    }
    public static void receive(ServerPlayer original, PacketEntityPreviewRequest request) {
        MinecraftServer server = original.level().getServer();
        if (!server.isSameThread()) throw new IllegalStateException("Entity previews require the server thread");
        ServerPlayer player = server.getPlayerList().getPlayer(original.getUUID());
        if (player == null || player.connection != original.connection || player.hasDisconnected() || !server.isRunning()) return;
        boolean allowed = LIMITS.computeIfAbsent(player.connection, ignored -> new EntityPreviewRateLimit()).acquire(System.nanoTime());
        String failure = !allowed ? "rate_limited" : !WorldEditPermissions.canManageWorld(player) ? "permission_denied" : "";
        List<PacketEntityPreviewResponse.Entry> entries = new ArrayList<>(request.targets().size());
        for (EntityPreviewTarget target : request.targets()) entries.add(failure.isEmpty() ? resolve(server, target) : unavailable(target, failure));
        NetworkHandler.sendToPlayer(player, new PacketEntityPreviewResponse(request.requestId(), entries));
    }
    private static PacketEntityPreviewResponse.Entry resolve(MinecraftServer server, EntityPreviewTarget target) {
        Identifier dimension = Identifier.tryParse(target.dimension());
        if (dimension == null) return unavailable(target, "invalid_dimension");
        try {
            // UUID lookup across currently loaded levels handles dimension transfers without loading chunks.
            Entity entity = ServerEntityIndexService.INSTANCE.findLoaded(server, target.entityId()).orElse(null);
            if (entity == null) {
                var record = ServerEntityIndexService.INSTANCE.record(server, target.entityId()).orElse(null);
                if (record == null) return unavailable(target, "entity_unavailable");
                if (Set.of("minecraft:player", "minecraft:interaction", "minecraft:mannequin").contains(record.entry().type()))
                    return unavailable(target, "unsupported_type");
                var payload = new EntityPreviewPayload(record.appearance(), record.appearanceSpec());
                return payload.isEmpty() ? unavailable(target, "appearance_unavailable")
                        : new PacketEntityPreviewResponse.Entry(target, payload, "");
            }
            if (entity instanceof net.minecraft.world.entity.player.Player || entity instanceof net.minecraft.world.entity.Interaction
                    || entity.getType() == net.minecraft.world.entity.EntityType.MANNEQUIN)
                return unavailable(target, "unsupported_type");
            EntityAppearanceSpec spec = EntityAppearances.get(entity);
            String unsupported = EntityAppearanceCapture.unsupportedReason(entity);
            if (!unsupported.isEmpty() && spec.isNone()) return unavailable(target, unsupported);
            EntityTemplateValue template = EntityTemplateValue.EMPTY;
            // A capture failure must not suppress an independent replacement description.
            try { template = EntityAppearanceCapture.template(entity); }
            catch (RuntimeException failure) { if (spec.isNone()) return unavailable(target, "appearance_unavailable"); }
            return new PacketEntityPreviewResponse.Entry(target, new EntityPreviewPayload(template, spec), "");
        } catch (RuntimeException failure) {
            return unavailable(target, "appearance_unavailable");
        }
    }
    private static PacketEntityPreviewResponse.Entry unavailable(EntityPreviewTarget target, String message) {
        return new PacketEntityPreviewResponse.Entry(target, EntityTemplateValue.EMPTY, message);
    }
}
