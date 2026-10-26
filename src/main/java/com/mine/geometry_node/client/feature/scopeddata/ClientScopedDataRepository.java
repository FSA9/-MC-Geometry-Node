package com.mine.geometry_node.client.feature.scopeddata;

import com.mine.geometry_node.client.network.request.ClientRequestTracker;
import com.mine.geometry_node.core.engine.metadata.value.GraphMetadataDataValues;
import com.mine.geometry_node.core.network.ClientboundPayloadRegistry;
import com.mine.geometry_node.core.network.NetworkHandler;
import com.mine.geometry_node.core.network.packet.scopeddata.PacketScopedDataRequest;
import com.mine.geometry_node.core.network.packet.scopeddata.PacketScopedDataResult;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static com.mine.geometry_node.core.network.packet.scopeddata.PacketScopedDataRequest.Action.*;

/** Live editor sessions, not a second scoped store. Transport and completions use the client queue. */
public final class ClientScopedDataRepository {
    public static final String GLOBAL = "GLOBAL";
    public static final String PREFIX = "geometry_node.database.data_update_";
    public static final ClientScopedDataRepository INSTANCE = new ClientScopedDataRepository(packet -> {
        var client = Minecraft.getInstance();
        if (client.level == null) throw new IllegalStateException("No client level");
        if (packet.action() == REPLACE)
            GraphMetadataDataValues.encode(packet.replacement(), client.level.registryAccess(), GraphMetadataDataValues.MAX_WRITE_BYTES);
        NetworkHandler.sendToServer(packet);
    }, task -> Minecraft.getInstance().execute(task),
            () -> Minecraft.getInstance().getConnection() != null && Minecraft.getInstance().level != null);

    public record Snapshot(List<String> dimensions, boolean loading, boolean loaded, String message, long sequence) {
        public Snapshot { dimensions = List.copyOf(dimensions); }
    }
    public record DataSnapshot(Map<String, Object> values, boolean loading, boolean available,
                               boolean editable, boolean pending, String message, boolean fetched) {
        public DataSnapshot { values = GraphMetadataDataValues.snapshot(values); }
    }
    private static final class Slot {
        Map<String, Object> values = Map.of();
        String revision = "", message = "";
        boolean loading, pending, fetched;
        int request;
    }
    private record Pending(int id, Session session, PacketScopedDataRequest.Action action, String target,
                           Slot slot, long generation, Consumer<String> completion) {}
    private final Consumer<PacketScopedDataRequest> transport;
    private final Consumer<Runnable> clientQueue;
    private final BooleanSupplier connected;
    private final ClientRequestTracker.Group requests;
    private final Set<Session> sessions = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<Integer, Pending> pending = new LinkedHashMap<>();
    private long generation;
    private boolean initialized;

