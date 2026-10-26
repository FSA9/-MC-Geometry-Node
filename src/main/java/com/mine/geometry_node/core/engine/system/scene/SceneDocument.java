package com.mine.geometry_node.core.engine.system.scene;

import java.util.*;
import java.util.function.UnaryOperator;
import com.mine.geometry_node.core.engine.metadata.state.OwnerMetadata;

/** Server-thread logical state; every batch validates completely before publishing any mutation. */
public final class SceneDocument {
    public static final int MAX_OBJECTS = 256;
    public static final int MAX_TOMBSTONES = 1024;
    public static final int MAX_METADATA_BYTES = 512 * 1024;
    private final Thread ownerThread = Thread.currentThread();
    private final Map<UUID, SceneObject> objects = new LinkedHashMap<>();
    private final LinkedHashMap<UUID, Long> deleted = new LinkedHashMap<>();
    private final Map<UUID, UUID> incarnations = new HashMap<>();
    private long revision;
    private boolean metadataUpdating;
    public SceneDocument() {}
    public SceneDocument(long revision, List<SceneObject> loaded) {
        if (revision < 0 || loaded.size() > MAX_OBJECTS) throw new IllegalArgumentException("Invalid scene document");
        requireMetadataBudget(loaded);
        this.revision = revision;
        for (SceneObject object : loaded) {
            requireAuthored(object);
            SceneObjectTypes.validate(object);
            if (object.revision() > revision || objects.putIfAbsent(object.id(), object) != null)
                throw new IllegalArgumentException("Invalid persisted object identity/revision");
            incarnations.put(object.id(), UUID.randomUUID());
        }
    }
    public UUID incarnation(UUID id) { return incarnations.get(id); }
    public long revision() { return revision; }
    public List<SceneObject> objects() { return List.copyOf(objects.values()); }
    public Map<UUID, Long> deletedVersions() { return Map.copyOf(deleted); }
    public SceneObject object(UUID id) { return objects.get(id); }
    public long version(UUID id) {
        SceneObject object = objects.get(id);
        return object == null ? deleted.getOrDefault(id, -1L) : object.revision();
    }
    public boolean apply(List<SceneChange> changes) {
        if (Thread.currentThread() != ownerThread) throw new IllegalStateException("Scene mutations require the owner thread");
        if (metadataUpdating) throw new IllegalStateException("Reentrant scene mutation during metadata update");
        if (changes.isEmpty() || changes.size() > MAX_OBJECTS) throw new IllegalArgumentException("Invalid scene batch size");
        Set<UUID> seen = new HashSet<>();
        int size = objects.size();
        boolean changed = false;
        for (SceneChange change : changes) {
            if (!seen.add(change.id())) throw new IllegalArgumentException("Duplicate object in batch");
            if (version(change.id()) != change.expectedRevision()) throw new IllegalStateException("Scene changed; refresh and retry");
            SceneObject current = objects.get(change.id());
            if (change.replacement() != null) {
                requireAuthored(change.replacement());
                SceneObjectTypes.validate(change.replacement());
                if (current == null) size++;
                if (!change.replacement().sameContent(current)) changed = true;
            } else {
                if (current == null) throw new IllegalArgumentException("Object no longer exists");
                size--;
                changed = true;
            }
        }
        Map<UUID, SceneObject> proposed = new LinkedHashMap<>(objects);
        for (SceneChange change : changes) {
            if (change.replacement() == null) proposed.remove(change.id());
            else proposed.put(change.id(), change.replacement());
        }
        requireMetadataBudget(proposed.values());
        if (size > MAX_OBJECTS) throw new IllegalArgumentException("Scene object budget exceeded (256 per dimension)");
        if (!changed) return false;
        long next = Math.incrementExact(revision);
        for (SceneChange change : changes) {
            if (change.replacement() == null) {
                objects.remove(change.id());
                incarnations.remove(change.id());
                deleted.put(change.id(), next);
            } else {
                incarnations.computeIfAbsent(change.id(), ignored -> UUID.randomUUID());
                objects.put(change.id(), change.replacement().withRevision(next));
                deleted.remove(change.id());
            }
        }
        while (deleted.size() > MAX_TOMBSTONES) deleted.remove(deleted.keySet().iterator().next());
        revision = next;
        return true;
    }

    public boolean updateMetadata(UUID id, UUID incarnation, UnaryOperator<OwnerMetadata> transformation) {
        if (Thread.currentThread() != ownerThread) throw new IllegalStateException("Scene mutations require the owner thread");
        if (metadataUpdating) throw new IllegalStateException("Reentrant scene metadata update");
        SceneObject before = objects.get(id);
        if (before == null || !Objects.equals(incarnation, incarnations.get(id)))
            throw new IllegalStateException("Stale scene metadata target");
        OwnerMetadata next;
        metadataUpdating = true;
        try { next = Objects.requireNonNull(transformation.apply(before.metadata())); }
        finally { metadataUpdating = false; }
        return apply(List.of(new SceneChange(id, before.revision(), before.withMetadata(next))));
    }

    public static void requireMetadataBudget(Collection<SceneObject> objects) {
        long bytes = 0;
        for (SceneObject object : objects) {
            bytes += object.metadata().encodedSize();
            if (bytes > MAX_METADATA_BYTES) throw new IllegalArgumentException("Scene metadata exceeds 512 KiB budget");
        }
    }

    private static void requireAuthored(SceneObject object) {
        if (SceneObjectTypes.require(object.typeId()).storage() != SceneObjectTypes.Storage.AUTHORED)
            throw new IllegalArgumentException("Native objects cannot be stored in an authored scene document");
    }
}
