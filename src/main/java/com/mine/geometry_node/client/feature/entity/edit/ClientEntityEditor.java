package com.mine.geometry_node.client.feature.entity.edit;

import com.mine.geometry_node.core.engine.metadata.GraphMetadataDataValues;
import com.mine.geometry_node.core.network.ClientboundPayloadRegistry;
import com.mine.geometry_node.core.network.NetworkHandler;
import com.mine.geometry_node.core.network.packet.entityeditor.PacketEntityEditorRequest;
import com.mine.geometry_node.core.network.packet.entityeditor.PacketEntityEditorResult;
import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** Bounded connection-owned FIFO. Reads and writes share one serial, rate-limited transport. */
public final class ClientEntityEditor {
    public static final ClientEntityEditor INSTANCE = new ClientEntityEditor(
            ClientEntityEditor::sendChecked, task -> Minecraft.getInstance().execute(task),
            () -> Minecraft.getInstance().getConnection() != null, System::nanoTime);
    private static final long TIMEOUT_NANOS = 15_000_000_000L;
    private static final int MAX_PENDING = 16;
    private static final long REQUEST_GAP_NANOS = 125_000_000L;
    public record Reply(Map<String, Object> values, String revision, String message) {
        public Reply { values = GraphMetadataDataValues.snapshot(values); }
        public boolean success() { return message.isEmpty(); }
    }
    public record Request(CompletableFuture<Reply> result, Runnable cancel) implements AutoCloseable {
        @Override public void close() { cancel.run(); }
    }
    private record Pending(PacketEntityEditorRequest packet, CompletableFuture<Reply> result,
                           long generation, long deadline) {}
    private final Map<Long, Pending> pending = new LinkedHashMap<>();
    private final Consumer<PacketEntityEditorRequest> transport;
    private final Consumer<Runnable> clientQueue;
    private final BooleanSupplier connected;
    private final LongSupplier clock;
    private long nextId, generation, inFlight;
    private long nextDispatch;
    private boolean coolingDown;
    private boolean initialized;

    private static final class RejectedPayload extends RuntimeException {}
    private static void sendChecked(PacketEntityEditorRequest packet) {
        var minecraft = Minecraft.getInstance();
        if (packet.edit() && minecraft.level == null) throw new IllegalStateException("No client level");
        sendChecked(packet, minecraft.level == null ? null : minecraft.level.registryAccess(), NetworkHandler::sendToServer);
    }
    static void sendChecked(PacketEntityEditorRequest packet, HolderLookup.Provider registries,
                            Consumer<PacketEntityEditorRequest> send) {
        if (packet.edit()) {
            try {
                // Encoding on Netty's queue is too late to report an oversize edit to this window.
                // Preflight on the Minecraft queue so malformed/oversize data never drops the connection.
                GraphMetadataDataValues.encode(packet.replacement(), registries, GraphMetadataDataValues.MAX_WRITE_BYTES);
            } catch (IllegalArgumentException invalid) { throw new RejectedPayload(); }
        }
        send.accept(packet);
    }

