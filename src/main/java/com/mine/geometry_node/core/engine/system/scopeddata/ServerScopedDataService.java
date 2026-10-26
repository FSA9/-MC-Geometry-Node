package com.mine.geometry_node.core.engine.system.scopeddata;

import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateNamespace;
import com.mine.geometry_node.core.engine.metadata.ScopedMetadataTargets;
import com.mine.geometry_node.core.engine.system.asset.repository.RemoteAssetPermissions;
import com.mine.geometry_node.core.metadata.DataAccess;
import com.mine.geometry_node.core.network.NetworkHandler;
import com.mine.geometry_node.core.network.packet.scopeddata.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import java.util.*;

/** On-demand access to the current save; resolves only live levels, never entities or chunks. */
public final class ServerScopedDataService {
    private static final ScopedDataConnections<ServerGamePacketListenerImpl> CONNECTIONS = new ScopedDataConnections<>();
    private static boolean initialized;
    private ServerScopedDataService() {}
    public static void init() {
        if (initialized) return;
        initialized = true;
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) CONNECTIONS.remove(player.connection);
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> CONNECTIONS.removeIf(
                connection -> connection.getPlayer().level().getServer() == event.getServer()));
    }
    public static void receive(ServerPlayer original, PacketScopedDataRequest request) {
        MinecraftServer server = original.level().getServer();
        if (!server.isSameThread()) throw new IllegalStateException("Scoped data requires the server thread");
        ServerPlayer player = server.getPlayerList().getPlayer(original.getUUID());
        if (player == null || player.connection != original.connection || player.hasDisconnected() || !server.isRunning()) return;
        PacketScopedDataResult result;
        try {
            // Limit failures as well as successful reads/edits, independently for every connection.
            if (!CONNECTIONS.acquire(player.connection)) result = PacketScopedDataResult.error(request, "failed");
            else result = ScopedDataOperations.execute(request, new ScopedDataOperations.Access() {
                public boolean permitted() { return RemoteAssetPermissions.canManageAssets(player); }
                public Iterable<String> dimensions() {
                    // Lazy mapping lets Operations enforce its scan bound before enumerating the whole server.
                    return () -> new Iterator<>() {
                        private final Iterator<ServerLevel> levels = server.getAllLevels().iterator();
                        public boolean hasNext() { return levels.hasNext(); }
                        public String next() { return levels.next().dimension().identifier().toString(); }
                    };
                }
                public DataAccess data(String target) {
                    if (PacketScopedDataRequest.GLOBAL.equals(target))
                        return ScopedMetadataTargets.global(server.overworld(), ScopedStateNamespace.PUBLIC);
                    var key = ResourceKey.create(Registries.DIMENSION, Identifier.parse(target));
                    ServerLevel level = server.getLevel(key);
                    if (level == null) throw new IllegalStateException("Dimension is unavailable");
                    return ScopedMetadataTargets.world(level, target, ScopedStateNamespace.PUBLIC);
                }
            }, server.registryAccess());
        } catch (RuntimeException failure) {
            System.getLogger(ServerScopedDataService.class.getName()).log(System.Logger.Level.WARNING, "Scoped data request failed", failure);
            result = PacketScopedDataResult.error(request, "failed");
        }
        NetworkHandler.sendToPlayer(player, result);
    }
}
