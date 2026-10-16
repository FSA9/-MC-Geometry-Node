package com.mine.geometry_node.core.engine.system.viewport;

import com.mine.geometry_node.core.network.NetworkHandler;
import com.mine.geometry_node.core.network.packet.c2s.PacketEditCamera;
import com.mine.geometry_node.core.network.packet.s2c.PacketEditCameraView;
import com.mine.geometry_node.mixin.viewport.CameraChunkMapAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.*;
import java.util.function.Supplier;

/** Server-thread leases for a second observer, without moving or spawning any player. */
public final class EditCameraChunkService {
    private static final DeferredRegister<TicketType> TYPES = DeferredRegister.create(Registries.TICKET_TYPE, "geometry_node");
    private static final Supplier<TicketType> LOAD = TYPES.register("edit_camera_load",
            () -> new TicketType(80, TicketType.FLAG_LOADING | TicketType.FLAG_CAN_EXPIRE_IF_UNLOADED));
    private static final Supplier<TicketType> SIMULATE = TYPES.register("edit_camera_simulate",
            () -> new TicketType(80, TicketType.FLAG_SIMULATION | TicketType.FLAG_CAN_EXPIRE_IF_UNLOADED));
    private static final Map<ServerPlayer, Session> SESSIONS = new IdentityHashMap<>();
    private static final Map<MinecraftServer, Set<Lease>> LEASES = new IdentityHashMap<>();

    private EditCameraChunkService() {}

    public static void register(IEventBus bus) {
        TYPES.register(bus);
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> tick(event.getServer()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                SESSIONS.remove(player);
                reconcile(player.level().getServer());
            }
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> {
            SESSIONS.entrySet().removeIf(e -> e.getValue().level.getServer() == event.getServer());
            LEASES.remove(event.getServer());
        });
    }

    public static void receive(ServerPlayer player, PacketEditCamera packet) {
        if (!packet.dimension().equals(player.level().dimension().identifier())) return;
        if (!packet.active()) { release(player); return; }
        if (!packet.validPosition() || !player.isAlive()) return;
        Session session = SESSIONS.get(player);
        if (session == null || session.level != player.level()) {
            session = new Session(player.level());
            SESSIONS.put(player, session);
        }
        // Coalesce requests; tickets and tracking change at most once per four ticks.
        session.requested = new Vec3(packet.x(), packet.y(), packet.z());
        session.heartbeat = player.level().getServer().getTickCount();
    }

    public static Vec3 position(ServerPlayer player) {
        Session session = SESSIONS.get(player);
        return session != null && session.level == player.level() ? session.position : null;
    }

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % 4 != 0) return;
        List<ServerPlayer> expired = new ArrayList<>();
        SESSIONS.forEach((player, session) -> {
            if (session.level.getServer() != server) return;
            if (player.hasDisconnected() || !player.isAlive() || session.level != player.level()
                    || server.getTickCount() - session.heartbeat > 60) {
                expired.add(player);
            } else {
                session.position = session.requested;
                ((CameraChunkMapAccess) session.level.getChunkSource().chunkMap).geometryNode$updateTracking(player);
                // Also refresh stationary entity tracking as the observer moves or
                // a chunk batch becomes ready; player position itself never changes.
                session.level.getChunkSource().chunkMap.move(player);
            }
        });
        for (ServerPlayer player : expired) release(player);
        reconcile(server);
    }

    private static void release(ServerPlayer player) {
        Session previous = SESSIONS.remove(player);
        if (previous == null) return;
        if (!player.hasDisconnected()) {
            ((CameraChunkMapAccess) player.level().getChunkSource().chunkMap).geometryNode$updateTracking(player);
            NetworkHandler.sendToPlayer(player, new PacketEditCameraView(previous.level.dimension().identifier(), false, 0, 0, 0));
        }
        reconcile(previous.level.getServer());
    }

    private static void reconcile(MinecraftServer server) {
        Set<Lease> desired = new HashSet<>();
        SESSIONS.forEach((player, session) -> {
            if (session.level.getServer() != server || session.position == null || session.level != player.level()) return;
            int radius = ((CameraChunkMapAccess) session.level.getChunkSource().chunkMap).geometryNode$viewDistance(player);
            int simulationRadius = Math.min(radius, server.getPlayerList().getSimulationDistance());
            ChunkPos center = new ChunkPos((int) Math.floor(session.position.x / 16), (int) Math.floor(session.position.z / 16));
            var region = new ChunkTrackingView.Positioned(center, radius);
            region.forEach(pos -> {
                // Level 32 supplies vanilla's ready-to-send chunks without a blocking getChunk call.
                desired.add(new Lease(session.level, pos, false));
                if (ChunkTrackingView.isInViewDistance(center.x(), center.z(), simulationRadius, pos.x(), pos.z())) {
                    desired.add(new Lease(session.level, pos, true));
                }
            });
        });
        Set<Lease> previous = LEASES.getOrDefault(server, Set.of());
        for (Lease lease : previous) if (!desired.contains(lease)) lease.remove();
        for (Lease lease : desired) if (!previous.contains(lease) || server.getTickCount() % 20 == 0) lease.refresh();
        if (desired.isEmpty()) LEASES.remove(server);
        else LEASES.put(server, desired);
    }

    private static final class Session {
        final ServerLevel level;
        Vec3 requested;
        Vec3 position;
        int heartbeat;
        Session(ServerLevel level) { this.level = level; }
    }

    /** Set union provides reference counting when multiple users watch the same chunks. */
    private record Lease(ServerLevel level, ChunkPos pos, boolean simulate) {
        TicketType type() { return simulate ? SIMULATE.get() : LOAD.get(); }
        int radius() { return simulate ? 2 : 1; }
        void refresh() { level.getChunkSource().addTicketWithRadius(type(), pos, radius()); }
        void remove() { level.getChunkSource().removeTicketWithRadius(type(), pos, radius()); }
    }
}
