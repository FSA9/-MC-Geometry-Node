package com.mine.geometry_node.client.scene;

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
public final class ClientSceneService {
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
    private final SceneHistory history = new SceneHistory();
    private final com.mine.geometry_node.core.engine.system.scene.volume.SceneCollisionRuntime collisions = new com.mine.geometry_node.core.engine.system.scene.volume.SceneCollisionRuntime();
    private final Consumer<PacketSceneEdit> transport;
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
    ClientSceneService(Consumer<PacketSceneEdit> transport) { this.transport = transport; }
    public Snapshot snapshot() { return published; }
    public Snapshot confirmedSnapshot() { return confirmed; }
    public synchronized boolean canUndo() { return canEdit && pending == null && history.canUndo(); }
    public synchronized boolean canRedo() { return canEdit && pending == null && history.canRedo(); }
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
        if (pending != null && ticks - pending.sentTick >= 100) {
            // Reuse the operation ID. A lost acknowledgement must not create a second object/history entry.
            pending.sentTick = ticks;
            send(pending.packet, generation);
            message = "Waiting for server confirmation";
            publish();
        }
    }
    private synchronized void reset(ClientLevel level) {
        bindWorld(level, level == null ? "" : level.dimension().identifier().toString());
    }
    synchronized void bindWorld(Object identity, String dimensionId) {
        if (identity == world && dimension.equals(dimensionId)) return;
        if (world instanceof ClientLevel previous) collisions.clear(previous);
        generation++;
        world = identity;
        dimension = dimensionId;
        revision = 0; received = canEdit = false; message = "";
        objects = Map.of(); versions = Map.of(); selected = Set.of(); active = null;
        previewBase = Map.of(); preview = Map.of(); previewOwner = null; pending = null; history.clear();
        lastSyncTick = ticks - 20;
        publish();
    }
    public void accept(PacketSceneSnapshot packet) { accept(packet, snapshot().worldGeneration()); }
    public synchronized void accept(PacketSceneSnapshot packet, long expectedGeneration) {
        ClientLevel level = Minecraft.getInstance().level;
        if (world != level) reset(level);
        acceptSnapshot(packet, expectedGeneration);
    }
    synchronized void acceptSnapshot(PacketSceneSnapshot packet, long expectedGeneration) {
        if (world == null || expectedGeneration != generation || !dimension.equals(packet.dimension())) return;
        Pending completed = pending != null && pending.packet.requestId().equals(packet.requestId()) ? pending : null;
        Map<UUID, SceneObject> incoming = index(packet.objects());
        Map<UUID, Long> incomingVersions = versionIndex(packet.objects(), packet.deletedVersions());
        if (completed != null) {
            pending = null;
            if (packet.success()) {
                if (completed.kind == Kind.EDIT) history.acknowledgeEdit(completed.before, incoming, completed.ids, incomingVersions);
                else history.acknowledgeHistory(completed.kind == Kind.UNDO, incomingVersions);
                if (completed.selectCreated != null && incoming.containsKey(completed.selectCreated)) {
                    selected = Set.of(completed.selectCreated); active = completed.selectCreated;
                }
                message = "";
            } else message = packet.message();
        }
        // Cached idempotent acknowledgements may describe an older revision than a later broadcast.
        if (!received || packet.revision() >= revision) {
            objects = incoming; versions = incomingVersions; revision = packet.revision();
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
        publish();
    }
    public synchronized void select(Set<UUID> ids, UUID activeId) {
        Set<UUID> valid = new LinkedHashSet<>(ids); valid.retainAll(objects.keySet()); selected = Set.copyOf(valid);
        active = activeId != null && selected.contains(activeId) ? activeId : valid.stream().findFirst().orElse(null);
        publish();
    }
    public void createPointLight(Vec3 position) { createPointLight(snapshot().worldGeneration(), position); }
    public synchronized void createPointLight(long expectedGeneration, Vec3 position) {
        if (!editable(expectedGeneration)) return;
        create(expectedGeneration, SceneObject.POINT_LIGHT, position);
    }
    public synchronized void create(long expectedGeneration,String typeId,Vec3 position){
        if (!editable(expectedGeneration)) return;
        var type = com.mine.geometry_node.core.engine.system.scene.SceneObjectTypes.require(typeId);
        SceneObject object = type.create(UUID.randomUUID(),position);
        String name = net.minecraft.network.chat.Component.translatable(type.labelKey()).getString();
        if (!name.isBlank() && name.length() <= 96)
            object = object.withProperties(name, object.color(), object.enabled(), object.parameters());
        submit(List.of(new SceneChange(object.id(), -1, object)), Kind.EDIT, object.id());
    }
    public void update(SceneObject replacement) { update(snapshot().worldGeneration(), replacement); }
    public synchronized void update(long expectedGeneration, SceneObject replacement) {
        if (!editable(expectedGeneration)) return;
        submit(List.of(new SceneChange(replacement.id(), replacement.revision(), replacement)), Kind.EDIT, null);
    }
    public synchronized void deleteSelected() {
        if (!editable(generation) || selected.isEmpty()) return;
        List<SceneChange> changes = selected.stream().map(id -> new SceneChange(id, versions.getOrDefault(id, -1L), null)).toList();
        submit(changes, Kind.EDIT, null);
    }
    public synchronized boolean beginPreview(Set<UUID> ids) {
        Map<UUID, Long> expected = new LinkedHashMap<>();
        for (UUID id : ids) expected.put(id, versions.getOrDefault(id, -1L));
        return beginPreview(this, generation, expected);
    }
    /** Validates the actually presented frame, atomically with taking ownership of the gesture. */
    public synchronized boolean beginPreview(Object owner, long expectedGeneration, Map<UUID, Long> expectedRevisions) {
        return beginPreview(owner, expectedGeneration, expectedRevisions, null);
    }
    /** Also guards a displayed, cancelled-preview image whose object revision has not changed. */
    public synchronized boolean beginPreview(Object owner, long expectedGeneration, Map<UUID, Long> expectedRevisions,
                                              Map<UUID, Vec3> expectedPositions) {
        Objects.requireNonNull(owner);
        if (!editable(expectedGeneration) || expectedRevisions.isEmpty() || !previewBase.isEmpty()) return false;
        Map<UUID, SceneObject> base = new LinkedHashMap<>();
        for (var entry : expectedRevisions.entrySet()) {
            SceneObject object = objects.get(entry.getKey());
            if (object == null || object.revision() != entry.getValue()) return false;
            if (expectedPositions != null && !object.position().equals(expectedPositions.get(entry.getKey()))) return false;
            base.put(entry.getKey(), object);
        }
        previewBase = Map.copyOf(base); preview = Map.of(); previewOwner = owner;
        publish(); return true;
    }
    public synchronized boolean previewActive(Object owner) { return previewOwner == owner && !previewBase.isEmpty(); }
    public synchronized void previewPositions(Object owner, Map<UUID, Vec3> positions) {
        if (previewActive(owner)) previewPositions(positions);
    }
    public synchronized void previewPositions(Map<UUID, Vec3> positions) {
        if (previewBase.isEmpty()) return;
        Map<UUID, SceneObject> next = new LinkedHashMap<>();
        for (var entry : positions.entrySet()) {
            SceneObject base = previewBase.get(entry.getKey());
            if (base == null) throw new IllegalArgumentException("Object is not part of this drag");
            next.put(entry.getKey(), base.withPosition(entry.getValue()));
        }
        preview = Map.copyOf(next); publish();
    }
    public synchronized void previewObjects(Object owner, Map<UUID,SceneObject> values) {
        if(!previewActive(owner))return;
        for(var entry:values.entrySet()){
            SceneObject base=previewBase.get(entry.getKey()), value=entry.getValue();
            if(base==null||!base.withTransform(value.position(),value.rotation(),value.scale()).equals(value))throw new IllegalArgumentException("Not a transform of preview base");
            com.mine.geometry_node.core.engine.system.scene.SceneObjectTypes.validate(value);
        }
        preview=Map.copyOf(values);publish();
    }
    public synchronized void commitPreview() {
        if (previewBase.isEmpty()) return;
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
        if (editable(generation) && !changes.isEmpty()) submit(changes, Kind.EDIT, null, visualObjects);
        else publish();
    }
    public synchronized void commitPreview(Object owner) { if (previewActive(owner)) commitPreview(); }
    public synchronized void cancelPreview(Object owner) { if (previewActive(owner)) cancelPreview(); }
    public synchronized void cancelPreview() { previewBase = Map.of(); preview = Map.of(); previewOwner = null; publish(); }
    public synchronized void undo() { submitHistory(true); }
    public synchronized void redo() { submitHistory(false); }
    private void submitHistory(boolean undoing) {
        if (!editable(generation)) return;
        try {
            List<SceneChange> changes = history.prepare(undoing, objects, versions);
            if (!changes.isEmpty()) submit(changes, undoing ? Kind.UNDO : Kind.REDO, null);
        } catch (IllegalStateException failure) { message = failure.getMessage(); publish(); }
    }
    private boolean editable(long expectedGeneration) {
        if (generation != expectedGeneration) return false;
        if (!received || !canEdit || dimension.isEmpty()) { message = "Scene editing is unavailable or permission was denied"; publish(); return false; }
        if (pending != null) { message = "Waiting for server confirmation"; publish(); return false; }
        return true;
    }
    private void submit(List<SceneChange> changes, Kind kind, UUID selectCreated) {
        submit(changes, kind, selectCreated, Map.of());
    }
    private void submit(List<SceneChange> changes, Kind kind, UUID selectCreated, Map<UUID, SceneObject> visualObjects) {
        previewBase = Map.of(); preview = Map.of(); previewOwner = null;
        PacketSceneEdit packet = new PacketSceneEdit(UUID.randomUUID(), dimension, changes);
        Set<UUID> ids = new LinkedHashSet<>(); changes.forEach(change -> ids.add(change.id()));
        pending = new Pending(packet, kind, objects, Set.copyOf(ids), selectCreated, ticks, Map.copyOf(visualObjects));
        message = ""; publish(); send(packet, generation);
    }
    private void send(PacketSceneEdit packet, long expectedGeneration) {
        if (transport != null) {
            if (generation == expectedGeneration) transport.accept(packet);
            return;
        }
        Minecraft.getInstance().execute(() -> {
            synchronized (this) {
                Minecraft minecraft = Minecraft.getInstance();
                if (generation != expectedGeneration || minecraft.level != world || minecraft.getConnection() == null) return;
                NetworkManager.sendToServer(packet);
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
    private enum Kind { EDIT, UNDO, REDO }
    private static final class Pending {
        final PacketSceneEdit packet; final Kind kind; final Map<UUID, SceneObject> before;
        final Set<UUID> ids; final UUID selectCreated; long sentTick;
        Map<UUID, SceneObject> visualObjects;
        Pending(PacketSceneEdit packet, Kind kind, Map<UUID, SceneObject> before, Set<UUID> ids, UUID selectCreated, long sentTick,
                Map<UUID, SceneObject> visualObjects) {
            this.packet = packet; this.kind = kind; this.before = before; this.ids = ids; this.selectCreated = selectCreated;
            this.sentTick = sentTick; this.visualObjects = visualObjects;
        }
    }
}
