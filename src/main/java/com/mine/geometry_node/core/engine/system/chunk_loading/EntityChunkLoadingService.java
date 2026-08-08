package com.mine.geometry_node.core.engine.system.chunk_loading;

import com.mine.geometry_node.GeometryNode;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.common.world.chunk.TicketHelper;
import net.neoforged.neoforge.common.world.chunk.TicketSet;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Owns one persistent strong-loading configuration per entity UUID.
 */
public final class EntityChunkLoadingService {
    public static final EntityChunkLoadingService INSTANCE = new EntityChunkLoadingService();
    public static final TicketController TICKET_CONTROLLER = new TicketController(
            Identifier.fromNamespaceAndPath(GeometryNode.MODID, "entity_chunk_loading"),
            EntityChunkLoadingService::validatePersistedTickets
    );

    private boolean initialized;

    private EntityChunkLoadingService() {
    }

    public static void registerTicketController(RegisterTicketControllersEvent event) {
        event.register(TICKET_CONTROLLER);
    }

    public synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;

        NeoForge.EVENT_BUS.addListener((EntityJoinLevelEvent event) -> {
            if (event.getLevel() instanceof ServerLevel level) {
                updateForEntity(level, event.getEntity());
            }
        });
        NeoForge.EVENT_BUS.addListener((EntityLeaveLevelEvent event) -> {
            if (event.getLevel() instanceof ServerLevel level
                    && event.getEntity().getRemovalReason() != null
                    && event.getEntity().getRemovalReason().shouldDestroy()) {
                disable(level.getServer(), event.getEntity().getUUID());
            }
        });
        NeoForge.EVENT_BUS.addListener((LivingDeathEvent event) -> {
            if (event.getEntity().level() instanceof ServerLevel level) {
                disable(level.getServer(), event.getEntity().getUUID());
            }
        });
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> restoreAll(event.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> tick(event.getServer()));
    }

    public boolean configure(Entity entity, int radius) {
        if (entity == null || entity.isRemoved() || !entity.isAlive() || !(entity.level() instanceof ServerLevel level)) {
            return false;
        }

        MinecraftServer server = level.getServer();
        EntityChunkLoadingStorage storage = EntityChunkLoadingStorage.get(server);
        EntityChunkLoadingConfig next = configFor(entity, radius);
        EntityChunkLoadingConfig previous = storage.put(next).orElse(null);
        reconcile(server, previous, next);
        return true;
    }

    public boolean disable(Entity entity) {
        if (entity == null || !(entity.level() instanceof ServerLevel level)) {
            return false;
        }
        return disable(level.getServer(), entity.getUUID());
    }

    public boolean disable(MinecraftServer server, UUID entityId) {
        if (server == null || entityId == null) {
            return false;
        }
        EntityChunkLoadingConfig previous = EntityChunkLoadingStorage.get(server).remove(entityId).orElse(null);
        if (previous == null) {
            return false;
        }
        release(server, previous);
        return true;
    }

    private static void tick(MinecraftServer server) {
        EntityChunkLoadingStorage storage = EntityChunkLoadingStorage.get(server);
        for (EntityChunkLoadingConfig config : storage.all()) {
            Entity entity = server.overworld().getEntityInAnyDimension(config.entityId());
            if (entity != null && entity.level() instanceof ServerLevel level && !entity.isRemoved() && entity.isAlive()) {
                updateForEntity(level, entity);
            }
        }
    }

    private static void restoreAll(MinecraftServer server) {
        for (EntityChunkLoadingConfig config : EntityChunkLoadingStorage.get(server).all()) {
            ServerLevel level = server.getLevel(config.dimension());
            if (level == null) {
                continue;
            }
            for (long chunk : coveredChunks(config)) {
                force(level, config.entityId(), chunk, true);
            }
        }
    }

    private static void updateForEntity(ServerLevel level, Entity entity) {
        MinecraftServer server = level.getServer();
        EntityChunkLoadingStorage storage = EntityChunkLoadingStorage.get(server);
        EntityChunkLoadingConfig previous = storage.get(entity.getUUID()).orElse(null);
        if (previous == null) {
            return;
        }

        EntityChunkLoadingConfig next = configFor(entity, previous.radius());
        if (previous.equals(next)) {
            return;
        }
        storage.put(next);
        reconcile(server, previous, next);
    }

    private static EntityChunkLoadingConfig configFor(Entity entity, int radius) {
        ChunkPos center = ChunkPos.containing(entity.blockPosition());
        return new EntityChunkLoadingConfig(
                entity.getUUID(),
                entity.level().dimension(),
                center.x(),
                center.z(),
                radius
        );
    }

    private static void reconcile(MinecraftServer server,
                                  EntityChunkLoadingConfig previous,
                                  EntityChunkLoadingConfig next) {
        Set<Long> previousChunks = previous == null ? Set.of() : coveredChunks(previous);
        Set<Long> nextChunks = coveredChunks(next);

        if (previous != null && previous.dimension().equals(next.dimension())) {
            ServerLevel level = server.getLevel(next.dimension());
            if (level == null) {
                return;
            }
            for (long chunk : previousChunks) {
                if (!nextChunks.contains(chunk)) {
                    force(level, previous.entityId(), chunk, false);
                }
            }
            for (long chunk : nextChunks) {
                if (!previousChunks.contains(chunk)) {
                    force(level, next.entityId(), chunk, true);
                }
            }
            return;
        }

        if (previous != null) {
            release(server, previous);
        }
        ServerLevel targetLevel = server.getLevel(next.dimension());
        if (targetLevel != null) {
            for (long chunk : nextChunks) {
                force(targetLevel, next.entityId(), chunk, true);
            }
        }
    }

    private static void release(MinecraftServer server, EntityChunkLoadingConfig config) {
        ServerLevel level = server.getLevel(config.dimension());
        if (level == null) {
            return;
        }
        for (long chunk : coveredChunks(config)) {
            force(level, config.entityId(), chunk, false);
        }
    }

    private static void force(ServerLevel level, UUID entityId, long chunk, boolean add) {
        TICKET_CONTROLLER.forceChunk(level, entityId, ChunkPos.getX(chunk), ChunkPos.getZ(chunk), add, true);
    }

    private static Set<Long> coveredChunks(EntityChunkLoadingConfig config) {
        int diameter = config.radius() * 2 + 1;
        Set<Long> chunks = new HashSet<>(diameter * diameter);
        for (int x = config.centerChunkX() - config.radius(); x <= config.centerChunkX() + config.radius(); x++) {
            for (int z = config.centerChunkZ() - config.radius(); z <= config.centerChunkZ() + config.radius(); z++) {
                chunks.add(ChunkPos.pack(x, z));
            }
        }
        return chunks;
    }

    private static void validatePersistedTickets(ServerLevel level, TicketHelper helper) {
        EntityChunkLoadingStorage storage = EntityChunkLoadingStorage.get(level.getServer());
        for (var entry : helper.getEntityTickets().entrySet()) {
            UUID entityId = entry.getKey();
            EntityChunkLoadingConfig config = storage.get(entityId).orElse(null);
            if (config == null || !config.dimension().equals(level.dimension())) {
                helper.removeAllTickets(entityId);
                continue;
            }

            Set<Long> expected = coveredChunks(config);
            TicketSet tickets = entry.getValue();
            removeUnexpectedTickets(helper, entityId, tickets.normal(), false, expected);
            removeUnexpectedTickets(helper, entityId, tickets.naturalSpawning(), true, expected);
        }
    }

    private static void removeUnexpectedTickets(TicketHelper helper,
                                                UUID entityId,
                                                LongSet tickets,
                                                boolean naturalSpawning,
                                                Set<Long> expected) {
        for (long chunk : tickets.toLongArray()) {
            if (!expected.contains(chunk)) {
                helper.removeTicket(entityId, chunk, naturalSpawning);
            }
        }
    }
}