    ClientScopedDataRepository(Consumer<PacketScopedDataRequest> transport, Consumer<Runnable> clientQueue,
                               BooleanSupplier connected) {
        this.transport = Objects.requireNonNull(transport);
        this.clientQueue = Objects.requireNonNull(clientQueue);
        this.connected = Objects.requireNonNull(connected);
        requests = ClientRequestTracker.group("scoped-data", clientQueue::accept);
    }
    public synchronized void init() {
        if (initialized) return;
        initialized = true;
        ClientboundPayloadRegistry.registerClientReceiver(PacketScopedDataResult.TYPE, (packet, context) -> {
            long expected = generation();
            context.queue(() -> accept(packet, expected));
        });
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> disconnect());
    }
    public synchronized long generation() { return generation; }
    public synchronized Session newSession() {
        if (sessions.size() >= 64) throw new IllegalStateException("Too many scoped data sessions");
        Session session = new Session(generation); sessions.add(session); return session;
    }
    public void accept(PacketScopedDataResult packet, long expectedGeneration) {
        synchronized (this) {
            if (generation != expectedGeneration || packet.requestId() <= 0 || packet.requestId() > Integer.MAX_VALUE) return;
        }
        requests.complete((int) packet.requestId(), packet);
    }

    public final class Session implements AutoCloseable {
        private final long ownerGeneration;
        private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();
        private final Map<String, Slot> slots = new LinkedHashMap<>();
        private Snapshot snapshot = new Snapshot(List.of(), false, false, "", 0);
        private int catalogRequest;
        private boolean closed;
        private Session(long generation) { ownerGeneration = generation; }
        public Snapshot snapshot() { synchronized (ClientScopedDataRepository.this) { return snapshot; } }
        public long generation() { return ownerGeneration; }
        public boolean current() { synchronized (ClientScopedDataRepository.this) { return !closed && ownerGeneration == generation; } }
        public DataSnapshot data(String target) {
            synchronized (ClientScopedDataRepository.this) {
                Slot slot = slots.get(target);
                if (!current()) return new DataSnapshot(Map.of(), false, false, false, false, PREFIX + "unavailable", false);
                if (slot == null) return new DataSnapshot(Map.of(), false, false, false, false, "", false);
                boolean available = !slot.revision.isEmpty();
                boolean listed = snapshot.loaded() && (target.equals(GLOBAL) || snapshot.dimensions().contains(target));
                return new DataSnapshot(slot.values, slot.loading, available, available && listed && !slot.loading && !slot.pending,
                        slot.pending, slot.message, slot.fetched);
            }
        }
        public void addChangeListener(Runnable listener) { listeners.addIfAbsent(Objects.requireNonNull(listener)); }
        public void removeChangeListener(Runnable listener) { listeners.remove(listener); }
        public void refresh() { enqueue(CATALOG, "", Map.of(), Map.of(), null, true); }
        public void read(String target, boolean reload) { enqueue(READ, target, Map.of(), Map.of(), null, reload); }
        public void replace(String target, Map<String, Object> expected, Map<String, Object> replacement, Consumer<String> completion) {
            Objects.requireNonNull(completion);
            Map<String, Object> before, after;
            try { before = GraphMetadataDataValues.snapshot(expected); after = GraphMetadataDataValues.snapshot(replacement); }
            catch (RuntimeException invalid) { clientQueue.accept(() -> complete(completion, PREFIX + "invalid")); return; }
            enqueue(REPLACE, target, before, after, completion, false);
        }
        private void enqueue(PacketScopedDataRequest.Action action, String target, Map<String, Object> expected,
                             Map<String, Object> replacement, Consumer<String> completion, boolean reload) {
            Objects.requireNonNull(target);
            clientQueue.accept(() -> dispatch(this, action, target, expected, replacement, completion, reload));
        }
        private void notifyListeners() {
            for (Runnable listener : listeners) {
                try { listener.run(); }
                catch (RuntimeException failure) { log("Scoped data listener failed", failure); }
            }
        }
        @Override public void close() {
            clientQueue.accept(() -> {
                synchronized (ClientScopedDataRepository.this) {
                    if (closed) return;
                    closed = true; sessions.remove(this);
                    for (Pending item : List.copyOf(pending.values())) {
                        if (item.session != this || item.action == REPLACE) continue;
                        pending.remove(item.id); requests.cancel(item.id);
                    }
                    catalogRequest = 0;
                    // An accepted write retains its callback/slot until reply or timeout. Never replay it.
                    releaseClosed(this);
                }
            });
        }
    }

    private void dispatch(Session session, PacketScopedDataRequest.Action action, String target,
                          Map<String, Object> expected, Map<String, Object> replacement,
                          Consumer<String> completion, boolean reload) {
        Pending item = null;
        PacketScopedDataRequest packet = null;
        String failure = "";
        synchronized (this) {
            if (!session.current() || !connected.getAsBoolean()) failure = PREFIX + "unavailable";
            else if (action == CATALOG && session.catalogRequest != 0) return;
            else {
                Slot slot = action == CATALOG ? null : session.slots.computeIfAbsent(target, ignored -> new Slot());
                if (slot != null && (slot.loading || slot.pending)) {
                    if (action != REPLACE) return;
                    failure = PREFIX + "pending";
                } else if (action == READ && !reload && !slot.revision.isEmpty()) return;
                else if (pending.size() >= 64) failure = PREFIX + "failed";
                else if (action == REPLACE && (!session.snapshot.loaded()
                        || !target.equals(GLOBAL) && !session.snapshot.dimensions().contains(target))) failure = PREFIX + "unavailable";
                else if (action == REPLACE && (slot.revision.isEmpty()
                        || !GraphMetadataDataValues.equivalent(slot.values, expected))) failure = PREFIX + "conflict";
                else {
                    int id = requests.nextRequestId();
                    try {
                        packet = new PacketScopedDataRequest(id, action, target,
                                action == REPLACE ? slot.revision : "", replacement);
                        item = new Pending(id, session, action, target, slot, generation, completion);
                        pending.put(id, item);
                        if (action == CATALOG) {
                            session.catalogRequest = id;
                            var old = session.snapshot;
                            session.snapshot = new Snapshot(old.dimensions(), true, old.loaded(), "", old.sequence() + 1);
                        } else {
                            slot.request = id; slot.message = "";
                            slot.loading = action == READ; slot.pending = action == REPLACE;
                        }
                    } catch (RuntimeException invalid) { failure = PREFIX + "invalid"; }
                }
            }
            if (!failure.isEmpty() && action != REPLACE) markReadFailure(session, action, target, failure);
        }
        if (!failure.isEmpty()) { complete(completion, failure); session.notifyListeners(); return; }
        Pending sent = item;
        requests.register(sent.id, PacketScopedDataResult.class, reply -> finish(sent, reply),
                () -> fail(sent, PREFIX + "failed"));
        session.notifyListeners();
        // Listeners can close the owner or disconnect before dispatch. Do not send a canceled read.
        synchronized (this) { if (pending.get(sent.id) != sent) return; }
        try { transport.accept(packet); }
        catch (RuntimeException error) { fail(sent, PREFIX + "failed"); }
    }

    private void finish(Pending item, PacketScopedDataResult reply) {
        String message;
        synchronized (this) {
            if (pending.get(item.id) != item || item.generation != generation) return;
            pending.remove(item.id);
            message = reply.action() == item.action && reply.target().equals(item.target)
                    ? normalize(reply.message()) : PREFIX + "invalid";
            if (item.action == CATALOG) {
                item.session.catalogRequest = 0;
                var old = item.session.snapshot;
                item.session.snapshot = new Snapshot(message.isEmpty() ? reply.dimensions() : old.dimensions(),
                        false, message.isEmpty(), message, old.sequence() + 1);
                if (message.isEmpty()) {
                    for (var entry : item.session.slots.entrySet()) {
                        if (entry.getKey().equals(GLOBAL) || reply.dimensions().contains(entry.getKey()) || entry.getValue().pending) continue;
                        entry.getValue().revision = ""; entry.getValue().message = PREFIX + "unavailable";
                    }
                }
            } else {
                Slot slot = item.slot;
                slot.request = 0; slot.loading = slot.pending = false; slot.message = message;
                if (message.isEmpty()) {
                    slot.values = reply.values(); slot.revision = reply.revision(); slot.fetched = true;
                } else slot.revision = "";
            }
        }
        complete(item.completion, message);
        item.session.notifyListeners();
        synchronized (this) { releaseClosed(item.session); }
    }
    private void fail(Pending item, String message) {
        synchronized (this) {
            if (pending.get(item.id) != item) return;
            pending.remove(item.id); requests.cancel(item.id);
            if (item.action == CATALOG) {
                item.session.catalogRequest = 0; markReadFailure(item.session, CATALOG, "", message);
            } else {
                item.slot.request = 0; item.slot.loading = item.slot.pending = false;
                item.slot.revision = ""; item.slot.message = message;
            }
        }
        complete(item.completion, message);
        item.session.notifyListeners();
        synchronized (this) { releaseClosed(item.session); }
    }
    private void markReadFailure(Session session, PacketScopedDataRequest.Action action, String target, String message) {
        if (action == CATALOG) {
            var old = session.snapshot;
            session.snapshot = new Snapshot(old.dimensions(), false, false, message, old.sequence() + 1);
        } else {
            Slot slot = session.slots.computeIfAbsent(target, ignored -> new Slot());
            slot.revision = ""; slot.message = message; slot.loading = false;
        }
    }
    public void disconnect() {
        List<Pending> abandoned;
        List<Session> changed;
        synchronized (this) {
            generation++; requests.reset(); abandoned = List.copyOf(pending.values()); pending.clear();
            changed = List.copyOf(sessions); sessions.clear();
            for (Session session : changed) {
                session.closed = true; session.catalogRequest = 0; session.slots.clear();
                session.snapshot = new Snapshot(List.of(), false, false, PREFIX + "unavailable", session.snapshot.sequence() + 1);
            }
        }
        abandoned.forEach(item -> complete(item.completion, PREFIX + "unavailable"));
        changed.forEach(Session::notifyListeners);
        synchronized (this) {
            changed.forEach(this::releaseClosed);
            abandoned.forEach(item -> releaseClosed(item.session));
        }
    }
    private void releaseClosed(Session session) {
        if (session.closed && pending.values().stream().noneMatch(item -> item.session == session)) {
            session.slots.clear(); session.listeners.clear();
        }
    }
    private static String normalize(String message) {
        if (message == null || message.isEmpty()) return "";
        String key = message.startsWith("geometry_node.") ? message : "geometry_node." + message;
        return key.startsWith(PREFIX) ? key : PREFIX + "failed";
    }
    private static void complete(Consumer<String> completion, String message) {
        if (completion == null) return;
        try { completion.accept(message); }
        catch (RuntimeException failure) { log("Scoped data completion failed", failure); }
    }
    private static void log(String message, RuntimeException failure) {
        System.getLogger(ClientScopedDataRepository.class.getName()).log(System.Logger.Level.WARNING, message, failure);
    }
}
