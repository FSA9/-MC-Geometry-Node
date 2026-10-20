package com.mine.geometry_node.client.scene;

import com.mine.geometry_node.client.history.*;
import com.mine.geometry_node.client.scene.history.SceneHistoryAdapter;
import com.mine.geometry_node.client.scene.history.SceneHistoryAdapter.Delta;
import com.mine.geometry_node.core.engine.system.scene.SceneChange;
import com.mine.geometry_node.core.engine.system.scene.SceneObject;
import com.mine.geometry_node.core.network.packet.scene.PacketSceneEdit;
import com.mine.geometry_node.core.network.packet.scene.PacketSceneSnapshot;
import dev.architectury.networking.NetworkManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import java.util.*;
import java.util.function.Consumer;

/** Immutable cross-thread UI/render mirror. Every mutation is acknowledged by the server before entering history. */
public final class ClientSceneService implements HistoryTarget {
    private static final System.Logger LOGGER = System.getLogger(ClientSceneService.class.getName());
    public static final ClientSceneService INSTANCE = new ClientSceneService();
    public record Snapshot(String dimension, long revision, List<SceneObject> objects, Set<UUID> selected,
                           UUID active, boolean canEdit, boolean pending, String message, long worldGeneration) {
        public Snapshot { objects = List.copyOf(objects); selected = Set.copyOf(selected); }
        public SceneObject object(UUID id) {
            if (id == null) return null;
            for (SceneObject object : objects) if (id.equals(object.id())) return object;
            return null;
        }
    }
    private volatile Snapshot published = new Snapshot("", 0, List.of(), Set.of(), null, false, false, "", 0);
    private volatile Snapshot confirmed = published;
    private final SceneHistoryAdapter history = new SceneHistoryAdapter("scene:unbound");
    private final List<HistorySubscription> historyListeners = new ArrayList<>();
    private HistoryState lastHistoryState;
    private boolean notifyingHistory, finalizingHistory;
    private SceneClipboard clipboard = SceneClipboard.EMPTY;
    private final com.mine.geometry_node.core.engine.system.scene.volume.SceneCollisionRuntime collisions = new com.mine.geometry_node.core.engine.system.scene.volume.SceneCollisionRuntime();
    private final Consumer<PacketSceneEdit> transport;
    private final Map<String, BackendSnapshot> backendSnapshots = new LinkedHashMap<>();
    private Map<UUID, SceneObject> objects = Map.of();
    private Map<UUID, Long> versions = Map.of();
    private Set<UUID> selected = Set.of();
    private UUID active;
    private Map<UUID, SceneObject> previewBase = Map.of();
    private Map<UUID, SceneObject> preview = Map.of();
    private Object previewOwner;
    private Pending pending;
    private Object world;
    private String dimension = "", message = "";
    private long revision, generation, ticks, lastSyncTick;
    private boolean canEdit, initialized, received;
    private ClientSceneService() { this(null); }
    /** Optional transport keeps the logical mirror usable without a Minecraft process. */
    ClientSceneService(Consumer<PacketSceneEdit> transport) {
        this.transport = transport;
        history.subscribe(ignored -> publishHistory());
    }
    public Snapshot snapshot() { return published; }
    public Snapshot confirmedSnapshot() { return confirmed; }
    /** UI and automation query the same complete-selection policy as mutation entry points. */
    public synchronized SceneEditCapabilities.Availability availability(SceneEditCapabilities.Action action) {
        return availability(action, false);
    }
    public synchronized SceneEditCapabilities.Availability availability(SceneEditCapabilities.Action action, boolean unresolvedTargets) {
        Collection<SceneObject> values = action == SceneEditCapabilities.Action.PASTE ? clipboard.objects()
                : objects.values().stream().filter(object -> selected.contains(object.id())).toList();
        var result = SceneEditCapabilities.evaluate(action, values, unresolvedTargets);
        if (!result.allowed()) return result;
        if (!received || dimension.isEmpty() || (action != SceneEditCapabilities.Action.COPY && !canEdit))
            return new SceneEditCapabilities.Availability(SceneEditCapabilities.Reason.UNAVAILABLE);
        if (action != SceneEditCapabilities.Action.COPY && (pending != null || previewOwner != null
                || history.state().phase() != HistoryState.Phase.IDLE))
            return new SceneEditCapabilities.Availability(SceneEditCapabilities.Reason.BUSY);
        return result;
    }
    private static void requireCapability(SceneEditCapabilities.Action action, Collection<SceneObject> values) {
        var availability = SceneEditCapabilities.evaluate(action, values, false);
        if (!availability.allowed()) throw new IllegalArgumentException(availability.message());
    }
    @Override public synchronized HistoryState historyState() {
        HistoryState state = history.state();
        String unavailable = !received || !canEdit || dimension.isEmpty() ? "Scene editing is unavailable or permission was denied"
                : previewOwner != null ? "Cancel the active scene preview first" : "";
        if (state.phase() != HistoryState.Phase.IDLE) return state;
        return state.withAvailability(
                unavailable.isEmpty() ? history.blockedReason(true, objects, versions) : unavailable,
                unavailable.isEmpty() ? history.blockedReason(false, objects, versions) : unavailable);
    }
    public synchronized boolean canUndo() { return historyState().canUndo(); }
    public synchronized boolean canRedo() { return historyState().canRedo(); }
    @Override public synchronized AutoCloseable subscribeHistory(Consumer<HistoryState> listener) {
        requireMutationAllowed();
        Objects.requireNonNull(listener);
        HistorySubscription subscription = new HistorySubscription(listener);
        historyListeners.add(subscription);
        boolean previous = notifyingHistory;
        notifyingHistory = true;
        try { notifyHistory(listener, historyState()); }
        finally { notifyingHistory = previous; }
        return subscription;
    }
    private void publishHistory() {
        if (finalizingHistory) return;
        HistoryState state = historyState();
        if (state.equals(lastHistoryState)) return;
        lastHistoryState = state;
        boolean previous = notifyingHistory;
        notifyingHistory = true;
        try {
            for (var subscription : List.copyOf(historyListeners)) {
                if (subscription.active) notifyHistory(subscription.listener, state);
            }
        } finally { notifyingHistory = previous; }
    }
    private void notifyHistory(Consumer<HistoryState> listener, HistoryState state) {
        try { listener.accept(state); }
        catch (Throwable failure) { LOGGER.log(System.Logger.Level.WARNING, "Scene history observer failed", failure); }
    }
    private final class HistorySubscription implements AutoCloseable {
        private final Consumer<HistoryState> listener;
        private boolean active = true;
        private HistorySubscription(Consumer<HistoryState> listener) { this.listener = listener; }
        @Override public void close() {
            synchronized (ClientSceneService.this) { active = false; historyListeners.remove(this); }
        }
    }
    private void requireMutationAllowed() {
        if (notifyingHistory || finalizingHistory) throw new IllegalStateException("Scene history notification reentry");
    }
    public synchronized void init() {
        if (initialized) return;
        initialized = true;
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> tick());
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> reset(null));
    }
    private synchronized void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (world != level) reset(level);
        ticks++;
        if (level == null || minecraft.getConnection() == null) return;
        collisions.synchronize(level, confirmed.objects());
        // Join packets may arrive before ClientLevel exists; retry a bounded sync until one is accepted.
        if ((!received && ticks - lastSyncTick >= 20) || ticks - lastSyncTick >= 200) {
            lastSyncTick = ticks;
            send(new PacketSceneEdit(UUID.randomUUID(), dimension, List.of()), generation);
        }
        syncSubscribedBackends();
        retryPending();
    }
    synchronized void syncSubscribedBackends() {
        for (var backend : SceneEditBackends.all()) if (backend.subscribed().getAsBoolean() && ticks % backend.syncIntervalTicks() == 0)
            send(new PacketSceneEdit(UUID.randomUUID(), dimension, List.of()), generation, backend.id());
    }
    synchronized void advancePendingTick() { ticks++; retryPending(); }
    private void retryPending() {
        if (pending == null || ticks - pending.sentTick < 100) return;
        Pending attempt = pending;
        attempt.sentTick = ticks;
        message = "Waiting for server confirmation";
        publish();
        history.markUnknown(attempt.ticket, message);
        // UNKNOWN still owns the reservation; retry precisely the same request/token.
        if (pending == attempt) {
            try { send(attempt.packet, generation, attempt.backend); }
            catch (RuntimeException ignored) { /* Still UNKNOWN; retry the same reservation next time. */ }
        }
    }
    private synchronized void reset(ClientLevel level) {
        bindWorld(level, level == null ? "" : level.dimension().identifier().toString());
    }
    synchronized void bindWorld(Object identity, String dimensionId) {
        requireMutationAllowed();
        if (identity == world && dimension.equals(dimensionId)) return;
        if (world instanceof ClientLevel previous) collisions.clear(previous);
        generation++;
        world = identity;
        dimension = dimensionId;
        revision = 0; received = canEdit = false; message = "";
        backendSnapshots.clear();
        objects = Map.of(); versions = Map.of(); selected = Set.of(); active = null;
        previewBase = Map.of(); preview = Map.of(); previewOwner = null; pending = null; clipboard = SceneClipboard.EMPTY;
        lastSyncTick = ticks - 20;
        finalizingHistory = true;
        try {
            publish();
            history.reset("scene:" + generation + ":" + dimension);
        } finally { finalizingHistory = false; }
        publishHistory();
    }
    public void accept(PacketSceneSnapshot packet) { accept(packet, snapshot().worldGeneration()); }
    public synchronized void accept(PacketSceneSnapshot packet, long expectedGeneration) {
        ClientLevel level = Minecraft.getInstance().level;
        if (world != level) reset(level);
        acceptSnapshot(packet, expectedGeneration);
    }
    /** Network entry point for registered authorities; world identity is checked before UI publication. */
    public synchronized void acceptBackendSnapshot(String backend, PacketSceneSnapshot packet, long expectedGeneration) {
        ClientLevel level = Minecraft.getInstance().level;
        if (world != level) reset(level);
        acceptSnapshot(packet, expectedGeneration, backend);
    }
    synchronized void acceptSnapshot(PacketSceneSnapshot packet, long expectedGeneration) {
        acceptSnapshot(packet, expectedGeneration, SceneEditBackends.AUTHORED);
    }
    synchronized void acceptSnapshot(PacketSceneSnapshot packet, long expectedGeneration, String backend) {
        requireMutationAllowed();
        if (world == null || expectedGeneration != generation || !dimension.equals(packet.dimension())) return;
        if (!backend.equals(SceneEditBackends.AUTHORED) && SceneEditBackends.all().stream().noneMatch(b -> b.id().equals(backend))) return;
        if (packet.objects().stream().anyMatch(object -> !SceneEditBackends.accepts(backend, object))) return;
        Pending completed = pending != null && pending.backend.equals(backend)
                && pending.packet.requestId().equals(packet.requestId()) ? pending : null;
        Map<UUID, SceneObject> incoming = index(packet.objects());
        Map<UUID, Long> incomingVersions = versionIndex(packet.objects(), packet.deletedVersions());
        if (completed != null) {
            pending = null;
            if (packet.success()) {
                if (completed.selection != null) {
                    selected = completed.selection.ids(); active = completed.selection.active();
                }
                message = "";
            } else message = packet.message();
        }
        // Cached idempotent acknowledgements may describe an older revision than a later broadcast.
        BackendSnapshot previous = backendSnapshots.get(backend);
        if (previous == null || packet.revision() >= previous.revision()) {
            backendSnapshots.put(backend, new BackendSnapshot(packet.revision(), incoming, incomingVersions));
            Map<UUID, SceneObject> merged = new LinkedHashMap<>();
            Map<UUID, Long> mergedVersions = new HashMap<>();
            for (var entry : backendSnapshots.values()) {
                entry.objects().forEach((id, object) -> {
                    if (merged.putIfAbsent(id, object) != null) throw new IllegalStateException("Duplicate backend object identity");
                });
                mergedVersions.putAll(entry.versions());
            }
            objects = Collections.unmodifiableMap(merged); versions = Map.copyOf(mergedVersions);
            revision = Math.max(revision, packet.revision());
            canEdit = packet.canEdit(); received = true;
            if (completed == null && !packet.success()) message = packet.message();
            if (pending != null && !pending.visualObjects.isEmpty()) {
                boolean conflict = pending.visualObjects.keySet().stream().anyMatch(id ->
                        objects.get(id) == null || objects.get(id).revision() != pending.before.get(id).revision());
                if (conflict || !canEdit) {
                    pending.visualObjects = Map.of();
                    message = "Scene changed while waiting for the move confirmation";
                }
            }
            if (!previewBase.isEmpty()) {
                boolean conflict = previewBase.values().stream().anyMatch(base ->
                        objects.get(base.id()) == null || objects.get(base.id()).revision() != base.revision());
                if (conflict || !canEdit) {
                    previewBase = Map.of(); preview = Map.of(); previewOwner = null; message = "Drag cancelled because the scene changed";
                }
            }
        }
        Set<UUID> surviving = new LinkedHashSet<>(selected); surviving.retainAll(objects.keySet()); selected = Set.copyOf(surviving);
        if (active == null || !selected.contains(active)) active = selected.stream().findFirst().orElse(null);
        // Keep the reservation occupied through mirror/selection publication. Observers and
        // operation completion must see the final domain state, including for a cached old ACK.
        finalizingHistory = true;
        try {
            publish();
            if (completed != null) {
                if (!packet.success()) history.fail(completed.ticket, HistoryResult.Status.REJECTED, packet.message());
                else if (completed.ticket.kind() == HistoryOperation.Kind.EDIT)
                    history.acknowledgeEdit(completed.ticket, completed.before, incoming, completed.ids,
                            completed.packet.changes().stream().collect(java.util.stream.Collectors.toMap(
                                    SceneChange::id, SceneChange::expectedRevision)), incomingVersions);
                else history.acknowledgeReplay(completed.ticket, incomingVersions);
            }
        } finally { finalizingHistory = false; }
        publishHistory();
    }
    public synchronized void select(Set<UUID> ids, UUID activeId) {
        requireMutationAllowed();
        Set<UUID> valid = new LinkedHashSet<>(ids); valid.retainAll(objects.keySet()); selected = Set.copyOf(valid);
        active = activeId != null && selected.contains(activeId) ? activeId : valid.stream().findFirst().orElse(null);
        publish();
    }
    public HistoryOperation createPointLight(Vec3 position) { return createPointLight(snapshot().worldGeneration(), position); }
    public synchronized HistoryOperation createPointLight(long expectedGeneration, Vec3 position) {
        return create(expectedGeneration, SceneObject.POINT_LIGHT, position);
    }
    public synchronized HistoryOperation create(long expectedGeneration, String typeId, Vec3 position) {
        return executeEdit(expectedGeneration, "Create scene object", () -> {
            var type = com.mine.geometry_node.core.engine.system.scene.SceneObjectTypes.require(typeId);
            if (!type.creatable()) throw new IllegalArgumentException(
                    new SceneEditCapabilities.Availability(SceneEditCapabilities.Reason.UNSUPPORTED).message());
            SceneObject object = type.create(UUID.randomUUID(), position);
            String name = net.minecraft.network.chat.Component.translatable(type.labelKey()).getString();
            if (!name.isBlank() && name.length() <= 96)
                object = object.withProperties(name, object.color(), object.enabled(), object.parameters());
            requireCapability(SceneEditCapabilities.Action.CREATE, List.of(object));
            return new Edit(List.of(new SceneChange(object.id(), -1, object)),
                    new Selection(Set.of(object.id()), object.id()), Map.of());
        });
    }
    public HistoryOperation update(SceneObject replacement) { return update(snapshot().worldGeneration(), replacement); }
    public synchronized HistoryOperation update(long expectedGeneration, SceneObject replacement) {
        return executeEdit(expectedGeneration, "Update scene object", () -> new Edit(
                List.of(new SceneChange(replacement.id(), replacement.revision(), replacement)), null, Map.of()));
    }
    /** Copy confirmed immutable values; later source edits/deletion cannot change the clipboard. */
    public synchronized void copySelected() {
        requireMutationAllowed();
        var availability = availability(SceneEditCapabilities.Action.COPY);
        if (!availability.allowed()) { message = availability.message(); publish(); return; }
        List<SceneObject> values = objects.values().stream().filter(object -> selected.contains(object.id())).toList();
        clipboard = new SceneClipboard(values, active);
        message = ""; publish();
    }
    public synchronized HistoryOperation paste() {
        return executeEdit(generation, "Paste scene objects", () -> {
            if (clipboard.objects().isEmpty()) {
                clipboardMessage("empty");
                return new Edit(List.of(), null, Map.of());
            }
            requireCapability(SceneEditCapabilities.Action.PASTE, clipboard.objects());
            String backend = SceneEditBackends.owner(clipboard.objects().getFirst());
            long existing = objects.values().stream().filter(object -> SceneEditBackends.owner(object).equals(backend)).count();
            if (existing + clipboard.objects().size() > com.mine.geometry_node.core.engine.system.scene.SceneDocument.MAX_OBJECTS) {
                clipboardMessage("budget");
                throw new IllegalArgumentException(message);
            }
            SceneClipboard copies = clipboard.duplicate();
            List<SceneChange> changes = new ArrayList<>();
            Set<UUID> ids = new LinkedHashSet<>();
            for (SceneObject object : copies.objects()) {
                com.mine.geometry_node.core.engine.system.scene.SceneObjectTypes.validate(object);
                changes.add(new SceneChange(object.id(), -1, object));
                ids.add(object.id());
            }
            return new Edit(changes, new Selection(ids, copies.active()), Map.of());
        });
    }
    private void clipboardMessage(String suffix) {
        message = net.minecraft.network.chat.Component.translatable("geometry_node.game_view.scene.clipboard." + suffix).getString();
        publish();
    }
    public synchronized HistoryOperation deleteSelected() {
        return executeEdit(generation, "Delete scene objects", () -> {
            if (!selected.isEmpty()) requireCapability(SceneEditCapabilities.Action.DELETE,
                    selected.stream().map(objects::get).toList());
            return new Edit(selected.stream().map(id -> new SceneChange(id, versions.getOrDefault(id, -1L), null)).toList(), null, Map.of());
        });
    }
    public synchronized boolean beginPreview(Set<UUID> ids) {
        Map<UUID, Long> expected = new LinkedHashMap<>();
        for (UUID id : ids) expected.put(id, versions.getOrDefault(id, -1L));
        return beginPreview(this, generation, expected);
    }
    /** Checks server versions atomically with taking ownership of a logical preview. */
    public synchronized boolean beginPreview(Object owner, long expectedGeneration, Map<UUID, Long> expectedRevisions) {
        return beginPreview(owner, expectedGeneration, expectedRevisions, null);
    }
    /** Presented-image gestures also validate full contents while acquiring ownership.
     * A cancelled position/rotation/scale preview retains the same server revision. */
    public synchronized boolean beginPreview(Object owner, long expectedGeneration, Map<UUID, Long> expectedRevisions,
                                              Map<UUID, SceneObject> expectedObjects) {
        Objects.requireNonNull(owner);
        if (!editable(expectedGeneration) || expectedRevisions.isEmpty() || !previewBase.isEmpty()) return false;
        Map<UUID, SceneObject> base = new LinkedHashMap<>();
        for (var entry : expectedRevisions.entrySet()) {
            SceneObject object = objects.get(entry.getKey());
            if (object == null || object.revision() != entry.getValue()) return false;
            if (expectedObjects != null && !object.sameContent(expectedObjects.get(entry.getKey()))) return false;
            base.put(entry.getKey(), object);
        }
        var availability = SceneEditCapabilities.evaluate(SceneEditCapabilities.Action.PROPERTIES, base.values(), false);
        if (!availability.allowed()) { message = availability.message(); publish(); return false; }
        previewBase = Map.copyOf(base); preview = Map.of(); previewOwner = owner;
        publish(); return true;
    }
    public synchronized boolean previewActive(Object owner) { return previewOwner == owner && !previewBase.isEmpty(); }
    public synchronized void previewPositions(Object owner, Map<UUID, Vec3> positions) {
        requireMutationAllowed();
        if (previewActive(owner)) applyPreviewPositions(positions);
    }
    public synchronized void previewPositions(Map<UUID, Vec3> positions) {
        requireMutationAllowed();
        if (previewActive(this)) applyPreviewPositions(positions);
    }
    private void applyPreviewPositions(Map<UUID, Vec3> positions) {
        Map<UUID, SceneObject> next = new LinkedHashMap<>();
        for (var entry : positions.entrySet()) {
            SceneObject base = previewBase.get(entry.getKey());
            if (base == null) throw new IllegalArgumentException("Object is not part of this drag");
            SceneObject moved = com.mine.geometry_node.core.engine.system.scene.SceneObjectCapabilities.transformed(
                    base, entry.getValue(), base.rotation(), base.scale());
            com.mine.geometry_node.core.engine.system.scene.SceneObjectTypes.validate(moved);
            next.put(entry.getKey(), moved);
        }
        preview = Map.copyOf(next); publish();
    }
    public synchronized void previewObjects(Object owner, Map<UUID,SceneObject> values) {
        requireMutationAllowed();
        if(!previewActive(owner))return;
        for(var entry:values.entrySet()){
            SceneObject base=previewBase.get(entry.getKey()), value=entry.getValue();
            if(base==null||!com.mine.geometry_node.core.engine.system.scene.SceneObjectCapabilities.transformed(
                    base,value.position(),value.rotation(),value.scale()).equals(value))throw new IllegalArgumentException("Not a transform of preview base");
            com.mine.geometry_node.core.engine.system.scene.SceneObjectTypes.validate(value);
        }
        preview=Map.copyOf(values);publish();
    }
    public synchronized HistoryOperation commitPreview() { return commitPreview(this); }
    public synchronized HistoryOperation commitPreview(Object owner) {
        requireMutationAllowed();
        if (!previewActive(owner)) return completed(HistoryOperation.Kind.EDIT, HistoryResult.Status.NO_CHANGE, "Preview is not owned by caller");
        return executeEdit(generation, "Transform scene objects", () -> {
            List<SceneChange> changes = new ArrayList<>();
            Map<UUID, SceneObject> visualObjects = new LinkedHashMap<>();
            for (var entry : preview.entrySet()) {
                SceneObject base = previewBase.get(entry.getKey());
                if (!base.sameContent(entry.getValue())) {
                    changes.add(new SceneChange(base.id(), base.revision(), entry.getValue()));
                    visualObjects.put(base.id(), entry.getValue());
                }
            }
            previewBase = Map.of(); preview = Map.of(); previewOwner = null;
            return new Edit(changes, null, Map.copyOf(visualObjects));
        }, owner);
    }
    public synchronized void cancelPreview(Object owner) {
        requireMutationAllowed();
        if (previewActive(owner)) { previewBase = Map.of(); preview = Map.of(); previewOwner = null; publish(); }
    }
    public synchronized void cancelPreview() { cancelPreview(this); }
    @Override public synchronized HistoryOperation undo() { return submitHistory(true); }
    @Override public synchronized HistoryOperation redo() { return submitHistory(false); }
    private HistoryOperation submitHistory(boolean undoing) {
        requireMutationAllowed();
        HistoryOperation.Kind kind = undoing ? HistoryOperation.Kind.UNDO : HistoryOperation.Kind.REDO;
        if (history.state().phase() != HistoryState.Phase.IDLE)
            return completed(kind, HistoryResult.Status.REJECTED, "Waiting for server confirmation");
        HistorySession.Ticket<Delta> ticket = history.beginReplay(undoing);
        if (ticket == null) return completed(kind, HistoryResult.Status.NO_CHANGE, "");
        if (!editable(generation, true) || previewOwner != null) {
            if (previewOwner != null) { message = "Cancel the active scene preview first"; publish(); }
            failAttempt(ticket, HistoryResult.Status.REJECTED, message);
            return ticket.operation();
        }
        try { submit(history.prepare(ticket, objects, versions), ticket, null, Map.of()); }
        catch (IllegalArgumentException failure) { failAttempt(ticket, HistoryResult.Status.REJECTED, failure.getMessage()); }
        catch (IllegalStateException failure) { failAttempt(ticket, HistoryResult.Status.CONFLICT, failure.getMessage()); }
        catch (RuntimeException failure) { failAttempt(ticket, HistoryResult.Status.FAILED, failure.getMessage()); }
        return ticket.operation();
    }
    private boolean editable(long expectedGeneration) { return editable(expectedGeneration, false); }
    private boolean editable(long expectedGeneration, boolean reserved) {
        requireMutationAllowed();
        if (generation != expectedGeneration) { message = "Scene scope changed"; return false; }
        if (!received || !canEdit || dimension.isEmpty()) { message = "Scene editing is unavailable or permission was denied"; publish(); return false; }
        if (pending != null || (!reserved && history.state().phase() != HistoryState.Phase.IDLE)) {
            message = "Waiting for server confirmation"; publish(); return false;
        }
        return true;
    }
    private HistoryOperation executeEdit(long expectedGeneration, String label, java.util.function.Supplier<Edit> prepare) {
        return executeEdit(expectedGeneration, label, prepare, null);
    }
    private HistoryOperation executeEdit(long expectedGeneration, String label, java.util.function.Supplier<Edit> prepare, Object owner) {
        requireMutationAllowed();
        if (history.state().phase() != HistoryState.Phase.IDLE)
            return completed(HistoryOperation.Kind.EDIT, HistoryResult.Status.REJECTED, "Waiting for server confirmation");
        HistorySession.Ticket<Delta> ticket = history.beginEdit(UUID.randomUUID().toString(), label);
        try {
            if (!editable(expectedGeneration, true) || (previewOwner != null && previewOwner != owner)) {
                if (previewOwner != null && previewOwner != owner) message = "Cancel the active scene preview first";
                failAttempt(ticket, HistoryResult.Status.REJECTED, message);
            } else {
                Edit edit = prepare.get();
                if (edit.changes().isEmpty()) {
                    finalizingHistory = true;
                    try { publish(); history.noChange(ticket); }
                    finally { finalizingHistory = false; }
                    publishHistory();
                }
                else submit(edit.changes(), ticket, edit.selection(), edit.visualObjects());
            }
        } catch (IllegalArgumentException failure) { failAttempt(ticket, HistoryResult.Status.REJECTED, failure.getMessage()); }
        catch (RuntimeException failure) { failAttempt(ticket, HistoryResult.Status.FAILED, failure.getMessage()); }
        return ticket.operation();
    }
    private void failAttempt(HistorySession.Ticket<Delta> ticket, HistoryResult.Status status, String reason) {
        if (pending != null && pending.ticket == ticket) pending = null;
        message = reason == null ? "Scene edit failed" : reason;
        finalizingHistory = true;
        try { publish(); history.fail(ticket, status, message); }
        finally { finalizingHistory = false; }
        publishHistory();
    }
    private HistoryOperation completed(HistoryOperation.Kind kind, HistoryResult.Status status, String reason) {
        HistoryState state = history.state();
        return HistoryOperation.completed(kind, new HistoryResult(status, reason, state.lastChangeId(), state.revision()));
    }
    private void submit(List<SceneChange> changes, HistorySession.Ticket<Delta> ticket, Selection selection,
                        Map<UUID, SceneObject> visualObjects) {
        Set<String> backends = new HashSet<>();
        for (SceneChange change : changes) {
            SceneObject object = change.replacement() != null ? change.replacement() : objects.get(change.id());
            if (object == null) throw new IllegalArgumentException("Object no longer exists");
            // Undoing deletion recreates an absent object against a positive tombstone revision.
            var mutation = change.replacement() == null ? SceneEditBackends.Mutation.DELETE
                    : change.expectedRevision() < 0 || !objects.containsKey(change.id()) ? SceneEditBackends.Mutation.CREATE : SceneEditBackends.Mutation.UPDATE;
            if (!SceneEditBackends.supports(object, mutation)) throw new IllegalArgumentException(
                    new SceneEditCapabilities.Availability(SceneEditCapabilities.Reason.UNSUPPORTED).message());
            backends.add(SceneEditBackends.owner(object));
        }
        if (backends.size() != 1) throw new IllegalArgumentException(
                new SceneEditCapabilities.Availability(SceneEditCapabilities.Reason.MIXED_BACKENDS).message());
        String backend = backends.iterator().next();
        PacketSceneEdit packet = new PacketSceneEdit(ticket.operation().id(), dimension, changes);
        Set<UUID> ids = new LinkedHashSet<>(); changes.forEach(change -> ids.add(change.id()));
        Pending attempt = new Pending(backend, packet, ticket, objects, Set.copyOf(ids), selection, ticks, Map.copyOf(visualObjects));
        pending = attempt;
        message = ""; publish();
        try { send(packet, generation, backend); }
        catch (RuntimeException failure) {
            // A transport can throw after submitting. Retain ownership until the request is resolved.
            if (pending == attempt) {
                message = "Scene transport failed; awaiting confirmation"; publish();
                history.markUnknown(ticket, message);
            }
        }
    }
    private void send(PacketSceneEdit packet, long expectedGeneration) {
        send(packet, expectedGeneration, SceneEditBackends.AUTHORED);
    }
    private void send(PacketSceneEdit packet, long expectedGeneration, String backend) {
        if (transport != null) {
            if (generation == expectedGeneration) transport.accept(packet);
            return;
        }
        Minecraft.getInstance().execute(() -> {
            synchronized (this) {
                Minecraft minecraft = Minecraft.getInstance();
                if (generation != expectedGeneration || minecraft.level != world || minecraft.getConnection() == null) return;
                SceneEditBackends.send(backend, packet);
            }
        });
    }
    private void publish() {
        confirmed = new Snapshot(dimension, revision, List.copyOf(objects.values()), selected, active,
                canEdit, pending != null, message, generation);
        Map<UUID, SceneObject> visualObjects = pending != null && !pending.visualObjects.isEmpty() ? pending.visualObjects : preview;
        if (visualObjects.isEmpty()) published = confirmed;
        else {
            List<SceneObject> rendered = objects.values().stream()
                    .map(object -> visualObjects.containsKey(object.id()) ? visualObjects.get(object.id()) : object).toList();
            published = new Snapshot(dimension, revision, rendered, selected, active, canEdit, pending != null, message, generation);
        }
        publishHistory();
    }
    private static Map<UUID, SceneObject> index(List<SceneObject> values) {
        Map<UUID, SceneObject> result = new LinkedHashMap<>();
        for (SceneObject object : values) if (result.putIfAbsent(object.id(), object) != null) throw new IllegalArgumentException("Duplicate scene object ID");
        return Collections.unmodifiableMap(result);
    }
    private static Map<UUID, Long> versionIndex(List<SceneObject> values, Map<UUID, Long> deleted) {
        Map<UUID, Long> result = new HashMap<>(deleted); values.forEach(object -> result.put(object.id(), object.revision()));
        return Map.copyOf(result);
    }
    private record BackendSnapshot(long revision, Map<UUID, SceneObject> objects, Map<UUID, Long> versions) {}
    private record Selection(Set<UUID> ids, UUID active) {
        private Selection { ids = Set.copyOf(ids); }
    }
    private record Edit(List<SceneChange> changes, Selection selection, Map<UUID, SceneObject> visualObjects) {}
    private static final class Pending {
        final String backend;
        final PacketSceneEdit packet; final HistorySession.Ticket<Delta> ticket; final Map<UUID, SceneObject> before;
        final Set<UUID> ids; final Selection selection; long sentTick;
        Map<UUID, SceneObject> visualObjects;
        Pending(String backend, PacketSceneEdit packet, HistorySession.Ticket<Delta> ticket, Map<UUID, SceneObject> before, Set<UUID> ids, Selection selection, long sentTick,
                Map<UUID, SceneObject> visualObjects) {
            this.backend = backend; this.packet = packet; this.ticket = ticket; this.before = before; this.ids = ids; this.selection = selection;
            this.sentTick = sentTick; this.visualObjects = visualObjects;
        }
    }
}
