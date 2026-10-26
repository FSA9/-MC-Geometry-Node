package com.mine.geometry_node.client.feature.faction;

import com.mine.geometry_node.client.network.request.ClientRequestTracker;
import com.mine.geometry_node.core.cache.*;
import com.mine.geometry_node.core.metadata.TagPolicy;
import com.mine.geometry_node.core.engine.system.faction.model.*;
import com.mine.geometry_node.core.engine.system.faction.storage.FactionJsonCodec;
import com.mine.geometry_node.core.engine.metadata.value.GraphMetadataDataValues;
import com.mine.geometry_node.core.network.ClientboundPayloadRegistry;
import com.mine.geometry_node.core.network.NetworkHandler;
import com.mine.geometry_node.core.network.packet.faction.*;
import com.mine.geometry_node.core.node.value.RichTextValue;
import com.mine.geometry_node.core.node.RegistryDataManager;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Connection-owned catalog mirror. Commands serialize against the last authoritative revision. */
public final class ClientFactionRepository {
    public static final ClientFactionRepository INSTANCE = new ClientFactionRepository(
            packet -> {
                var minecraft = Minecraft.getInstance();
                if (minecraft.level == null) throw new IllegalStateException("No client level");
                GraphMetadataDataValues.encode(packet.values(), minecraft.level.registryAccess(), GraphMetadataDataValues.MAX_WRITE_BYTES);
                NetworkHandler.sendToServer(packet);
            }, task -> Minecraft.getInstance().execute(task),
            () -> Minecraft.getInstance().getConnection() != null);
    private static final int MAX_PENDING = 32;
    private record Command(String operation, Map<String, Object> values, CompletableFuture<Void> result, int retries) {
        Command(String operation, Map<String, Object> values, CompletableFuture<Void> result) { this(operation, values, result, 0); }
        Command retry() { return new Command(operation, values, result, retries + 1); }
    }
    private record Pending(int id, Command command, long generation) {}
    private final Consumer<PacketFactionRequest> transport;
    private final Consumer<Runnable> clientQueue;
    private final BooleanSupplier connected;
    private final ClientRequestTracker.Group requests;
    private final ArrayDeque<Command> commands = new ArrayDeque<>();
    private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();
    private volatile FactionCatalog catalog = FactionCatalog.EMPTY;
    private volatile boolean loaded, loading;
    private volatile String error = "";
    private Pending inFlight;
    private long generation;
    private boolean initialized, coolingDown;

    ClientFactionRepository(Consumer<PacketFactionRequest> transport, Consumer<Runnable> clientQueue,
                            BooleanSupplier connected) {
        this.transport = Objects.requireNonNull(transport);
        this.clientQueue = Objects.requireNonNull(clientQueue);
        this.connected = Objects.requireNonNull(connected);
        requests = ClientRequestTracker.group("factions", clientQueue::accept);
    }

