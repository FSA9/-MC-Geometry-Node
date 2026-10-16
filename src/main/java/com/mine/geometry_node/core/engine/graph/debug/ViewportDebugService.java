package com.mine.geometry_node.core.engine.graph.debug;

import com.mine.geometry_node.core.engine.system.viewport.EditCameraChunkService;
import com.mine.geometry_node.core.network.NetworkHandler;
import com.mine.geometry_node.core.network.packet.c2s.PacketViewportDebugSubscription;
import com.mine.geometry_node.core.network.packet.s2c.PacketViewportDebugSnapshot;
import com.mine.geometry_node.core.network.packet.s2c.PacketGeometryDebugSnapshot;
import dev.architectury.networking.NetworkManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import java.util.*;

/** Leased editor-only diagnostics. Player chat subscriptions remain independent. */
public final class ViewportDebugService {
    private static final Map<ServerPlayer,Lease> LEASES = new IdentityHashMap<>();
    private static boolean initialized;
    private ViewportDebugService() {}
    public static void registerNetwork() {
        if (initialized) return;
        initialized=true;
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, PacketViewportDebugSubscription.TYPE,
                PacketViewportDebugSubscription.STREAM_CODEC, (packet,context) -> context.queue(() -> {
                    if (context.getPlayer() instanceof ServerPlayer player) receive(player,packet);
                }));
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> tick(event.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> LEASES.keySet().removeIf(p -> p.level().getServer()==event.getServer()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) remove(player);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) remove(player);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                LEASES.keySet().removeIf(p -> p.getUUID().equals(player.getUUID()));
                DebugRendererSessionManager.viewportSubscriptionsChanged(player.level().getServer());
            }
        });
    }
    private static void receive(ServerPlayer player, PacketViewportDebugSubscription packet) {
        if (!packet.dimension().equals(player.level().dimension().identifier())) return;
        LEASES.keySet().removeIf(previous -> previous != player && previous.getUUID().equals(player.getUUID()));
        if (packet.mask()==0 || !allowed(player)) {
            remove(player);
            NetworkHandler.sendToPlayer(player,new PacketViewportDebugSnapshot(packet.dimension(),Map.of()));
            return;
        }
        Lease lease=LEASES.computeIfAbsent(player,p -> new Lease(packet.dimension()));
        boolean hydrate = (lease.mask & ViewportDebugLayer.SCHEMATIC.bit()) == 0
                && (packet.mask() & ViewportDebugLayer.SCHEMATIC.bit()) != 0;
        if (lease.mask!=packet.mask()) lease.signature=Long.MIN_VALUE;
        lease.mask=packet.mask();
        DebugRendererSessionManager.viewportSubscriptionsChanged(player.level().getServer());
        lease.heartbeat=player.level().getServer().getTickCount();
        DebugRendererSessionManager.hydrateViewport(player, hydrate);
    }
    private static void remove(ServerPlayer player) {
        LEASES.remove(player);
        DebugRendererSessionManager.viewportSubscriptionsChanged(player.level().getServer());
    }
    private static boolean allowed(ServerPlayer player) {
        return player.isAlive(); // Same read access as the existing debug commands; writes use scene permissions.
    }
    static boolean wants(MinecraftServer server, ViewportDebugLayer layer) {
        return LEASES.entrySet().stream().anyMatch(e -> e.getKey().level().getServer()==server && (e.getValue().mask & layer.bit())!=0);
    }
    private static void tick(MinecraftServer server) {
        int now=server.getTickCount();
        for (var entry : List.copyOf(LEASES.entrySet())) {
            ServerPlayer player=entry.getKey(); Lease lease=entry.getValue();
            if (player.level().getServer()!=server) continue;
            if (now-lease.heartbeat>60 || !allowed(player) || !lease.dimension.equals(player.level().dimension().identifier())) {
                remove(player);
                if (server.getPlayerList().getPlayer(player.getUUID()) == player)
                    NetworkHandler.sendToPlayer(player,new PacketViewportDebugSnapshot(player.level().dimension().identifier(),Map.of()));
                continue;
            }
            if (Math.floorMod(now,5)!=0) continue;
            var origins=EditCameraChunkService.positions(player);
            var layers=origins.isEmpty() ? Map.<ViewportDebugLayer,PacketGeometryDebugSnapshot>of()
                : DebugRendererSessionManager.collectViewport(player,origins,lease.mask,lease.observations,
                    lease.nodeCollision,lease.modelCollision);
            long signature=signature(layers);
            if (signature!=lease.signature) {
                NetworkHandler.sendToPlayer(player,new PacketViewportDebugSnapshot(lease.dimension,layers));
                lease.signature=signature;
            }
        }
    }
    private static long signature(Map<ViewportDebugLayer,PacketGeometryDebugSnapshot> layers) {
        long result=1;
        for (var layer:ViewportDebugLayer.values()) {
            var data=layers.get(layer); if (data==null) continue;
            result=31*result+layer.id;
            for(var mesh:data.meshes()) {
                result=31*result+Objects.hash(mesh.id(),mesh.graphId(),mesh.geometryType(),mesh.color(),mesh.showPoints(),
                    mesh.centerX(),mesh.centerY(),mesh.centerZ(),mesh.sizeX(),mesh.sizeY(),mesh.sizeZ(),
                    mesh.rotationX(),mesh.rotationY(),mesh.rotationZ(),mesh.anchorUuid(),mesh.anchorEntityId());
                result=31*result+Arrays.hashCode(mesh.vertices());
                result=31*result+Arrays.hashCode(mesh.edges()); result=31*result+Arrays.hashCode(mesh.faces());
            }
        }
        return result;
    }
    private static final class Lease {
        final Identifier dimension;
        final DebugLevelObservationCache observations=new DebugLevelObservationCache();
        final CollisionDebugProvider nodeCollision=new CollisionDebugProvider(true), modelCollision=new CollisionDebugProvider(false);
        int mask,heartbeat;
        long signature=Long.MIN_VALUE;
        Lease(Identifier dimension) { this.dimension=dimension; }
    }
}
