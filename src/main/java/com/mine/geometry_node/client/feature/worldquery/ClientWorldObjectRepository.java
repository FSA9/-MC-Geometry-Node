package com.mine.geometry_node.client.feature.worldquery;

import com.mine.geometry_node.core.engine.system.worldquery.metadata.WorldObjectDataEdits;
import com.mine.geometry_node.core.engine.metadata.value.GraphMetadataDataValues;
import com.mine.geometry_node.core.engine.system.worldquery.WorldObjectEntries;
import com.mine.geometry_node.core.engine.system.worldquery.model.WorldObjectEntry;
import com.mine.geometry_node.core.engine.system.worldquery.model.WorldObjectPage;
import com.mine.geometry_node.core.engine.system.worldquery.model.WorldObjectQuery;
import com.mine.geometry_node.core.engine.system.worldquery.metadata.WorldObjectTagEdits;
import com.mine.geometry_node.core.engine.system.worldquery.model.WorldObjectType;
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

/**
 * Cross-thread immutable mirrors. Transport and callbacks run exclusively on the Minecraft client thread.
 * Session snapshots, subscriptions and pending writes are live query state, not disposable cache entries.
 * Their owners refresh/close them; global cache clearing must not disconnect listeners or drop edit replies.
 */
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
    public record DataSnapshot(Map<String, Object> values, boolean loading, boolean available,
                               boolean editable, boolean pending, String message, boolean fetched) {
        public DataSnapshot { values = GraphMetadataDataValues.snapshot(values); }
        public DataSnapshot(Map<String, Object> values, boolean loading, boolean available,
                            boolean editable, boolean pending, String message) {
            this(values, loading, available, editable, pending, message, editable);
        }
    }
    private static final class DataSlot {
        final String key, token;
        Map<String, Object> values = Map.of();
        String revision = "", message = "";
        long requestId;
        boolean loading, pending;
        DataSlot(String key, String token) { this.key = key; this.token = token; }
    }
    private record PendingData(Session session, DataSlot slot, long sentAt, long scope, boolean edit,
                               Consumer<String> completion) {}
    private final Map<Long, PendingData> pendingData = new HashMap<>();
    private final Map<Long, Session> pending = new HashMap<>();
    private final Map<Long, Session> pendingTags = new HashMap<>();
    private final Set<Session> sessions = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Consumer<PacketWorldObjectQuery> transport;
    private final Consumer<PacketWorldObjectTagEdit> tagTransport;
    private final Consumer<PacketWorldObjectDataRequest> dataTransport;
    private final Consumer<Runnable> clientQueue;
    private final BooleanSupplier connected;
    private long nextRequest, nextSession, generation;
    private boolean initialized;
    private ClientWorldObjectRepository() {
        this(NetworkHandler::sendToServer, NetworkHandler::sendToServer, NetworkHandler::sendToServer, task -> Minecraft.getInstance().execute(task),
                () -> Minecraft.getInstance().getConnection() != null && Minecraft.getInstance().level != null);
    }
    /** Logical mirror tests use a deterministic queue and transport without initializing Minecraft. */
    ClientWorldObjectRepository(Consumer<PacketWorldObjectQuery> transport, Consumer<Runnable> clientQueue,
                                BooleanSupplier connected) {
        this(transport, packet -> { throw new IllegalStateException("No tag transport"); }, clientQueue, connected);
    }
    ClientWorldObjectRepository(Consumer<PacketWorldObjectQuery> transport, Consumer<PacketWorldObjectTagEdit> tagTransport,
                                Consumer<Runnable> clientQueue, BooleanSupplier connected) {
        this(transport, tagTransport, packet -> { throw new IllegalStateException("No data transport"); }, clientQueue, connected);
    }
    ClientWorldObjectRepository(Consumer<PacketWorldObjectQuery> transport, Consumer<PacketWorldObjectTagEdit> tagTransport,
                                Consumer<PacketWorldObjectDataRequest> dataTransport, Consumer<Runnable> clientQueue,
                                BooleanSupplier connected) {
        this.dataTransport = Objects.requireNonNull(dataTransport);
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
        com.mine.geometry_node.core.network.ClientboundPayloadRegistry.registerClientReceiver(
                PacketWorldObjectDataResult.TYPE, (packet, context) -> {
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
        PreparedQuery refresh = null;
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
                refresh = target.prepareQuery(snapshot.query(), true);
            }
        }
        // Minecraft.execute may run inline on the client thread. Never dispatch while holding
        // the repository monitor, or a slow refresh prevents replacement queries and close().
        if (refresh != null) clientQueue.accept(refresh.dispatch());
        target.notifyListeners();
    }
    public void accept(PacketWorldObjectDataResult packet, long expectedGeneration) {
        PendingData request;
        String message;
        synchronized (this) {
            if (generation != expectedGeneration) return;
            request = pendingData.remove(packet.requestId());
            if (request == null || request.session().closed || request.scope() != request.session().scopeRevision
                    || request.slot().requestId != packet.requestId()) return;
            DataSlot slot = request.slot(); slot.requestId = 0; slot.loading = slot.pending = false;
            boolean current = request.session().dataSlots.get(slot.key) == slot
                    && request.session().published.entries().stream().anyMatch(row -> row.key().equals(slot.key)
                    && slot.token.equals(row.references().get(WorldObjectDataEdits.TARGET_REFERENCE)));
            message = current ? packet.message() : WorldObjectDataEdits.PREFIX + "unavailable";
            slot.message = message;
            if (message.isEmpty()) {
                slot.values = packet.values(); slot.revision = packet.revision();
                request.session().updateDataSummary(slot);
            } else if (message.endsWith("conflict") || message.endsWith("unavailable") || message.endsWith("forbidden")) {
                slot.revision = "";
            }
        }
        request.session().notifyListeners();
        complete(request, message);
    }
    private void complete(PendingData request, String message) {
        if (request.completion() == null || request.session().closed) return;
        synchronized (this) {
            if (request.session().closed) return;
            if (request.scope() != request.session().scopeRevision) message = WorldObjectDataEdits.PREFIX + "unavailable";
        }
        try { request.completion().accept(message); }
        catch (RuntimeException failure) {
            System.getLogger(ClientWorldObjectRepository.class.getName()).log(System.Logger.Level.WARNING, "World data completion failed", failure);
        }
    }
    void disconnect() {
        List<Session> changed;
        List<PendingData> canceled;
        synchronized (this) {
            canceled = List.copyOf(pendingData.values()); pendingData.clear();
            generation++; pending.clear(); pendingTags.clear(); changed = List.copyOf(sessions);
            for (Session session : changed) {
                session.dataSlots.clear();
                session.tagRequestId = 0; session.tagMessage = WorldObjectTagEdits.PREFIX + "unavailable";
                session.requestId = 0;
                session.published = new Snapshot(session.published.query(), WorldObjectPage.empty(0, "disconnected"),
                        false, session.published.sequence() + 1);
            }
        }
        changed.forEach(Session::notifyListeners);
        canceled.forEach(request -> complete(request, WorldObjectDataEdits.PREFIX + "unavailable"));
    }
    private void expire() { expire(System.nanoTime()); }
    void expire(long now) {
        List<Session> changed = new ArrayList<>();
        List<PendingData> expiredData = new ArrayList<>();
        synchronized (this) {
            var data = pendingData.entrySet().iterator();
            while (data.hasNext()) {
                var request = data.next().getValue();
                if (now - request.sentAt() < 15_000_000_000L) continue;
                data.remove(); request.slot().requestId = 0; request.slot().loading = request.slot().pending = false;
                request.slot().message = WorldObjectDataEdits.PREFIX + "failed";
                changed.add(request.session()); expiredData.add(request);
            }
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
        expiredData.forEach(request -> complete(request, WorldObjectDataEdits.PREFIX + "failed"));
    }
    private record PreparedQuery(long id, Runnable dispatch) {}

    public final class Session implements AutoCloseable {
        private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();
        private boolean dataOnly;
        private volatile Snapshot published = new Snapshot(WorldObjectQuery.defaults(), WorldObjectPage.empty(0, ""), false, 0);
        private long requestId, sentAt, scopeRevision, tagScopeRevision, tagSentAt;
        private volatile long tagRequestId;
        private volatile String tagMessage = "";
        private WorldObjectEntry tagEntry;
        private String editedTag;
        private boolean tagAdded, retainingRows;
        private volatile boolean closed;
        private final long sessionId;
        private final LinkedHashMap<String, DataSlot> dataSlots = new LinkedHashMap<>(16, .75f, true);
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
            PacketWorldObjectTagEdit prepared = null;
            boolean accepted = false, notify = false;
            final long expectedGeneration;
            synchronized (ClientWorldObjectRepository.this) {
                if (tagRequestId != 0 || !canEditTags(entry)) return false;
                try {
                    String normalized = com.mine.geometry_node.core.metadata.TagPolicy.EXACT.normalize(tag);
                    if (entry.tags().contains(normalized) == add) {
                        notify = !tagMessage.isEmpty();
                        tagMessage = "";
                    } else {
                        prepared = new PacketWorldObjectTagEdit(++nextRequest, entry.references().get(WorldObjectTagEdits.TARGET_REFERENCE),
                                entry.key(), entry.tags(), normalized, add);
                    }
                    accepted = true;
                } catch (IllegalArgumentException invalid) {
                    tagMessage = WorldObjectTagEdits.PREFIX + "invalid";
                    notify = true;
                }
                expectedGeneration = generation;
                if (prepared != null) {
                    tagRequestId = prepared.requestId(); tagSentAt = System.nanoTime();
                    tagScopeRevision = scopeRevision; tagEntry = entry; editedTag = prepared.tag(); tagAdded = add;
                    tagMessage = WorldObjectTagEdits.PREFIX + "pending"; pendingTags.put(tagRequestId, this);
                }
            }
            // Even local validation/no-op notifications can run inline on the client thread.
            if (prepared == null) {
                if (notify) clientQueue.accept(this::notifyListeners);
                return accepted;
            }
            final PacketWorldObjectTagEdit packet = prepared;
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
        /** Passive cache read: never schedules network IO. */
        public DataSnapshot dataSnapshot(WorldObjectEntry entry) {
            synchronized (ClientWorldObjectRepository.this) {
                boolean available = dataTarget(entry);
                DataSlot slot = entry == null ? null : dataSlots.get(entry.key());
                if (slot == null || !Objects.equals(slot.token, entry.references().get(WorldObjectDataEdits.TARGET_REFERENCE)))
                    return new DataSnapshot(Map.of(), false, available, false, false, "");
                return new DataSnapshot(slot.values, slot.loading, available, available && !slot.revision.isEmpty()
                        && !entry.references().containsKey(WorldObjectDataEdits.READ_ONLY_REFERENCE)
                        && !slot.message.endsWith("forbidden") && !slot.message.endsWith("unavailable"),
                        slot.pending, slot.message, !slot.revision.isEmpty());
            }
        }
        private boolean dataTarget(WorldObjectEntry entry) {
            if (closed || entry == null || published.loading()) return false;
            String token = entry.references().get(WorldObjectDataEdits.TARGET_REFERENCE);
            if (token == null) return false;
            try { new PacketWorldObjectDataRequest(0, token, entry.key(), "", Map.of()); }
            catch (IllegalArgumentException invalid) { return false; }
            return published.entries().stream().anyMatch(row -> row.key().equals(entry.key())
                    && token.equals(row.references().get(WorldObjectDataEdits.TARGET_REFERENCE)));
        }
        /** Cached successful/in-flight reads are idempotent; failed reads can be explicitly retried. */
        public boolean requestData(WorldObjectEntry entry) {
            return prepareData(entry, null, null, null, false);
        }
        /** Explicit reload revalidates an already loaded dictionary without changing selection/query scope.
         * Duplicate in-flight reads and saves remain coalesced; a reload never races an outstanding save. */
        public boolean refreshData(WorldObjectEntry entry) {
            return prepareData(entry, null, null, null, true);
        }
        public boolean replaceData(WorldObjectEntry entry, Map<String, Object> expected,
                                   Map<String, Object> replacement, Consumer<String> completion) {
            Objects.requireNonNull(expected); Objects.requireNonNull(replacement); Objects.requireNonNull(completion);
            return prepareData(entry, expected, replacement, completion, false);
        }
        private boolean prepareData(WorldObjectEntry entry, Map<String, Object> expected,
                                    Map<String, Object> replacement, Consumer<String> completion, boolean reload) {
            final PacketWorldObjectDataRequest packet;
            final PendingData request;
            final long expectedGeneration;
            synchronized (ClientWorldObjectRepository.this) {
                if (!dataTarget(entry)) return false;
                String token = entry.references().get(WorldObjectDataEdits.TARGET_REFERENCE);
                DataSlot slot = dataSlots.get(entry.key());
                if (slot != null && !slot.token.equals(token)) { dataSlots.remove(entry.key()); slot = null; }
                boolean edit = replacement != null;
                if (edit && entry.references().containsKey(WorldObjectDataEdits.READ_ONLY_REFERENCE)) return false;
                if (!edit && slot != null && (slot.loading || slot.pending || !reload && !slot.revision.isEmpty())) return true;
                if (edit && (slot == null || slot.loading || slot.pending || slot.revision.isEmpty()
                        || !GraphMetadataDataValues.equivalent(slot.values, expected))) return false;
                if (slot == null) {
                    if (dataSlots.size() >= 16) {
                        String oldest = dataSlots.entrySet().stream().filter(item -> item.getValue().requestId == 0)
                                .map(Map.Entry::getKey).findFirst().orElse(null);
                        if (oldest == null) return false;
                        dataSlots.remove(oldest);
                    }
                    slot = new DataSlot(entry.key(), token); dataSlots.put(entry.key(), slot);
                }
                try {
                    packet = new PacketWorldObjectDataRequest(++nextRequest, token, entry.key(), edit ? slot.revision : "",
                            edit ? replacement : Map.of());
                } catch (IllegalArgumentException invalid) { return false; }
                expectedGeneration = generation;
                slot.requestId = packet.requestId(); slot.loading = !edit; slot.pending = edit;
                slot.message = edit ? WorldObjectDataEdits.PREFIX + "pending" : "";
                request = new PendingData(this, slot, System.nanoTime(), scopeRevision, edit, completion);
                pendingData.put(packet.requestId(), request);
            }
            clientQueue.accept(() -> {
                synchronized (ClientWorldObjectRepository.this) {
                    if (closed || generation != expectedGeneration || pendingData.get(packet.requestId()) != request) return;
                }
                notifyListeners();
                try {
                    synchronized (ClientWorldObjectRepository.this) {
                        if (closed || generation != expectedGeneration || pendingData.get(packet.requestId()) != request) return;
                    }
                    if (!connected.getAsBoolean()) throw new IllegalStateException("Disconnected");
                    dataTransport.accept(packet);
                } catch (RuntimeException failure) {
                    synchronized (ClientWorldObjectRepository.this) {
                        if (closed || generation != expectedGeneration || pendingData.get(packet.requestId()) != request) return;
                        pendingData.remove(packet.requestId());
                        request.slot().requestId = 0; request.slot().loading = request.slot().pending = false;
                        request.slot().message = WorldObjectDataEdits.PREFIX + "failed";
                    }
                    notifyListeners(); complete(request, WorldObjectDataEdits.PREFIX + "failed");
                }
            });
            return true;
        }
        private void updateDataSummary(DataSlot slot) {
            Snapshot before = published; WorldObjectPage page = before.page();
            List<WorldObjectEntry> rows = page.entries().stream().map(row -> {
                if (!row.key().equals(slot.key) || !slot.token.equals(row.references().get(WorldObjectDataEdits.TARGET_REFERENCE))) return row;
                Map<String, String> refs = new LinkedHashMap<>(row.references());
                refs.put(WorldObjectDataEdits.COUNT_REFERENCE, Integer.toString(slot.values.size()));
                refs.put(WorldObjectDataEdits.SUMMARY_REFERENCE, GraphMetadataDataValues.summary(slot.values));
                if (refs.equals(row.references())) return row;
                return new WorldObjectEntry(row.key(), row.provider(), row.type(), row.name(), row.dimension(), row.position(), row.bounds(),
                        row.tags(), row.source(), row.lifetime(), row.sceneObjectId(), row.entityId(), refs, row.graphs(), row.entityState());
            }).toList();
            if (rows.equals(page.entries())) return;
            try {
                published = new Snapshot(before.query(), new WorldObjectPage(page.requestId(), rows, page.truncated(), page.dimensionIds(),
                        page.types(), page.coverage(), page.message(), page.counts()), before.loading(), before.sequence() + 1);
            } catch (IllegalArgumentException pageLimit) { /* On-demand state remains valid even if a full page has no summary byte headroom. */ }
        }
        /**
         * Retains one currently authorized target for a separate edit lifetime. No query is shared:
         * replacing/closing the table query cannot cancel this editor's reads and completed writes.
         * Server-side target authorization and revisions still validate every operation.
         */
        public Session forkDataSession(WorldObjectEntry entry) {
            synchronized (ClientWorldObjectRepository.this) {
                if (!dataTarget(entry)) throw new IllegalStateException("Stale world data target");
                Session fork = newSession();
                fork.dataOnly = true;
                fork.published = new Snapshot(published.query(), new WorldObjectPage(0,
                        List.of(entry), false, List.of(), List.of(), List.of(), ""), false, 0);
                return fork;
            }
        }

        public long query(WorldObjectQuery query) {
            if (dataOnly) throw new IllegalStateException("Data edit sessions cannot replace their target");
            PreparedQuery prepared = prepareQuery(query, false);
            clientQueue.accept(prepared.dispatch());
            return prepared.id();
        }
        private PreparedQuery prepareQuery(WorldObjectQuery query, boolean retainRows) {
            Objects.requireNonNull(query);
            final long id, expectedGeneration;
            final List<PendingData> canceledData = new ArrayList<>();
            synchronized (ClientWorldObjectRepository.this) {
                if (closed) throw new IllegalStateException("World query session is closed");
                retainingRows = retainRows;
                if (!retainRows) {
                    scopeRevision++; tagMessage = "";
                    var data = pendingData.entrySet().iterator();
                    while (data.hasNext()) {
                        PendingData request = data.next().getValue();
                        if (request.session() == this) { canceledData.add(request); data.remove(); }
                    }
                    dataSlots.clear();
                }
                pending.remove(requestId); id = ++nextRequest; requestId = id; expectedGeneration = generation;
                pending.put(id, this); sentAt = System.nanoTime();
                published = new Snapshot(query, retainRows ? published.page() : WorldObjectPage.empty(id, ""), !retainRows, published.sequence() + 1);
            }
            return new PreparedQuery(id, () -> {
                canceledData.forEach(request -> complete(request, WorldObjectDataEdits.PREFIX + "unavailable"));
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
                closed = true; pendingData.values().removeIf(request -> request.session() == this); dataSlots.clear();
                pendingTags.remove(tagRequestId); tagRequestId = 0; pending.remove(requestId); sessions.remove(this); listeners.clear();
            }
        }
    }
}