    public synchronized void init() {
        if (initialized) return;
        initialized = true;
        RegistryDataManager.registerDynamicOptions(RegistryDataManager.FACTION_REGISTRY_ID, this::selectOptions);
        ClientboundPayloadRegistry.registerClientReceiver(PacketFactionResult.TYPE, (packet, context) -> {
            long expected = generation();
            context.queue(() -> accept(packet, expected));
        });
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> disconnect());
        CacheRegistry.GLOBAL.register(new CacheDescriptor("geometry_node:faction.catalog",
                "geometry_node.cache.faction.catalog", CacheDescriptor.Domain.CLIENT,
                CacheDescriptor.Storage.MEMORY, CacheDescriptor.Lifetime.CONNECTION), new ManagedCache() {
            public java.util.concurrent.CompletionStage<CacheStats> stats() {
                return onClient(() -> new CacheStats(loaded ? catalog.factions().size() : 0, -1, 0,
                        loading && loaded ? catalog.factions().size() : 0));
            }
            public java.util.concurrent.CompletionStage<Void> clear(CacheClearRequest request) {
                return clearOutcome(request).thenApply(ignored -> null);
            }
            public java.util.concurrent.CompletionStage<CacheClearOutcome> clearOutcome(CacheClearRequest request) {
                if (!supports(request)) return CompletableFuture.failedFuture(new IllegalArgumentException("Unsupported cache scope"));
                return onClient(() -> {
                    if (loading) return CacheClearOutcome.retained(loaded ? catalog.factions().size() : 0);
                    loaded = false; catalog = FactionCatalog.EMPTY; error = "";
                    notifyListeners();
                    if (!listeners.isEmpty()) refresh();
                    return CacheClearOutcome.completed(0);
                });
            }
        });
    }

    public FactionCatalog snapshot() { return catalog; }
    /** A fresh detached option snapshot: renames affect labels, never graph values. */
    public RegistryDataManager.DynamicOptions selectOptions() {
        FactionCatalog current = catalog;
        List<FactionDefinition> sorted = current.factions().stream()
                .sorted(Comparator.comparing((FactionDefinition faction) -> faction.name().plain(),
                        String.CASE_INSENSITIVE_ORDER).thenComparing(FactionDefinition::id)).toList();
        Map<String, String> labels = new LinkedHashMap<>();
        for (FactionDefinition faction : sorted) labels.put(faction.id().toString(), faction.name().plain());
        return new RegistryDataManager.DynamicOptions(List.copyOf(labels.keySet()), labels, loaded);
    }
    public boolean loaded() { return loaded; }
    public boolean loading() { return loading; }
    public String error() { return error; }
    public void addChangeListener(Runnable listener) { listeners.addIfAbsent(Objects.requireNonNull(listener)); }
    public void removeChangeListener(Runnable listener) { listeners.remove(listener); }
    public synchronized long generation() { return generation; }

    public void refresh() {
        long expectedGeneration = generation();
        clientQueue.accept(() -> {
            synchronized (this) {
                if (generation != expectedGeneration) return;
                if (inFlight != null && inFlight.command.operation.equals("READ")
                        || commands.stream().anyMatch(command -> command.operation.equals("READ"))) return;
                if (commands.size() >= MAX_PENDING) return;
                commands.addLast(new Command("READ", Map.of(), new CompletableFuture<>()));
                loading = true;
            }
            notifyListeners(); dispatch();
        });
    }

    public CompletableFuture<Void> create(RichTextValue name) { return enqueue("CREATE", Map.of("name", name)); }
    public CompletableFuture<Void> update(UUID id, RichTextValue name, RichTextValue description) {
        return enqueue("UPDATE", Map.of("id", id, "name", name, "description", description));
    }
    public CompletableFuture<Void> rename(UUID id, RichTextValue name) {
        return enqueue("UPDATE", Map.of("id", id, "name", name));
    }
    public CompletableFuture<Void> describe(UUID id, RichTextValue description) {
        return enqueue("UPDATE", Map.of("id", id, "description", description));
    }
    public CompletableFuture<Void> delete(Set<UUID> ids) {
        return enqueue("DELETE", Map.of("ids", List.copyOf(ids)));
    }
    public CompletableFuture<Void> relation(UUID source, UUID target, int score) {
        return enqueue("RELATION", Map.of("source", source, "target", target, "score", FactionRules.score(score)));
    }
    public CompletableFuture<Void> rank(UUID faction, Integer previous, int level, RichTextValue name) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", faction); values.put("level", level); values.put("name", name);
        if (previous != null) values.put("previous", previous);
        return enqueue("RANK", values);
    }
    public CompletableFuture<Void> removeRank(UUID faction, int level) {
        return enqueue("REMOVE_RANK", Map.of("id", faction, "level", level));
    }

    public CompletableFuture<Void> addTag(UUID id, String tag) {
        return enqueue("ADD_TAG", Map.of("id", id, "tag", TagPolicy.EXACT.normalize(tag)));
    }
    public CompletableFuture<Void> removeTag(UUID id, String tag) {
        return enqueue("REMOVE_TAG", Map.of("id", id, "tag", TagPolicy.EXACT.normalize(tag)));
    }
    /** The editor baseline travels unchanged even if newer catalog pushes arrive before dispatch. */
    public CompletableFuture<Void> replaceData(UUID id, Map<String, Object> expected, Map<String, Object> replacement) {
        return enqueue("REPLACE_DATA", Map.of("id", id, "expected", expected, "replacement", replacement));
    }

    private CompletableFuture<Void> enqueue(String operation, Map<String, Object> values) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        Map<String, Object> detached = GraphMetadataDataValues.snapshot(values);
        long expectedGeneration = generation();
        clientQueue.accept(() -> {
            synchronized (this) {
                if (generation != expectedGeneration) {
                    result.completeExceptionally(new IllegalStateException(key("disconnected"))); return;
                }
                if (commands.size() >= MAX_PENDING) {
                    result.completeExceptionally(new IllegalStateException(key("busy"))); return;
                }
                commands.addLast(new Command(operation, detached, result)); loading = true;
            }
            notifyListeners(); dispatch();
        });
        return result;
    }

    private void dispatch() {
        final Pending pending;
        final PacketFactionRequest packet;
        synchronized (this) {
            if (inFlight != null || coolingDown || commands.isEmpty()) return;
            if (!loaded && !commands.getFirst().operation.equals("READ"))
                commands.addFirst(new Command("READ", Map.of(), new CompletableFuture<>()));
            Command command = commands.removeFirst();
            pending = new Pending(requests.nextRequestId(), command, generation); inFlight = pending; loading = true;
            packet = new PacketFactionRequest(pending.id, command.operation.equals("READ") ? -1 : catalog.revision(),
                    command.operation, command.values);
        }
        requests.register(pending.id, PacketFactionResult.class, reply -> finish(pending, reply),
                () -> fail(pending, "timeout"));
        try {
            if (!connected.getAsBoolean()) { fail(pending, "disconnected"); return; }
            transport.accept(packet);
        } catch (RuntimeException failure) { fail(pending, "failed"); }
    }

    /** Connection generation is captured before network-to-client dispatch. */
    public void accept(PacketFactionResult packet, long expectedGeneration) {
        synchronized (this) { if (generation != expectedGeneration) return; }
        if (packet.requestId() == 0) {
            if (!packet.catalogJson().isEmpty()) {
                try { publish(FactionJsonCodec.decode(packet.catalogJson())); notifyListeners(); }
                catch (RuntimeException invalid) { error = key("invalid"); notifyListeners(); }
            }
        } else requests.complete(packet.requestId(), packet);
    }

    private void finish(Pending pending, PacketFactionResult reply) {
        synchronized (this) {
            if (inFlight != pending || generation != pending.generation) return;
            if (!reply.success() && reply.message().equals("rate_limited") && pending.command.retries < 2) {
                inFlight = null; commands.addFirst(pending.command.retry()); coolingDown = true;
                long expectedGeneration = generation;
                CompletableFuture.delayedExecutor(150L << pending.command.retries, java.util.concurrent.TimeUnit.MILLISECONDS)
                        .execute(() -> clientQueue.accept(() -> {
                            synchronized (this) {
                                if (generation != expectedGeneration) return;
                                coolingDown = false;
                            }
                            dispatch();
                        }));
                return;
            }
        }
        boolean success = reply.success();
        String reason = reply.message().isEmpty() ? "failed" : reply.message();
        try {
            if (!reply.catalogJson().isEmpty()) publish(FactionJsonCodec.decode(reply.catalogJson()));
            else if (success) throw new IllegalArgumentException("Missing faction catalog");
        } catch (RuntimeException invalid) { success = false; reason = "invalid"; loaded = false; }
        List<Command> abandoned = List.of();
        synchronized (this) {
            inFlight = null;
            error = success ? "" : key(reason);
            // Settle the old queue before invoking futures/listeners, which may enqueue or disconnect.
            if (!success && (!loaded || Set.of("permission_denied", "disconnected", "failed").contains(reason)))
                abandoned = takeQueued();
            loading = !commands.isEmpty();
        }
        if (success) pending.command.result.complete(null);
        else pending.command.result.completeExceptionally(new IllegalStateException(error));
        failCommands(abandoned, key(reason));
        if (generation() == pending.generation) { notifyListeners(); dispatch(); }
    }

    private void publish(FactionCatalog next) {
        if (!loaded || next.revision() >= catalog.revision()) { catalog = next; loaded = true; }
    }

    private void fail(Pending pending, String reason) {
        List<Command> abandoned;
        String message = key(reason);
        synchronized (this) {
            if (inFlight != pending || generation != pending.generation) return;
            requests.cancel(pending.id); inFlight = null; loaded = false; loading = false;
            coolingDown = false; error = message; abandoned = takeQueued();
        }
        pending.command.result.completeExceptionally(new IllegalStateException(message));
        failCommands(abandoned, message);
        if (generation() == pending.generation) {
            notifyListeners();
            // A timed-out write may already have committed. Read once; never replay the write.
            if (reason.equals("timeout") && !pending.command.operation.equals("READ")) refresh();
        }
    }

    private List<Command> takeQueued() {
        List<Command> abandoned = List.copyOf(commands); commands.clear(); return abandoned;
    }
    private static void failCommands(List<Command> abandoned, String reason) {
        abandoned.forEach(command -> command.result.completeExceptionally(new IllegalStateException(reason)));
    }

    public void disconnect() {
        Pending abandoned;
        List<Command> queued;
        synchronized (this) {
            generation++; requests.reset(); abandoned = inFlight; inFlight = null;
            queued = takeQueued(); coolingDown = false;
            catalog = FactionCatalog.EMPTY; loaded = false; loading = false; error = "";
        }
        if (abandoned != null) abandoned.command.result.completeExceptionally(new IllegalStateException(key("disconnected")));
        failCommands(queued, key("disconnected")); notifyListeners();
    }

    private <T> CompletableFuture<T> onClient(java.util.function.Supplier<T> task) {
        CompletableFuture<T> result = new CompletableFuture<>();
        long expectedGeneration = generation();
        Minecraft.getInstance().schedule(() -> {
            if (generation() != expectedGeneration) {
                result.completeExceptionally(new IllegalStateException(key("disconnected"))); return;
            }
            try { result.complete(task.get()); }
            catch (RuntimeException failure) { result.completeExceptionally(failure); }
        });
        return result;
    }
    private void notifyListeners() {
        for (Runnable listener : listeners) {
            try { listener.run(); }
            catch (RuntimeException failure) {
                System.getLogger(ClientFactionRepository.class.getName()).log(System.Logger.Level.WARNING,
                        "Faction repository listener failed", failure);
            }
        }
    }
    private static String key(String reason) {
        return reason.startsWith("geometry_node.") ? reason : "geometry_node.faction.error." + reason;
    }
}
