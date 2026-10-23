package com.mine.geometry_node.core.engine.system.entitypreview;

import com.mine.geometry_node.core.engine.system.entitypreview.appearance.EntityAppearanceCapture;
import com.mine.geometry_node.core.engine.system.entitypreview.protocol.EntityPreviewTarget;

import com.mine.geometry_node.core.engine.system.asset.repository.RemoteAssetPermissions;
import com.mine.geometry_node.core.network.NetworkHandler;
import com.mine.geometry_node.core.network.packet.entitypreview.*;
import com.mine.geometry_node.core.node.value.entity.EntityTemplateValue;
import net.minecraft.core.registries.*;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import java.util.*;

/** Immediate server tasks keep working in a paused integrated server; no tick callbacks or entity caches. */
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
        String failure = !allowed ? "rate_limited" : !RemoteAssetPermissions.canManageAssets(player) ? "permission_denied" : "";
        List<PacketEntityPreviewResponse.Entry> entries = new ArrayList<>(request.targets().size());
        for (EntityPreviewTarget target : request.targets()) entries.add(failure.isEmpty() ? resolve(server, target) : unavailable(target, failure));
        NetworkHandler.sendToPlayer(player, new PacketEntityPreviewResponse(request.requestId(), entries));
    }
    private static PacketEntityPreviewResponse.Entry resolve(MinecraftServer server, EntityPreviewTarget target) {
        Identifier dimension = Identifier.tryParse(target.dimension());
        if (dimension == null) return unavailable(target, "invalid_dimension");
        var level = server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
        if (level == null) return unavailable(target, "dimension_unavailable");
        // ServerLevel.getEntity(UUID) is a loaded-entity index lookup. It cannot load a chunk.
        Entity entity = level.getEntity(target.entityId());
        if (entity == null || entity.isRemoved()) return unavailable(target, "entity_unavailable");
        if (entity instanceof Player) return unavailable(target, "unsupported_player");
        if (entity instanceof Interaction) return unavailable(target, "no_appearance");
        if (!entity.getType().canSerialize() || !entity.getType().canSummon()) return unavailable(target, "unsupported_type");
        var id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        // Unknown mod serializers cannot safely be invoked using the vanilla appearance schema.
        if (id == null || !id.getNamespace().equals("minecraft")
                || !entity.getClass().getName().startsWith("net.minecraft.world.entity.")
                || id.getPath().equals("mannequin")) return unavailable(target, "unsupported_type");
        try {
            var template = new EntityTemplateValue(id.toString(), EntityAppearanceCapture.capture(entity));
            return new PacketEntityPreviewResponse.Entry(target, template, "");
        } catch (RuntimeException failure) {
            return unavailable(target, "appearance_unavailable");
        }
    }
    private static PacketEntityPreviewResponse.Entry unavailable(EntityPreviewTarget target, String message) {
        return new PacketEntityPreviewResponse.Entry(target, EntityTemplateValue.EMPTY, message);
    }
}
