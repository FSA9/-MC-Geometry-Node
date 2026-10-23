package com.mine.geometry_node.core.engine.system.worldquery;

import com.mine.geometry_node.core.engine.system.worldquery.metadata.WorldObjectDataEdits;
import com.mine.geometry_node.core.engine.system.worldquery.metadata.WorldObjectTagEdits;
import com.mine.geometry_node.core.engine.system.worldquery.model.WorldObjectEntry;
import com.mine.geometry_node.core.engine.system.worldquery.model.WorldObjectPage;
import com.mine.geometry_node.core.engine.system.worldquery.model.WorldObjectQuery;
import com.mine.geometry_node.core.engine.system.worldquery.provider.WorldObjectProvider;
import com.mine.geometry_node.core.engine.system.worldquery.provider.WorldObjectProviders;

import com.mine.geometry_node.core.engine.system.asset.repository.RemoteAssetPermissions;
import com.mine.geometry_node.core.engine.system.viewport.EditCameraChunkService;
import com.mine.geometry_node.core.network.NetworkHandler;
import com.mine.geometry_node.core.network.packet.worldquery.*;
import com.mine.geometry_node.core.node.RegistryDataManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Server-thread scheduling, permission checks and bounded collection. Never opens a level or requests a chunk. */
public final class ServerWorldObjectQueries {
    public static final int MAX_SCANNED = 8192, MAX_LEVELS = 64,
            MAX_PENDING_PER_PLAYER = WorldObjectQueryScheduler.MAX_PENDING_PER_CONNECTION;
    private static final Map<MinecraftServer, WorldObjectQueryScheduler<ServerGamePacketListenerImpl>> QUEUES = new IdentityHashMap<>();
    private static final Map<ServerGamePacketListenerImpl, WorldObjectTagEdits> TAG_EDITS = new IdentityHashMap<>();
    private static final Map<ServerGamePacketListenerImpl, WorldObjectDataEdits> DATA_EDITS = new IdentityHashMap<>();
    private static boolean initialized;
    private ServerWorldObjectQueries() {}
    public static void init() {
        if (initialized) return;
        initialized = true; BuiltinWorldObjectProviders.init();
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                TAG_EDITS.remove(player.connection);
                DATA_EDITS.remove(player.connection);
                var queue = QUEUES.get(player.level().getServer());
                if (queue != null) queue.remove(player.connection);
            }
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> {
            DATA_EDITS.keySet().removeIf(connection -> connection.getPlayer().level().getServer() == event.getServer());
            TAG_EDITS.keySet().removeIf(connection -> connection.getPlayer().level().getServer() == event.getServer());
            var queue = QUEUES.remove(event.getServer());
            if (queue != null) queue.close();
        });
    }
    public static void receive(ServerPlayer player, PacketWorldObjectQuery request) {
        MinecraftServer server = player.level().getServer();
        if (!server.isSameThread()) throw new IllegalStateException("World queries require the server thread");
        ServerPlayer currentPlayer = currentPlayer(server, player.connection);
        if (currentPlayer == null || !server.isRunning()) return;
        player = currentPlayer;
        if (!RemoteAssetPermissions.canManageAssets(player)) {
            send(player, WorldObjectPage.empty(request.requestId(), "permission_denied")); return;
        }
        var queue = QUEUES.computeIfAbsent(server, ignored -> new WorldObjectQueryScheduler<>(
                // IntegratedServer.tickPaused skips ServerTickEvent, but its server task loop still runs.
                // schedule (unlike execute) never runs inline on the timer thread during server shutdown.
                task -> CompletableFuture.delayedExecutor(WorldObjectQueryScheduler.BATCH_INTERVAL_MILLIS,
                        TimeUnit.MILLISECONDS, delayed -> server.schedule(server.wrapRunnable(delayed))).execute(task),
                (connection, queued) -> process(server, connection, queued)));
        if (!queue.offer(player.connection, request)) {
            send(player, WorldObjectPage.empty(request.requestId(), "rate_limited")); return;
        }
    }
    public static void receive(ServerPlayer player, PacketWorldObjectTagEdit request) {
        MinecraftServer server = player.level().getServer();
        if (!server.isSameThread()) throw new IllegalStateException("Tag edits require the server thread");
        ServerPlayer current = currentPlayer(server, player.connection);
        if (current == null || !server.isRunning()) return;
        String message;
        try {
            boolean permitted = RemoteAssetPermissions.canManageAssets(current);
            var edits = TAG_EDITS.get(current.connection);
            message = !permitted ? WorldObjectTagEdits.PREFIX + "forbidden"
                    : edits == null ? WorldObjectTagEdits.PREFIX + "unavailable"
                    : edits.edit(true, request.target(), request.key(), request.expectedTags(), request.tag(), request.add());
        } catch (RuntimeException failure) {
            System.getLogger(ServerWorldObjectQueries.class.getName()).log(System.Logger.Level.WARNING, "World tag edit failed", failure);
            message = WorldObjectTagEdits.PREFIX + "failed";
        }
        NetworkHandler.sendToPlayer(current, new PacketWorldObjectTagResult(request.requestId(), message));
    }
    public static void receive(ServerPlayer player, PacketWorldObjectDataRequest request) {
        MinecraftServer server = player.level().getServer();
        if (!server.isSameThread()) throw new IllegalStateException("Data edits require the server thread");
        ServerPlayer current = currentPlayer(server, player.connection);
        if (current == null || !server.isRunning()) return;
        WorldObjectDataEdits.Result result;
        try {
            boolean permitted = RemoteAssetPermissions.canManageAssets(current);
            var edits = DATA_EDITS.get(current.connection);
            result = !permitted ? WorldObjectDataEdits.Result.error("forbidden")
                    : edits == null ? WorldObjectDataEdits.Result.error("unavailable")
                    : request.edit() ? edits.replace(true, request.target(), request.key(), request.expectedRevision(), request.replacement())
                    : edits.read(true, request.target(), request.key());
        } catch (RuntimeException failure) {
            System.getLogger(ServerWorldObjectQueries.class.getName()).log(System.Logger.Level.WARNING, "World custom data request failed", failure);
            result = WorldObjectDataEdits.Result.error("failed");
        }
        NetworkHandler.sendToPlayer(current, new PacketWorldObjectDataResult(request.requestId(), result.values(), result.revision(), result.message()));
    }
    private static ServerPlayer currentPlayer(MinecraftServer server, ServerGamePacketListenerImpl connection) {
        ServerPlayer player = server.getPlayerList().getPlayer(connection.getPlayer().getUUID());
        // Respawn keeps the connection; reconnect with the same UUID must not inherit queued work.
        return player != null && player.connection == connection && !player.hasDisconnected() ? player : null;
    }
    private static void process(MinecraftServer server, ServerGamePacketListenerImpl connection, PacketWorldObjectQuery request) {
        if (!server.isSameThread()) throw new IllegalStateException("World queries require the server thread");
        ServerPlayer player = currentPlayer(server, connection);
        if (player == null || !server.isRunning()) return;
        WorldObjectPage result;
        try { result = execute(player, request); }
        catch (RuntimeException failure) {
            System.getLogger(ServerWorldObjectQueries.class.getName()).log(System.Logger.Level.WARNING,
                    "World query " + request.requestId() + " failed", failure);
            result = WorldObjectPage.empty(request.requestId(), "query_failed");
        }
        send(player, result);
    }
    private static void send(ServerPlayer player, WorldObjectPage page) {
        NetworkHandler.sendToPlayer(player, new PacketWorldObjectPage(page));
    }
    static WorldObjectPage execute(ServerPlayer player, PacketWorldObjectQuery request) {
        if (!RemoteAssetPermissions.canManageAssets(player)) return WorldObjectPage.empty(request.requestId(), "permission_denied");
        WorldObjectQuery query = request.query(); MinecraftServer server = player.level().getServer();
        String current = player.level().dimension().identifier().toString();
        if (query.range() == WorldObjectQuery.Range.NEARBY && !query.dimension().isEmpty() && !query.dimension().equals(current))
            return WorldObjectPage.empty(request.requestId(), "nearby_requires_current_dimension");
        String selected = query.selectedDimension(current);
        // Navigation statistics use scope only. Column/category predicates apply to result rows.
        java.util.function.Predicate<String> acceptsDimension = dim -> selected.isEmpty() || selected.equals(dim);
        Map<String, ServerLevel> allLevels = new LinkedHashMap<>();
        allLevels.put(current, player.level());
        LinkedHashSet<String> dimensions = new LinkedHashSet<>();
        boolean metadataTruncated = false;
        // Registry catalog is independent of selected scope, scan budgets and matching objects.
        for (String dim : RegistryDataManager.getDimensions(server.registryAccess())) {
            if (dim.length() > 128) { metadataTruncated = true; continue; }
            if (dimensions.size() >= WorldObjectPage.MAX_METADATA) { metadataTruncated = true; break; }
            dimensions.add(dim);
        }
        boolean levelLimitReached = false;
        for (ServerLevel level : server.getAllLevels()) {
            String dim = level.dimension().identifier().toString();
            if (allLevels.containsKey(dim)) continue;
            if (allLevels.size() == MAX_LEVELS) { levelLimitReached = true; break; }
            allLevels.put(dim, level);
        }
        List<ServerLevel> levels = allLevels.entrySet().stream().filter(e -> acceptsDimension.test(e.getKey())).map(Map.Entry::getValue).toList();
        var context = new WorldObjectProvider.Context(server, levels, acceptsDimension);
        Vec3 camera = EditCameraChunkService.position(player);
        Vec3 origin = camera != null && player.isAlive() ? camera : player.position();
        List<String> coverage = new ArrayList<>();
        var providers = new ArrayList<>(WorldObjectProviders.all());
        providers.sort(Comparator.comparing(provider -> provider.id().equals("native")));
        var catalog = WorldObjectTypeCatalog.collect(providers, query);
        metadataTruncated |= catalog.partial();
        coverage.addAll(catalog.failures());
        WorldObjectResultCollector collector = new WorldObjectResultCollector(query, entry -> {
            if (!acceptsDimension.test(entry.dimension())) return false;
            if (query.range() == WorldObjectQuery.Range.NEARBY && !query.withinRadius(entry, origin)) return false;
            if (query.range() == WorldObjectQuery.Range.LOADED) {
                ServerLevel level = allLevels.get(entry.dimension());
                var p = entry.position();
                if (level == null || Math.abs(p.x) > 30_000_000 || Math.abs(p.z) > 30_000_000
                        || level.getChunkSource().getChunkNow((int)Math.floor(p.x / 16), (int)Math.floor(p.z / 16)) == null) return false;
            }
            return true;
        }) {
            @Override protected WorldObjectEntry advertise(WorldObjectEntry entry, int remainingBytes) {
                // A capability lease holds the adapter's live owner guard, not a mutable query mirror.
                var access = ownerProvider.resolveMetadata(context, entry);
                WorldObjectEntry advertised = TAG_EDITS.computeIfAbsent(player.connection, ignored -> new WorldObjectTagEdits()).advertise(entry, access);
                try {
                    return DATA_EDITS.computeIfAbsent(player.connection, ignored -> new WorldObjectDataEdits(server.registryAccess()))
                            .advertise(advertised, access);
                } catch (IllegalArgumentException | IllegalStateException | UnsupportedOperationException unavailable) {
                    // A DATA codec/owner failure must not discard otherwise valid query rows or TAG support.
                    return advertised;
                }
            }
        };
        List<WorldObjectEntry> entries = collector.entries;
        if (levelLimitReached && (selected.isEmpty() || !allLevels.containsKey(selected))
                && providers.stream().anyMatch(p -> !Set.of("scene", "projection", "schematic").contains(p.id()))) {
            collector.countsPartial = collector.truncated = true; coverage.add("levels:partial");
        }
        for (WorldObjectProvider provider : providers) {
            if (collector.stopped) { coverage.add(provider.id() + ":not_scanned"); continue; }
            collector.provider = provider.id(); collector.ownerProvider = provider;
            try {
                provider.collect(context, collector);
                coverage.add(provider.id() + ":" + provider.coverage() + (collector.stopped || collector.resultsFull ? ":partial" : ":complete"));
            } catch (RuntimeException failure) {
                collector.countsPartial = collector.truncated = true; coverage.add(provider.id() + ":failed");
            }
        }
        if (metadataTruncated) coverage.add("metadata:partial");
        if (collector.countsPartial) coverage.add("counts:partial");
        if (query.range() == WorldObjectQuery.Range.LOADED) coverage.add("range:pivot_in_loaded_chunk");
        coverage.add("models:server_collision_instances_only");
        entries.sort(Comparator.comparing(WorldObjectEntry::key));
        return new WorldObjectPage(request.requestId(), entries, collector.truncated,
                dimensions.stream().sorted().toList(), catalog.types(), coverage,
                WorldObjectTypeCatalog.resultMessage(collector.truncated, metadataTruncated), collector.counts());
    }
}
