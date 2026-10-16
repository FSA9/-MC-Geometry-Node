package com.mine.geometry_node.client.scene;

import com.mine.geometry_node.core.engine.system.scene.SceneChange;
import com.mine.geometry_node.core.engine.system.scene.SceneObject;
import java.util.*;

/** Acknowledged scene deltas, independent of graph history and renderer object identity. */
public final class SceneHistory {
    private static final int LIMIT = 50;
    private final Deque<Entry> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
    private final Map<UUID, Long> ownedVersions = new HashMap<>();
    public record Entry(Set<UUID> ids, Map<UUID, SceneObject> before, Map<UUID, SceneObject> after) {
        public Entry { ids = Set.copyOf(ids); before = Map.copyOf(before); after = Map.copyOf(after); }
    }
    public boolean canUndo() { return !undo.isEmpty(); }
    public boolean canRedo() { return !redo.isEmpty(); }
    public void clear() { undo.clear(); redo.clear(); ownedVersions.clear(); }
    public void acknowledgeEdit(Map<UUID, SceneObject> before, Map<UUID, SceneObject> after,
                                Set<UUID> ids, Map<UUID, Long> versions) {
        Map<UUID, SceneObject> oldValues = subset(before, ids), newValues = subset(after, ids);
        boolean changed = ids.stream().anyMatch(id -> !same(oldValues.get(id), newValues.get(id)));
        if (!changed) return;
        undo.push(new Entry(ids, oldValues, newValues));
        while (undo.size() > LIMIT) undo.removeLast();
        redo.clear();
        acknowledgeVersions(ids, versions);
    }
    public List<SceneChange> prepare(boolean undoing, Map<UUID, SceneObject> current, Map<UUID, Long> versions) {
        Entry entry = (undoing ? undo : redo).peek();
        if (entry == null) return List.of();
        Map<UUID, SceneObject> expected = undoing ? entry.after : entry.before;
        Map<UUID, SceneObject> desired = undoing ? entry.before : entry.after;
        List<SceneChange> changes = new ArrayList<>();
        for (UUID id : entry.ids) {
            long version = versions.getOrDefault(id, -1L);
            if (version != ownedVersions.getOrDefault(id, Long.MIN_VALUE) || !same(current.get(id), expected.get(id)))
                throw new IllegalStateException("Scene history conflicts with another edit");
            changes.add(new SceneChange(id, version, desired.get(id)));
        }
        return List.copyOf(changes);
    }
    /** Called only after the corresponding operation's success response, never when sending it. */
    public void acknowledgeHistory(boolean undoing, Map<UUID, Long> versions) {
        Deque<Entry> from = undoing ? undo : redo, to = undoing ? redo : undo;
        Entry entry = from.pop();
        to.push(entry);
        acknowledgeVersions(entry.ids, versions);
    }
    private void acknowledgeVersions(Set<UUID> ids, Map<UUID, Long> versions) {
        for (UUID id : ids) ownedVersions.put(id, versions.getOrDefault(id, -1L));
        Set<UUID> retained = new HashSet<>();
        undo.forEach(entry -> retained.addAll(entry.ids)); redo.forEach(entry -> retained.addAll(entry.ids));
        ownedVersions.keySet().retainAll(retained);
    }
    private static boolean same(SceneObject a, SceneObject b) { return a == null ? b == null : a.sameContent(b); }
    private static Map<UUID, SceneObject> subset(Map<UUID, SceneObject> objects, Set<UUID> ids) {
        Map<UUID, SceneObject> result = new LinkedHashMap<>();
        for (UUID id : ids) if (objects.containsKey(id)) result.put(id, objects.get(id));
        return result;
    }
}