    ClientEntityEditor(Consumer<PacketEntityEditorRequest> transport, Consumer<Runnable> clientQueue,
                       BooleanSupplier connected, LongSupplier clock) {
        this.transport = Objects.requireNonNull(transport); this.clientQueue = Objects.requireNonNull(clientQueue);
        this.connected = Objects.requireNonNull(connected); this.clock = Objects.requireNonNull(clock);
    }
    public synchronized void init() {
        if (initialized) return;
        initialized = true;
        ClientboundPayloadRegistry.registerClientReceiver(PacketEntityEditorResult.TYPE, (packet, context) -> {
            long expected = generation();
            context.queue(() -> accept(packet, expected));
        });
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> disconnect());
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> expire());
    }
    public Request read(String dimension, UUID entity) { return request(dimension, entity, "", Map.of()); }
    public Request apply(String dimension, UUID entity, String revision, Map<String, Object> replacement) {
        if (revision == null || revision.isEmpty()) throw new IllegalArgumentException("Missing entity edit revision");
        return request(dimension, entity, revision, replacement);
    }
    private Request request(String dimension, UUID entity, String revision, Map<String, Object> replacement) {
        final long id;
        CompletableFuture<Reply> future = new CompletableFuture<>();
        final long expected;
        synchronized (this) {
            id = ++nextId;
            var packet = new PacketEntityEditorRequest(id, dimension, entity, revision, replacement);
            if (pending.size() >= MAX_PENDING) {
                future.complete(error("busy")); return new Request(future, () -> {});
            }
            expected = generation;
            pending.put(id, new Pending(packet, future, generation, clock.getAsLong() + TIMEOUT_NANOS));
        }
        try { clientQueue.accept(() -> dispatch(expected)); }
        catch (RuntimeException rejected) { fail(id, "failed", false); }
        return new Request(future, () -> cancel(id));
    }
    private void dispatch(long expected) {
        Pending item;
        synchronized (this) {
            if (generation != expected || inFlight != 0 || pending.isEmpty()) return;
            if (coolingDown && clock.getAsLong() - nextDispatch < 0) return;
            item = pending.values().iterator().next();
            if (clock.getAsLong() - item.deadline >= 0) return; // expire() owns timeout completion.
            inFlight = item.packet.requestId();
        }
        if (!connected.getAsBoolean()) { fail(item.packet.requestId(), "disconnected", false); return; }
        try { transport.accept(item.packet); }
        catch (RejectedPayload rejected) { fail(item.packet.requestId(), "payload", false); }
        catch (RuntimeException rejected) { fail(item.packet.requestId(), "failed", true); }
    }
    public synchronized long generation() { return generation; }
    public void accept(PacketEntityEditorResult packet, long expectedGeneration) {
        Pending item;
        synchronized (this) {
            if (generation != expectedGeneration || packet.requestId() != inFlight) return;
            item = pending.remove(packet.requestId());
            if (item == null || item.generation != generation) return;
            release(packet.requestId(), true);
        }
        item.result.complete(new Reply(packet.values(), packet.revision(), packet.message()));
    }
    private void cancel(long id) {
        Pending item;
        synchronized (this) {
            item = pending.get(id);
            // A cancelled read already sent still occupies the serial slot until reply/timeout.
            if (id != inFlight) pending.remove(id);
        }
        if (item != null) item.result.cancel(false);
    }
    private void fail(long id, String reason, boolean mayHaveSent) {
        Pending item;
        synchronized (this) {
            item = pending.remove(id);
            release(id, mayHaveSent);
        }
        if (item != null) item.result.complete(error(reason));
    }
    private void release(long id, boolean mayHaveSent) {
        if (inFlight != id) return;
        inFlight = 0;
        // Wait after the reply, so server processing is spaced even with variable network latency.
        if (mayHaveSent) { coolingDown = true; nextDispatch = clock.getAsLong() + REQUEST_GAP_NANOS; }
    }
    void expire() {
        List<Pending> expired = new ArrayList<>();
        long expected;
        synchronized (this) {
            long now = clock.getAsLong();
            pending.values().removeIf(item -> {
                if (now - item.deadline < 0) return false;
                expired.add(item); release(item.packet.requestId(), true); return true;
            });
            expected = generation;
        }
        expired.forEach(item -> item.result.complete(error("timeout")));
        dispatch(expected);
    }
    public void disconnect() {
        List<Pending> old;
        synchronized (this) {
            generation++; old = List.copyOf(pending.values()); pending.clear();
            inFlight = 0; coolingDown = false;
        }
        old.forEach(item -> item.result.complete(error("disconnected")));
    }
    private static Reply error(String reason) { return new Reply(Map.of(), "", "geometry_node.entity_editor.error." + reason); }
}
