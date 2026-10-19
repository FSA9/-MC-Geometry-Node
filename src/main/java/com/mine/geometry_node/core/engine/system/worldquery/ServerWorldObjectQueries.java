package com.mine.geometry_node.core.engine.system.worldquery;

import com.mine.geometry_node.core.engine.system.asset.RemoteAssetPermissions;
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
    private static boolean initialized;
    private ServerWorldObjectQueries() {}
    public static void init() {
        if (initialized) return;
        initialized = true; BuiltinWorldObjectProviders.init();
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                var queue = QUEUES.get(player.level().getServer());
                if (queue != null) queue.remove(player.connection);
            }
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> {
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
        List<WorldObjectEntry> entries = new ArrayList<>(); Set<String> keys = new HashSet<>();
        List<String> coverage = new ArrayList<>();
        var providers = new ArrayList<>(WorldObjectProviders.all());
        providers.sort(Comparator.comparing(provider -> provider.id().equals("native")));
        var catalog = WorldObjectTypeCatalog.collect(providers, query);
        metadataTruncated |= catalog.partial();
        coverage.addAll(catalog.failures());
        class Collector implements WorldObjectProvider.Sink {
            int scanned, bytes;
            boolean truncated, stopped;
            String provider;
            // A time cap supplements the cardinality budget; results explicitly report incompleteness.
            long deadline;
            public boolean scan() {
                if (stopped) return false;
                if (deadline == 0) deadline = System.nanoTime() + 10_000_000L;
                if (++scanned > MAX_SCANNED || System.nanoTime() > deadline) { truncated = stopped = true; return false; }
                return true;
            }
            public boolean emit(WorldObjectEntry entry) {
                if (stopped) return false;
                if (!entry.provider().equals(provider)) throw new IllegalArgumentException("Provider identity mismatch");
                if (!acceptsDimension.test(entry.dimension()) || !query.matchesMetadata(entry)) return true;
                if (query.range() == WorldObjectQuery.Range.NEARBY && !query.withinRadius(entry, origin)) return true;
                if (query.range() == WorldObjectQuery.Range.LOADED) {
                    ServerLevel level = allLevels.get(entry.dimension());
                    var p = entry.position();
                    if (level == null || Math.abs(p.x) > 30_000_000 || Math.abs(p.z) > 30_000_000
                            || level.getChunkSource().getChunkNow((int)Math.floor(p.x / 16), (int)Math.floor(p.z / 16)) == null) return true;
                }
                if (!keys.add(entry.key())) return true;
                if (entries.size() >= query.limit() || entry.estimatedBytes() > WorldObjectPage.MAX_ENTRY_BYTES - bytes) {
                    truncated = stopped = true; return false;
                }
                entries.add(entry); bytes += entry.estimatedBytes();
                return true;
            }
        }
        Collector collector = new Collector();
        if (levelLimitReached && (selected.isEmpty() || !allLevels.containsKey(selected))
                && providers.stream().anyMatch(p -> (query.providers().isEmpty() || query.providers().contains(p.id()))
                        && !Set.of("scene", "projection", "schematic").contains(p.id()))) {
            collector.truncated = true; coverage.add("levels:partial");
        }
        for (WorldObjectProvider provider : providers) {
            if (!query.providers().isEmpty() && !query.providers().contains(provider.id())) continue;
            if (collector.stopped) { coverage.add(provider.id() + ":not_scanned"); continue; }
            collector.provider = provider.id();
            try {
                provider.collect(context, collector);
                coverage.add(provider.id() + ":" + provider.coverage() + (collector.stopped ? ":partial" : ":complete"));
            } catch (RuntimeException failure) {
                collector.truncated = true; coverage.add(provider.id() + ":failed");
            }
        }
        if (metadataTruncated) coverage.add("metadata:partial");
        if (query.range() == WorldObjectQuery.Range.LOADED) coverage.add("range:pivot_in_loaded_chunk");
        coverage.add("models:server_collision_instances_only");
        entries.sort(Comparator.comparing(WorldObjectEntry::key));
        return new WorldObjectPage(request.requestId(), entries, collector.truncated,
                dimensions.stream().sorted().toList(), catalog.types(), coverage,
                WorldObjectPage.resultMessage(collector.truncated, metadataTruncated));
    }
}
