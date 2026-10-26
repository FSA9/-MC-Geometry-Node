package com.mine.geometry_node.client.feature.entity.index;

import com.mine.geometry_node.core.network.ClientboundPayloadRegistry;
import com.mine.geometry_node.core.network.NetworkHandler;
import com.mine.geometry_node.core.network.packet.entityindex.PacketEntityIndexRequest;
import com.mine.geometry_node.core.network.packet.entityindex.PacketEntityIndexResult;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** Connection-owned registration commands, independent of any table or entity edit draft. */
public final class ClientEntityIndex {
    public static final ClientEntityIndex INSTANCE = new ClientEntityIndex(NetworkHandler::sendToServer,
            task -> Minecraft.getInstance().execute(task), () -> Minecraft.getInstance().getConnection() != null, System::nanoTime);
    private static final int MAX_PENDING = 16;
    private static final long TIMEOUT_NANOS = 15_000_000_000L, REQUEST_GAP_NANOS = 125_000_000L;
    public record Reply(boolean registered, long revision, String message) {
        public boolean success() { return message.isEmpty(); }
    }
    public record Request(CompletableFuture<Reply> result, Runnable cancel) implements AutoCloseable {
        @Override public void close() { cancel.run(); }
    }
    private record Pending(PacketEntityIndexRequest packet, CompletableFuture<Reply> result, long generation, long deadline) {}
    private final Map<Long, Pending> pending = new LinkedHashMap<>();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private final Consumer<PacketEntityIndexRequest> transport;
    private final Consumer<Runnable> clientQueue;
    private final BooleanSupplier connected;
    private final LongSupplier clock;
    private long nextId, generation, inFlight, nextDispatch;
    private boolean coolingDown, initialized;

    ClientEntityIndex(Consumer<PacketEntityIndexRequest> transport, Consumer<Runnable> clientQueue,
                      BooleanSupplier connected, LongSupplier clock) {
        this.transport = Objects.requireNonNull(transport); this.clientQueue = Objects.requireNonNull(clientQueue);
        this.connected = Objects.requireNonNull(connected); this.clock = Objects.requireNonNull(clock);
    }
    public synchronized void init() {
        if (initialized) return;
        initialized = true;
        ClientboundPayloadRegistry.registerClientReceiver(PacketEntityIndexResult.TYPE, (packet, context) -> {
            long expected = generation();
            context.queue(() -> accept(packet, expected));
        });
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> disconnect());
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> expire());
    }
    public void addChangeListener(Runnable listener) { listeners.add(Objects.requireNonNull(listener)); }
    public void removeChangeListener(Runnable listener) { listeners.remove(listener); }
    public Request read(String dimension, UUID entity) { return request(dimension, entity, false, false, 0); }
    public Request setRegistered(String dimension, UUID entity, boolean registered, long expectedRevision) {
        return request(dimension, entity, true, registered, expectedRevision);
    }
    private Request request(String dimension, UUID entity, boolean edit, boolean registered, long revision) {
        final long id, expected;
        var future = new CompletableFuture<Reply>();
        synchronized (this) {
            id = ++nextId;
            var packet = new PacketEntityIndexRequest(id, dimension, entity, edit, registered, revision);
            if (pending.size() >= MAX_PENDING) {
                future.complete(error("busy")); return new Request(future, () -> {});
            }
            expected = generation;
            pending.put(id, new Pending(packet, future, expected, clock.getAsLong() + TIMEOUT_NANOS));
        }
        try { clientQueue.accept(() -> dispatch(expected)); }
        catch (RuntimeException rejected) { fail(id, "failed", false); }
        // Submitted writes belong to the connection. Hiding a view must not suppress their reply or refresh.
        return new Request(future, edit ? () -> {} : () -> cancelRead(id));
    }
    public synchronized long generation() { return generation; }
    private void dispatch(long expected) {
        Pending item;
        synchronized (this) {
            if (generation != expected || inFlight != 0 || pending.isEmpty()
                    || coolingDown && clock.getAsLong() - nextDispatch < 0) return;
            item = pending.values().iterator().next();
            if (clock.getAsLong() - item.deadline() >= 0) return;
            inFlight = item.packet().requestId();
        }
        if (!connected.getAsBoolean()) { fail(item.packet().requestId(), "disconnected", false); return; }
        try { transport.accept(item.packet()); }
        catch (RuntimeException rejected) { fail(item.packet().requestId(), "failed", true); }
    }
    public void accept(PacketEntityIndexResult packet, long expectedGeneration) {
        Pending item;
        synchronized (this) {
            if (generation != expectedGeneration || packet.requestId() != inFlight) return;
            item = pending.remove(packet.requestId());
            if (item == null || item.generation() != generation) return;
            release(packet.requestId(), true);
        }
        item.result().complete(new Reply(packet.registered(), packet.revision(), packet.message()));
        if (item.packet().edit() && packet.message().isEmpty()) notifyListeners();
    }
    private void cancelRead(long id) {
        Pending item;
        synchronized (this) {
            item = pending.get(id);
            if (id != inFlight) pending.remove(id);
        }
        if (item != null) item.result().cancel(false);
    }
    private void fail(long id, String reason, boolean mayHaveSent) {
        Pending item;
        synchronized (this) { item = pending.remove(id); release(id, mayHaveSent); }
        if (item != null) item.result().complete(error(reason));
    }
    private void release(long id, boolean mayHaveSent) {
        if (inFlight != id) return;
        inFlight = 0;
        if (mayHaveSent) { coolingDown = true; nextDispatch = clock.getAsLong() + REQUEST_GAP_NANOS; }
    }
    void expire() {
        var expired = new ArrayList<Pending>();
        long expected;
        synchronized (this) {
            long now = clock.getAsLong();
            pending.values().removeIf(item -> {
                if (now - item.deadline() < 0) return false;
                expired.add(item); release(item.packet().requestId(), true); return true;
            });
            expected = generation;
        }
        expired.forEach(item -> item.result().complete(error("timeout")));
        dispatch(expected);
    }
    public void disconnect() {
        List<Pending> old;
        synchronized (this) {
            generation++; old = List.copyOf(pending.values()); pending.clear();
            inFlight = 0; coolingDown = false;
        }
        old.forEach(item -> item.result().complete(error("disconnected")));
        notifyListeners();
    }
    private void notifyListeners() {
        for (Runnable listener : listeners) {
            try { listener.run(); }
            catch (RuntimeException failure) {
                System.getLogger(ClientEntityIndex.class.getName()).log(System.Logger.Level.WARNING, "Entity index listener failed", failure);
            }
        }
    }
    private static Reply error(String reason) { return new Reply(false, 0, reason); }
}
