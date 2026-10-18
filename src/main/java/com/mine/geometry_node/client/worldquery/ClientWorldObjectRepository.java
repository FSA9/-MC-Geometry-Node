package com.mine.geometry_node.client.worldquery;

import com.mine.geometry_node.core.engine.system.worldquery.*;
import com.mine.geometry_node.core.network.NetworkHandler;
import com.mine.geometry_node.core.network.packet.worldquery.*;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Cross-thread immutable mirrors. Transport and callbacks run exclusively on the Minecraft client thread. */
public final class ClientWorldObjectRepository {
    public static final ClientWorldObjectRepository INSTANCE = new ClientWorldObjectRepository();
    public record Snapshot(WorldObjectQuery query, WorldObjectPage page, boolean loading, long sequence) {
        public List<WorldObjectEntry> entries() { return page.entries(); }
        public String message() { return page.message(); }
        public List<String> dimensionIds() { return page.dimensionIds(); }
        public List<WorldObjectType> types() { return page.types(); }
        public List<String> coverage() { return page.coverage(); }
        public boolean truncated() { return page.truncated(); }
    }
    private final Map<Long, Session> pending = new HashMap<>();
    private final Set<Session> sessions = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Consumer<PacketWorldObjectQuery> transport;
    private final Consumer<Runnable> clientQueue;
    private final BooleanSupplier connected;
    private long nextRequest, generation;
    private boolean initialized;
    private ClientWorldObjectRepository() {
        this(NetworkHandler::sendToServer, task -> Minecraft.getInstance().execute(task),
                () -> Minecraft.getInstance().getConnection() != null && Minecraft.getInstance().level != null);
    }
    /** Logical mirror tests use a deterministic queue and transport without initializing Minecraft. */
    ClientWorldObjectRepository(Consumer<PacketWorldObjectQuery> transport, Consumer<Runnable> clientQueue,
                                BooleanSupplier connected) {
        this.transport = Objects.requireNonNull(transport); this.clientQueue = Objects.requireNonNull(clientQueue);
        this.connected = Objects.requireNonNull(connected);
    }
    public synchronized Session newSession() {
        if (sessions.size() >= 64) throw new IllegalStateException("Too many world query sessions");
        Session session = new Session(); sessions.add(session); return session;
    }
    public synchronized void init() {
        if (initialized) return;
        initialized = true;
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> disconnect());
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> expire());
    }
    public synchronized long generation() { return generation; }
    /** Receiver captures connection generation before queuing the response to the client thread. */
    public void accept(PacketWorldObjectPage packet, long expectedGeneration) {
        Session target;
        synchronized (this) {
            if (generation != expectedGeneration) return;
            target = pending.remove(packet.page().requestId());
            if (target == null || target.closed || target.requestId != packet.page().requestId()) return;
            target.published = new Snapshot(target.published.query(), packet.page(), false, target.published.sequence() + 1);
        }
        target.notifyListeners();
    }
    void disconnect() {
        List<Session> changed;
        synchronized (this) {
            generation++; pending.clear(); changed = List.copyOf(sessions);
            for (Session session : changed) {
                session.requestId = 0;
                session.published = new Snapshot(session.published.query(), WorldObjectPage.empty(0, "disconnected"),
                        false, session.published.sequence() + 1);
            }
        }
        changed.forEach(Session::notifyListeners);
    }
    private void expire() { expire(System.nanoTime()); }
    void expire(long now) {
        List<Session> changed = new ArrayList<>();
        synchronized (this) {
            var iterator = pending.entrySet().iterator();
            while (iterator.hasNext()) {
                var session = iterator.next().getValue();
                if (now - session.sentAt < 15_000_000_000L) continue;
                iterator.remove(); session.published = new Snapshot(session.published.query(),
                        WorldObjectPage.empty(session.requestId, "request_timeout"), false, session.published.sequence() + 1);
                session.requestId = 0;
                changed.add(session);
            }
        }
        changed.forEach(Session::notifyListeners);
    }
    public final class Session implements AutoCloseable {
        private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();
        private volatile Snapshot published = new Snapshot(WorldObjectQuery.defaults(), WorldObjectPage.empty(0, ""), false, 0);
        private long requestId, sentAt;
        private volatile boolean closed;
        private Session() {}
        public Snapshot snapshot() { return published; }
        public void addChangeListener(Runnable listener) {
            Objects.requireNonNull(listener);
            synchronized (ClientWorldObjectRepository.this) { if (!closed) listeners.addIfAbsent(listener); }
        }
        public void removeChangeListener(Runnable listener) { listeners.remove(listener); }
        public long query(WorldObjectQuery query) {
            Objects.requireNonNull(query);
            final long id, expectedGeneration;
            synchronized (ClientWorldObjectRepository.this) {
                if (closed) throw new IllegalStateException("World query session is closed");
                pending.remove(requestId); id = ++nextRequest; requestId = id; expectedGeneration = generation;
                pending.put(id, this); sentAt = System.nanoTime();
                published = new Snapshot(query, WorldObjectPage.empty(id, ""), true, published.sequence() + 1);
            }
            clientQueue.accept(() -> {
                synchronized (ClientWorldObjectRepository.this) {
                    if (closed || generation != expectedGeneration || requestId != id) return;
                    if (!connected.getAsBoolean()) {
                        pending.remove(id);
                        published = new Snapshot(query, WorldObjectPage.empty(id, "disconnected"), false, published.sequence() + 1);
                    } else {
                        try { transport.accept(new PacketWorldObjectQuery(id, query)); }
                        catch (RuntimeException failure) {
                            pending.remove(id);
                            published = new Snapshot(query, WorldObjectPage.empty(id, "send_failed"), false, published.sequence() + 1);
                        }
                    }
                }
                notifyListeners();
            });
            return id;
        }
        private void notifyListeners() {
            if (closed) return;
            for (Runnable listener : listeners) {
                if (closed) return;
                if (!listeners.contains(listener)) continue;
                try { listener.run(); }
                catch (RuntimeException failure) { System.getLogger(ClientWorldObjectRepository.class.getName()).log(System.Logger.Level.WARNING, "World query listener failed", failure); }
            }
        }
        @Override public void close() {
            synchronized (ClientWorldObjectRepository.this) {
                if (closed) return;
                closed = true; pending.remove(requestId); sessions.remove(this); listeners.clear();
            }
        }
    }
}
