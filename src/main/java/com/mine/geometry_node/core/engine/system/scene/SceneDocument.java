package com.mine.geometry_node.core.engine.system.scene;

import java.util.*;

/** Server-thread logical state; every batch validates completely before publishing any mutation. */
public final class SceneDocument {
    public static final int MAX_OBJECTS = 256;
    public static final int MAX_TOMBSTONES = 1024;
    private final Map<UUID, SceneObject> objects = new LinkedHashMap<>();
    private final LinkedHashMap<UUID, Long> deleted = new LinkedHashMap<>();
    private long revision;
    public SceneDocument() {}
    public SceneDocument(long revision, List<SceneObject> loaded) {
        if (revision < 0 || loaded.size() > MAX_OBJECTS) throw new IllegalArgumentException("Invalid scene document");
        this.revision = revision;
        for (SceneObject object : loaded) {
            requireAuthored(object);
            SceneObjectTypes.validate(object);
            if (object.revision() > revision || objects.putIfAbsent(object.id(), object) != null)
                throw new IllegalArgumentException("Invalid persisted object identity/revision");
        }
    }
    public long revision() { return revision; }
    public List<SceneObject> objects() { return List.copyOf(objects.values()); }
    public Map<UUID, Long> deletedVersions() { return Map.copyOf(deleted); }
    public SceneObject object(UUID id) { return objects.get(id); }
    public long version(UUID id) {
        SceneObject object = objects.get(id);
        return object == null ? deleted.getOrDefault(id, -1L) : object.revision();
    }
    public boolean apply(List<SceneChange> changes) {
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
        if (size > MAX_OBJECTS) throw new IllegalArgumentException("Scene object budget exceeded (256 per dimension)");
        if (!changed) return false;
        long next = Math.incrementExact(revision);
        for (SceneChange change : changes) {
            if (change.replacement() == null) {
                objects.remove(change.id());
                deleted.put(change.id(), next);
            } else {
                objects.put(change.id(), change.replacement().withRevision(next));
                deleted.remove(change.id());
            }
        }
        while (deleted.size() > MAX_TOMBSTONES) deleted.remove(deleted.keySet().iterator().next());
        revision = next;
        return true;
    }

    private static void requireAuthored(SceneObject object) {
        if (SceneObjectTypes.require(object.typeId()).storage() != SceneObjectTypes.Storage.AUTHORED)
            throw new IllegalArgumentException("Native objects cannot be stored in an authored scene document");
    }
}
