package com.mine.geometry_node.core.engine.system.marker;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.core.engine.system.marker.model.MarkerAddress;
import com.mine.geometry_node.core.engine.system.marker.model.MarkerAnchor;
import com.mine.geometry_node.core.engine.system.marker.model.MarkerAudience;
import com.mine.geometry_node.core.engine.system.marker.model.MarkerInstance;
import com.mine.geometry_node.core.engine.system.marker.model.MarkerRequest;
import com.mine.geometry_node.core.network.NetworkHandler;
import com.mine.geometry_node.core.network.packet.marker.MarkerPayload;
import com.mine.geometry_node.core.network.packet.s2c.PacketMarkerRemove;
import com.mine.geometry_node.core.network.packet.s2c.PacketMarkerSnapshot;
import com.mine.geometry_node.core.network.packet.s2c.PacketMarkerUpsert;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Server-authoritative marker lifecycle and recipient routing.
 */
public final class MarkerService {
    public static final MarkerService INSTANCE = new MarkerService();

    private static final int ENTITY_UPDATE_INTERVAL_TICKS = 2;
    private static final int EXPIRY_CHECK_INTERVAL_TICKS = 20;
    private static final double POSITION_UPDATE_EPSILON_SQR = 0.0025D;

    private final Map<MinecraftServer, RuntimeState> runtimeStates =
            Collections.synchronizedMap(new WeakHashMap<>());
    private boolean initialized;

    private MarkerService() {
    }

