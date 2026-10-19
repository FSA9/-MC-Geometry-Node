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
    private final Map<Long, Session> pendingTags = new HashMap<>();
    private final Set<Session> sessions = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Consumer<PacketWorldObjectQuery> transport;
    private final Consumer<PacketWorldObjectTagEdit> tagTransport;
    private final Consumer<Runnable> clientQueue;
    private final BooleanSupplier connected;
    private long nextRequest, nextSession, generation;
    private boolean initialized;
    private ClientWorldObjectRepository() {
        this(NetworkHandler::sendToServer, NetworkHandler::sendToServer, task -> Minecraft.getInstance().execute(task),
                () -> Minecraft.getInstance().getConnection() != null && Minecraft.getInstance().level != null);
    }
    /** Logical mirror tests use a deterministic queue and transport without initializing Minecraft. */
    ClientWorldObjectRepository(Consumer<PacketWorldObjectQuery> transport, Consumer<Runnable> clientQueue,
                                BooleanSupplier connected) {
        this(transport, packet -> { throw new IllegalStateException("No tag transport"); }, clientQueue, connected);
    }
    ClientWorldObjectRepository(Consumer<PacketWorldObjectQuery> transport, Consumer<PacketWorldObjectTagEdit> tagTransport,
                                Consumer<Runnable> clientQueue, BooleanSupplier connected) {
        this.tagTransport = Objects.requireNonNull(tagTransport);
        this.transport = Objects.requireNonNull(transport); this.clientQueue = Objects.requireNonNull(clientQueue);
        this.connected = Objects.requireNonNull(connected);
    }
    public synchronized Session newSession() {
        if (sessions.size() >= 64) throw new IllegalStateException("Too many world query sessions");
        Session session = new Session(++nextSession); sessions.add(session); return session;
    }
    public synchronized void init() {
        if (initialized) return;
        initialized = true;
        com.mine.geometry_node.core.network.ClientboundPayloadRegistry.registerClientReceiver(
                PacketWorldObjectTagResult.TYPE, (packet, context) -> {
                    long expectedGeneration = generation();
                    context.queue(() -> accept(packet, expectedGeneration));
                });
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
            var page = packet.page();
            if (target.retainingRows && Set.of("permission_denied", "query_failed", "rate_limited").contains(page.message())) {
                target.tagMessage = WorldObjectTagEdits.PREFIX + (page.message().equals("permission_denied") ? "forbidden" : "failed");
                page = target.published.page();
            }
            target.published = new Snapshot(target.published.query(), page, false, target.published.sequence() + 1);
        }
        target.notifyListeners();
    }
    public void accept(PacketWorldObjectTagResult packet, long expectedGeneration) {
        Session target;
        synchronized (this) {
            if (generation != expectedGeneration) return;
            target = pendingTags.remove(packet.requestId());
            if (target == null || target.closed || target.tagRequestId != packet.requestId()) return;
            target.tagRequestId = 0;
            if (target.scopeRevision == target.tagScopeRevision) target.tagMessage = packet.message();
            if ((packet.message().isEmpty() || packet.message().equals(WorldObjectTagEdits.PREFIX + "unavailable")
                    || packet.message().equals(WorldObjectTagEdits.PREFIX + "conflict")) && target.scopeRevision == target.tagScopeRevision) {
                var snapshot = target.published; var page = snapshot.page();
                var entries = page.entries().stream().map(entry -> {
                    if (!packet.message().isEmpty() || !entry.equals(target.tagEntry)) return entry;
                    var tags = target.tagAdded
                            ? com.mine.geometry_node.core.metadata.TagSet.add(entry.tags(), List.of(target.editedTag), com.mine.geometry_node.core.metadata.TagPolicy.EXACT)
                            : com.mine.geometry_node.core.metadata.TagSet.remove(entry.tags(), List.of(target.editedTag), com.mine.geometry_node.core.metadata.TagPolicy.EXACT);
                    return WorldObjectEntries.withTags(entry, tags);
                }).toList();
                // A tag can grow a page that was already at its byte limit. Keep rows and let the bounded query rebuild it.
                try {
                    target.published = new Snapshot(snapshot.query(), new WorldObjectPage(page.requestId(), entries, page.truncated(),
                            page.dimensionIds(), page.types(), page.coverage(), page.message(), page.counts()), false, snapshot.sequence() + 1);
                } catch (IllegalArgumentException pageLimit) { /* Refresh below still runs. */ }
                target.query(snapshot.query(), true);
            }
        }
        target.notifyListeners();
    }
    void disconnect() {
        List<Session> changed;
        synchronized (this) {
            generation++; pending.clear(); pendingTags.clear(); changed = List.copyOf(sessions);
            for (Session session : changed) {
                session.tagRequestId = 0; session.tagMessage = WorldObjectTagEdits.PREFIX + "unavailable";
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
            var tags = pendingTags.entrySet().iterator();
            while (tags.hasNext()) {
                Session session = tags.next().getValue();
                if (now - session.tagSentAt < 15_000_000_000L) continue;
                tags.remove(); session.tagRequestId = 0;
                if (session.scopeRevision == session.tagScopeRevision) session.tagMessage = WorldObjectTagEdits.PREFIX + "failed";
                changed.add(session);
            }
            var iterator = pending.entrySet().iterator();
            while (iterator.hasNext()) {
                var session = iterator.next().getValue();
                if (now - session.sentAt < 15_000_000_000L) continue;
                iterator.remove(); session.published = new Snapshot(session.published.query(),
                        session.retainingRows ? session.published.page() : WorldObjectPage.empty(session.requestId, "request_timeout"),
                        false, session.published.sequence() + 1);
                if (session.retainingRows) session.tagMessage = WorldObjectTagEdits.PREFIX + "failed";
                session.requestId = 0;
                changed.add(session);
            }
        }
        changed.forEach(Session::notifyListeners);
    }
    public final class Session implements AutoCloseable {
        private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();
        private volatile Snapshot published = new Snapshot(WorldObjectQuery.defaults(), WorldObjectPage.empty(0, ""), false, 0);
        private long requestId, sentAt, scopeRevision, tagScopeRevision, tagSentAt;
        private volatile long tagRequestId;
        private volatile String tagMessage = "";
        private WorldObjectEntry tagEntry;
        private String editedTag;
        private boolean tagAdded, retainingRows;
        private volatile boolean closed;
        private final long sessionId;
        private Session(long sessionId) { this.sessionId = sessionId; }
        public Snapshot snapshot() { return published; }
        public void addChangeListener(Runnable listener) {
            Objects.requireNonNull(listener);
            synchronized (ClientWorldObjectRepository.this) { if (!closed) listeners.addIfAbsent(listener); }
        }
        public void removeChangeListener(Runnable listener) { listeners.remove(listener); }
        /** Current row has a server-advertised capability. Stays true during edits; disable controls with isTagUpdatePending(). */
        public boolean canEditTags(WorldObjectEntry entry) {
            synchronized (ClientWorldObjectRepository.this) {
                if (closed || entry == null || published.loading()
                        || !published.entries().contains(entry) || !entry.references().containsKey(WorldObjectTagEdits.TARGET_REFERENCE)) return false;
                try {
                    new PacketWorldObjectTagEdit(0, entry.references().get(WorldObjectTagEdits.TARGET_REFERENCE), entry.key(), entry.tags(), "x", true);
                    return true;
                } catch (IllegalArgumentException invalid) { return false; }
            }
        }
        public boolean isTagUpdatePending() { return tagRequestId != 0; }
        /** Empty after success; otherwise database.tag_update_{pending,failed,conflict,forbidden,unavailable,invalid}. */
        public String tagUpdateMessage() { return tagMessage; }
        /** Returns true for a local no-op or when accepted for dispatch; false for invalid/stale rows or an already pending edit.
         * Completion uses change listeners; success refreshes the original query while retaining visible rows. */
        public boolean addTag(WorldObjectEntry entry, String tag) { return editTag(entry, tag, true); }
        public boolean removeTag(WorldObjectEntry entry, String tag) { return editTag(entry, tag, false); }
        private boolean editTag(WorldObjectEntry entry, String tag, boolean add) {
            final PacketWorldObjectTagEdit packet;
            final long expectedGeneration;
            synchronized (ClientWorldObjectRepository.this) {
                if (tagRequestId != 0 || !canEditTags(entry)) return false;
                try {
                    String normalized = com.mine.geometry_node.core.metadata.TagPolicy.EXACT.normalize(tag);
                    if (entry.tags().contains(normalized) == add) {
                        if (!tagMessage.isEmpty()) {
                            tagMessage = ""; clientQueue.accept(this::notifyListeners);
                        }
                        return true;
                    }
                    packet = new PacketWorldObjectTagEdit(++nextRequest, entry.references().get(WorldObjectTagEdits.TARGET_REFERENCE),
                            entry.key(), entry.tags(), normalized, add);
                } catch (IllegalArgumentException invalid) {
                    tagMessage = WorldObjectTagEdits.PREFIX + "invalid";
                    clientQueue.accept(this::notifyListeners); return false;
                }
                expectedGeneration = generation; tagRequestId = packet.requestId(); tagSentAt = System.nanoTime();
                tagScopeRevision = scopeRevision; tagEntry = entry; editedTag = packet.tag(); tagAdded = add;
                tagMessage = WorldObjectTagEdits.PREFIX + "pending"; pendingTags.put(tagRequestId, this);
            }
            clientQueue.accept(() -> {
                synchronized (ClientWorldObjectRepository.this) {
                    if (closed || generation != expectedGeneration || tagRequestId != packet.requestId()) return;
                }
                notifyListeners();
                synchronized (ClientWorldObjectRepository.this) {
                    if (closed || generation != expectedGeneration || tagRequestId != packet.requestId()) return;
                }
                try {
                    if (!connected.getAsBoolean()) throw new IllegalStateException("Disconnected");
                    tagTransport.accept(packet);
                } catch (RuntimeException failure) {
                    synchronized (ClientWorldObjectRepository.this) {
                        if (closed || generation != expectedGeneration || tagRequestId != packet.requestId()
                                || pendingTags.get(packet.requestId()) != this) return;
                        pendingTags.remove(packet.requestId()); tagRequestId = 0;
                        if (scopeRevision == tagScopeRevision) tagMessage = WorldObjectTagEdits.PREFIX + "failed";
                    }
                }
                notifyListeners();
            });
            return true;
        }
        public long query(WorldObjectQuery query) { return query(query, false); }
        private long query(WorldObjectQuery query, boolean retainRows) {
            Objects.requireNonNull(query);
            final long id, expectedGeneration;
            synchronized (ClientWorldObjectRepository.this) {
                if (closed) throw new IllegalStateException("World query session is closed");
                retainingRows = retainRows;
                if (!retainRows) { scopeRevision++; tagMessage = ""; }
                pending.remove(requestId); id = ++nextRequest; requestId = id; expectedGeneration = generation;
                pending.put(id, this); sentAt = System.nanoTime();
                published = new Snapshot(query, retainRows ? published.page() : WorldObjectPage.empty(id, ""), !retainRows, published.sequence() + 1);
            }
            clientQueue.accept(() -> {
                final PacketWorldObjectQuery packet;
                synchronized (ClientWorldObjectRepository.this) {
                    if (closed || generation != expectedGeneration || requestId != id) return;
                    packet = new PacketWorldObjectQuery(sessionId, id, query);
                }
                // Transport may block. A newer query/close/disconnect must still acquire the repository lock.
                String failureMessage = null;
                try {
                    if (!connected.getAsBoolean()) failureMessage = "disconnected";
                    else transport.accept(packet);
                } catch (RuntimeException failure) {
                    failureMessage = "send_failed";
                }
                synchronized (ClientWorldObjectRepository.this) {
                    if (closed || generation != expectedGeneration || requestId != id) return;
                    if (failureMessage != null) {
                        // A synchronous reply may already have completed this request before transport threw.
                        if (pending.get(id) != this) return;
                        pending.remove(id);
                        published = new Snapshot(query, retainRows ? published.page() : WorldObjectPage.empty(id, failureMessage),
                                false, published.sequence() + 1);
                        if (retainRows) tagMessage = WorldObjectTagEdits.PREFIX
                                + (failureMessage.equals("disconnected") ? "unavailable" : "failed");
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
                closed = true; pendingTags.remove(tagRequestId); tagRequestId = 0; pending.remove(requestId); sessions.remove(this); listeners.clear();
            }
        }
    }
}
