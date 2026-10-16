package com.mine.geometry_node.core.engine.system.viewport;

import com.mine.geometry_node.core.network.NetworkHandler;
import com.mine.geometry_node.core.network.packet.c2s.PacketEditCamera;
import com.mine.geometry_node.core.network.packet.s2c.PacketEditCameraView;
import com.mine.geometry_node.mixin.viewport.CameraChunkMapAccess;
import com.mine.geometry_node.core.engine.system.viewport.CameraChunkLeasePlan.Lease;
import com.mine.geometry_node.core.engine.system.viewport.CameraChunkLeasePlan.Observer;
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

/** Server-thread leases for bounded additional observers, without moving or spawning players. */
public final class EditCameraChunkService {
    private static final DeferredRegister<TicketType> TYPES = DeferredRegister.create(Registries.TICKET_TYPE, "geometry_node");
    private static final Supplier<TicketType> LOAD = TYPES.register("edit_camera_load",
            () -> new TicketType(80, TicketType.FLAG_LOADING | TicketType.FLAG_CAN_EXPIRE_IF_UNLOADED));
    private static final Supplier<TicketType> SIMULATE = TYPES.register("edit_camera_simulate",
            () -> new TicketType(80, TicketType.FLAG_SIMULATION | TicketType.FLAG_CAN_EXPIRE_IF_UNLOADED));
    private static final Map<ServerPlayer, Session> SESSIONS = new IdentityHashMap<>();
    private static final Map<MinecraftServer, CameraChunkLeasePlan> LEASES = new IdentityHashMap<>();

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
        if (packet.positions().isEmpty()) { release(player); return; }
        if (!packet.validPositions() || !player.isAlive()) return;
        Session session = SESSIONS.get(player);
        if (session == null || session.level != player.level()) {
            session = new Session(player.level());
            SESSIONS.put(player, session);
        }
        // Coalesce requests; tickets and tracking change at most once per four ticks.
        session.requested = packet.positions();
        session.heartbeat = player.level().getServer().getTickCount();
    }

    public static List<Vec3> positions(ServerPlayer player) {
        Session session = SESSIONS.get(player);
        return session != null && session.level == player.level() ? session.positions : List.of();
    }

    /** Active observer is first; all other observers remain in the normal send queue. */
    public static Vec3 position(ServerPlayer player) {
        var positions = positions(player);
        return positions.isEmpty() ? null : positions.getFirst();
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
                session.positions = session.requested;
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
            // Refresh stationary entities too: removing a distant observer changes
            // visibility even when neither the player nor those entities moved.
            player.level().getChunkSource().chunkMap.move(player);
            NetworkHandler.sendToPlayer(player, new PacketEditCameraView(previous.level.dimension().identifier(), List.of()));
        }
        reconcile(previous.level.getServer());
    }

    private static void reconcile(MinecraftServer server) {
        Set<Observer> observers = new HashSet<>();
        SESSIONS.forEach((player, session) -> {
            if (session.level.getServer() != server || session.positions.isEmpty() || session.level != player.level()) return;
            int radius = ((CameraChunkMapAccess) session.level.getChunkSource().chunkMap).geometryNode$viewDistance(player);
            int simulationRadius = Math.min(radius, server.getPlayerList().getSimulationDistance());
            for (Vec3 position : session.positions) {
                observers.add(new Observer(session.level, (int) Math.floor(position.x / 16),
                        (int) Math.floor(position.z / 16), radius, simulationRadius));
            }
        });
        CameraChunkLeasePlan previous = LEASES.getOrDefault(server, CameraChunkLeasePlan.EMPTY);
        CameraChunkLeasePlan next = previous.update(observers);
        boolean refresh = server.getTickCount() % 20 == 0;
        if (next != previous) {
            for (Lease lease : previous.leases()) if (!next.leases().contains(lease)) remove(lease);
            for (Lease lease : next.leases()) if (refresh || !previous.leases().contains(lease)) refresh(lease);
            if (next.leases().isEmpty()) LEASES.remove(server);
            else LEASES.put(server, next);
        } else if (refresh) {
            // Ticket expiry still needs a heartbeat; unchanged views do not need
            // another full region enumeration or a newly allocated lease union.
            for (Lease lease : next.leases()) refresh(lease);
        }
    }

    private static final class Session {
        final ServerLevel level;
        List<Vec3> requested = List.of();
        List<Vec3> positions = List.of();
        int heartbeat;
        Session(ServerLevel level) { this.level = level; }
    }

    private static void refresh(Lease lease) {
        // Level 32 supplies ready-to-send chunks without a blocking getChunk call.
        lease.level().getChunkSource().addTicketWithRadius(
                lease.simulate() ? SIMULATE.get() : LOAD.get(), new ChunkPos((int) lease.pos(), (int) (lease.pos() >> 32)), lease.simulate() ? 2 : 1);
    }

    private static void remove(Lease lease) {
        lease.level().getChunkSource().removeTicketWithRadius(
                lease.simulate() ? SIMULATE.get() : LOAD.get(), new ChunkPos((int) lease.pos(), (int) (lease.pos() >> 32)), lease.simulate() ? 2 : 1);
    }
}