    public synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                syncSnapshot(player);
            }
        });
        NeoForge.EVENT_BUS.addListener((EntityJoinLevelEvent event) -> {
            if (event.getLevel() instanceof ServerLevel level) {
                handleEntityJoin(level, event.getEntity());
            }
        });
        NeoForge.EVENT_BUS.addListener((EntityLeaveLevelEvent event) -> {
            if (event.getLevel() instanceof ServerLevel level) {
                handleEntityLeave(level, event.getEntity());
            }
        });
        NeoForge.EVENT_BUS.addListener((LivingDeathEvent event) -> {
            if (event.getEntity().level() instanceof ServerLevel level) {
                removeEntityMarkers(level.getServer(), event.getEntity().getUUID());
            }
        });
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> tick(event.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> runtimeStates.remove(event.getServer()));
    }

    public boolean upsert(ServerLevel level, MarkerRequest request) {
        if (level == null || request == null) {
            return false;
        }

        MarkerType type = MarkerTypeRegistry.INSTANCE.get(request.typeId());
        if (type == null) {
            GeometryNode.LOGGER.warn("Cannot create marker '{}': unknown type '{}'",
                    request.address().key(), request.typeId());
            return false;
        }

        MinecraftServer server = level.getServer();
        long gameTime = server.overworld().getGameTime();
        long expiresAt = request.durationTicks() > 0
                ? safeAdd(gameTime, request.durationTicks())
                : MarkerInstance.NEVER_EXPIRES;
        MarkerInstance marker = new MarkerInstance(
                request.address(),
                type.id(),
                request.anchor(),
                request.text(),
                request.showDistance(),
                gameTime,
                expiresAt
        );

        MarkerStorage storage = MarkerStorage.get(server);
        RuntimeState state = stateFor(server, storage);
        storage.put(marker).ifPresent(previous -> state.unindex(previous));
        state.index(marker);
        state.entityStates.remove(marker.address());
        publishUpsert(server, marker, resolvePayload(server, marker));
        return true;
    }

    public boolean remove(MinecraftServer server, MarkerAddress address) {
        if (server == null || address == null) {
            return false;
        }
        MarkerStorage storage = MarkerStorage.get(server);
        RuntimeState state = stateFor(server, storage);
        MarkerInstance removed = storage.remove(address).orElse(null);
        if (removed == null) {
            return false;
        }
        state.unindex(removed);
        state.entityStates.remove(address);
        publishRemove(server, address);
        return true;
    }

    public void syncSnapshot(ServerPlayer player) {
        if (player == null) {
            return;
        }
        MinecraftServer server = player.level().getServer();
        MarkerStorage storage = MarkerStorage.get(server);
        purgeExpired(server, storage, stateFor(server, storage), server.overworld().getGameTime());

        ArrayList<MarkerPayload> payloads = new ArrayList<>();
        for (MarkerInstance marker : storage.all()) {
            if (isVisibleTo(marker.address(), player)) {
                payloads.add(resolvePayload(server, marker));
            }
        }
        NetworkHandler.sendToPlayer(player, new PacketMarkerSnapshot(payloads));
    }

    private void tick(MinecraftServer server) {
        long gameTime = server.overworld().getGameTime();
        MarkerStorage storage = MarkerStorage.get(server);
        RuntimeState state = stateFor(server, storage);

        if (gameTime % EXPIRY_CHECK_INTERVAL_TICKS == 0L) {
            purgeExpired(server, storage, state, gameTime);
        }
        if (gameTime % ENTITY_UPDATE_INTERVAL_TICKS != 0L) {
            return;
        }

        for (MarkerInstance marker : storage.all()) {
            if (!(marker.anchor() instanceof MarkerAnchor.Entity)) {
                continue;
            }

            MarkerPayload payload = resolvePayload(server, marker);
            EntityRuntimeState previous = state.entityStates.get(marker.address());
            EntityRuntimeState current = new EntityRuntimeState(payload.active(), payload.position(), payload.dimension());
            if (previous == null || current.materiallyDiffers(previous)) {
                state.entityStates.put(marker.address(), current);
                publishUpsert(server, marker, payload);
            }
        }
    }

    private void handleEntityJoin(ServerLevel level, Entity entity) {
        MinecraftServer server = level.getServer();
        MarkerStorage storage = MarkerStorage.get(server);
        RuntimeState state = stateFor(server, storage);
        Set<MarkerAddress> addresses = state.entityIndex.get(entity.getUUID());
        if (addresses == null || addresses.isEmpty()) {
            return;
        }

        for (MarkerAddress address : List.copyOf(addresses)) {
            MarkerInstance marker = storage.get(address).orElse(null);
            if (marker == null || !(marker.anchor() instanceof MarkerAnchor.Entity anchor)) {
                continue;
            }
            if (!anchor.dimension().equals(level.dimension())) {
                MarkerInstance moved = marker.withAnchor(new MarkerAnchor.Entity(level.dimension(), anchor.entityId()));
                storage.put(moved);
            }
            state.entityStates.remove(address);
        }
    }

    private void handleEntityLeave(ServerLevel level, Entity entity) {
        MinecraftServer server = level.getServer();
        MarkerStorage storage = MarkerStorage.get(server);
        RuntimeState state = stateFor(server, storage);
        Set<MarkerAddress> addresses = state.entityIndex.get(entity.getUUID());
        if (addresses == null || addresses.isEmpty()) {
            return;
        }

        Entity.RemovalReason reason = entity.getRemovalReason();
        if (reason != null && reason.shouldDestroy()) {
            for (MarkerAddress address : List.copyOf(addresses)) {
                remove(server, address);
            }
            return;
        }

        for (MarkerAddress address : List.copyOf(addresses)) {
            MarkerInstance marker = storage.get(address).orElse(null);
            if (marker == null) {
                continue;
            }
            MarkerPayload hidden = inactivePayload(marker);
            state.entityStates.put(address, new EntityRuntimeState(false, Vec3.ZERO, hidden.dimension()));
            publishUpsert(server, marker, hidden);
        }
    }

    private void removeEntityMarkers(MinecraftServer server, UUID entityId) {
        MarkerStorage storage = MarkerStorage.get(server);
        RuntimeState state = stateFor(server, storage);
        Set<MarkerAddress> addresses = state.entityIndex.get(entityId);
        if (addresses == null || addresses.isEmpty()) {
            return;
        }
        for (MarkerAddress address : List.copyOf(addresses)) {
            remove(server, address);
        }
    }

    private void purgeExpired(MinecraftServer server, MarkerStorage storage, RuntimeState state, long gameTime) {
        for (MarkerInstance removed : storage.removeExpired(gameTime)) {
            state.unindex(removed);
            state.entityStates.remove(removed.address());
            publishRemove(server, removed.address());
        }
    }

    private MarkerPayload resolvePayload(MinecraftServer server, MarkerInstance marker) {
        if (marker.anchor() instanceof MarkerAnchor.Coordinate coordinate) {
            return new MarkerPayload(
                    marker.address(), marker.typeId(), coordinate.dimension().identifier().toString(),
                    MarkerPayload.AnchorKind.COORDINATE, null, true, coordinate.position(),
                    marker.text(), marker.showDistance()
            );
        }

        MarkerAnchor.Entity anchor = (MarkerAnchor.Entity) marker.anchor();
        ServerLevel level = server.getLevel(anchor.dimension());
        Entity entity = level != null ? level.getEntity(anchor.entityId()) : null;
        if (entity == null || entity.isRemoved() || !entity.isAlive()) {
            return inactivePayload(marker);
        }
        return new MarkerPayload(
                marker.address(), marker.typeId(), anchor.dimension().identifier().toString(),
                MarkerPayload.AnchorKind.ENTITY, anchor.entityId(), true, entity.getEyePosition(),
                marker.text(), marker.showDistance()
        );
    }

    private static MarkerPayload inactivePayload(MarkerInstance marker) {
        MarkerAnchor.Entity anchor = (MarkerAnchor.Entity) marker.anchor();
        return new MarkerPayload(
                marker.address(), marker.typeId(), anchor.dimension().identifier().toString(),
                MarkerPayload.AnchorKind.ENTITY, anchor.entityId(), false, Vec3.ZERO,
                marker.text(), marker.showDistance()
        );
    }

    private static void publishUpsert(MinecraftServer server, MarkerInstance marker, MarkerPayload payload) {
        List<ServerPlayer> recipients = recipients(server, marker.address());
        if (!recipients.isEmpty()) {
            NetworkHandler.sendToPlayers(recipients, new PacketMarkerUpsert(payload));
        }
    }

    private static void publishRemove(MinecraftServer server, MarkerAddress address) {
        List<ServerPlayer> recipients = recipients(server, address);
        if (!recipients.isEmpty()) {
            NetworkHandler.sendToPlayers(recipients, new PacketMarkerRemove(address));
        }
    }

    private static List<ServerPlayer> recipients(MinecraftServer server, MarkerAddress address) {
        if (address.audience() == MarkerAudience.ALL) {
            return List.copyOf(server.getPlayerList().getPlayers());
        }
        ServerPlayer viewer = server.getPlayerList().getPlayer(address.viewerId());
        return viewer != null ? List.of(viewer) : List.of();
    }

    private static boolean isVisibleTo(MarkerAddress address, ServerPlayer player) {
        return address.audience() == MarkerAudience.ALL || player.getUUID().equals(address.viewerId());
    }

    private RuntimeState stateFor(MinecraftServer server, MarkerStorage storage) {
        synchronized (runtimeStates) {
            return runtimeStates.computeIfAbsent(server, ignored -> new RuntimeState(storage.all()));
        }
    }

    private static long safeAdd(long value, long increment) {
        if (Long.MAX_VALUE - value < increment) {
            return Long.MAX_VALUE;
        }
        return value + increment;
    }

    private static final class RuntimeState {
        private final Map<UUID, Set<MarkerAddress>> entityIndex = new HashMap<>();
        private final Map<MarkerAddress, EntityRuntimeState> entityStates = new HashMap<>();

        private RuntimeState(Iterable<MarkerInstance> markers) {
            for (MarkerInstance marker : markers) {
                index(marker);
            }
        }

        private void index(MarkerInstance marker) {
            if (marker.anchor() instanceof MarkerAnchor.Entity entity) {
                entityIndex.computeIfAbsent(entity.entityId(), ignored -> new HashSet<>()).add(marker.address());
            }
        }

        private void unindex(MarkerInstance marker) {
            if (!(marker.anchor() instanceof MarkerAnchor.Entity entity)) {
                return;
            }
            Set<MarkerAddress> addresses = entityIndex.get(entity.entityId());
            if (addresses == null) {
                return;
            }
            addresses.remove(marker.address());
            if (addresses.isEmpty()) {
                entityIndex.remove(entity.entityId());
            }
        }
    }

    private record EntityRuntimeState(boolean active, Vec3 position, String dimension) {
        private boolean materiallyDiffers(EntityRuntimeState previous) {
            if (active != previous.active || !dimension.equals(previous.dimension)) {
                return true;
            }
            return active && position.distanceToSqr(previous.position) >= POSITION_UPDATE_EPSILON_SQR;
        }
    }
}
