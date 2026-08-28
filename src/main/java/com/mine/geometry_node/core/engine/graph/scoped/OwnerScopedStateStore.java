package com.mine.geometry_node.core.engine.graph.scoped;

import com.mine.geometry_node.core.engine.graph.value.GraphValueCodecRegistry;
import com.mine.geometry_node.core.node.port.PortType;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Typed OWNER blackboard partition serialized independently from blueprint attributes. */
public final class OwnerScopedStateStore {
    private static final int HARD_MAX_RECORDS = ScopedStateServerConfig.HARD_MAX_ENTRIES;
    private static final int HARD_MAX_TOTAL_RECORDS =
            HARD_MAX_RECORDS * ScopedStateNamespace.values().length;
    private final Map<StateKey, StoredEntry> entries = new LinkedHashMap<>();
    private final Map<StateKey, ScopedStateChange> changes = new LinkedHashMap<>();
    private long revision;

    public @Nullable ScopedStateEntry get(String name, HolderLookup.Provider registries) {
        return get(ScopedStateNamespace.PUBLIC, name, registries);
    }

    public @Nullable ScopedStateEntry get(ScopedStateNamespace namespace, String name,
                                          HolderLookup.Provider registries) {
        StoredEntry stored = entries.get(new StateKey(namespace, name));
        if (stored == null) return null;
        Object value = GraphValueCodecRegistry.fromTag(stored.value(), registries);
        if (value == null) {
            throw new ScopedStateAccessException(
                    "OWNER blackboard value cannot be decoded: " + name);
        }
        ScopedStateValueCodec.validate(value, namespace.serializedName() + "/OWNER/" + name);
        Object frozen = ScopedStateValueCodec.freeze(value);
        return new ScopedStateEntry(frozen, PortType.getTypeOf(frozen), stored.revision(),
                stored.sourceNodeId(), stored.gameTick());
    }

    public ScopedStateEntry put(String name, Object value,
                                String sourceNodeId, long gameTick, int maxEntries,
                                HolderLookup.Provider registries) {
        return put(ScopedStateNamespace.PUBLIC, name, value, sourceNodeId,
                gameTick, maxEntries, registries);
    }

    public ScopedStateEntry put(ScopedStateNamespace namespace, String name, Object value,
                                String sourceNodeId, long gameTick, int maxEntries,
                                HolderLookup.Provider registries) {
        Objects.requireNonNull(value, "value");
        StateKey stateKey = new StateKey(namespace, name);
        StoredEntry previous = entries.get(stateKey);
        int limit = Math.min(maxEntries, HARD_MAX_RECORDS);
        if (previous == null && size(namespace) >= limit) {
            throw new ScopedStateAccessException(
                    "Scoped-state namespace entry limit exceeded: " + limit);
        }
        Tag encoded = ScopedStateValueCodec.encode(value, registries,
                namespace.serializedName() + "/OWNER/" + name);
        Object frozen = ScopedStateValueCodec.freeze(value);
        String source = sourceNodeId != null ? sourceNodeId : "";
        StoredEntry stored = new StoredEntry(encoded.copy(), ++revision, source, gameTick);
        entries.put(stateKey, stored);
        recordChange(stateKey, new ScopedStateChange(revision, source, gameTick));
        return new ScopedStateEntry(
                frozen, PortType.getTypeOf(frozen), revision, source, gameTick);
    }

    public ScopedStateChange remove(String name, String sourceNodeId, long gameTick) {
        return remove(ScopedStateNamespace.PUBLIC, name, sourceNodeId, gameTick);
    }

    public ScopedStateChange remove(ScopedStateNamespace namespace, String name,
                                    String sourceNodeId, long gameTick) {
        StateKey stateKey = new StateKey(namespace, name);
        if (entries.remove(stateKey) == null) {
            throw new ScopedStateAccessException(
                    "Cannot clear a missing OWNER blackboard record: " + name);
        }
        String source = sourceNodeId != null ? sourceNodeId : "";
        ScopedStateChange change = new ScopedStateChange(
                ++revision, source, gameTick);
        recordChange(stateKey, change);
        return change;
    }

    public @Nullable ScopedStateChange lastChange(String name) {
        return lastChange(ScopedStateNamespace.PUBLIC, name);
    }

    public @Nullable ScopedStateChange lastChange(ScopedStateNamespace namespace, String name) {
        return changes.get(new StateKey(namespace, name));
    }

    public long revision() { return revision; }
    public boolean hasRecord(String name) {
        return hasRecord(ScopedStateNamespace.PUBLIC, name);
    }

    public boolean hasRecord(ScopedStateNamespace namespace, String name) {
        return entries.containsKey(new StateKey(namespace, name));
    }

    public int size() { return size(ScopedStateNamespace.PUBLIC); }

    public int size(ScopedStateNamespace namespace) {
        int size = 0;
        for (StateKey key : entries.keySet()) {
            if (key.namespace() == namespace) size++;
        }
        return size;
    }

    public boolean isEmpty() { return entries.isEmpty() && changes.isEmpty(); }

    public Map<String, ScopedStateEntry> entries(HolderLookup.Provider registries) {
        return entries(ScopedStateNamespace.PUBLIC, registries);
    }

    public Map<String, ScopedStateEntry> entries(ScopedStateNamespace namespace,
                                                  HolderLookup.Provider registries) {
        Map<String, ScopedStateEntry> result = new LinkedHashMap<>();
        for (StateKey key : entries.keySet()) {
            if (key.namespace() != namespace) continue;
            try {
                ScopedStateEntry entry = get(namespace, key.name(), registries);
                if (entry != null) result.put(key.name(), entry);
            } catch (RuntimeException ignored) {
                // Snapshot enumeration is best-effort; direct get keeps the diagnostic failure.
            }
        }
        return Map.copyOf(result);
    }

    public CompoundTag save(CompoundTag root, HolderLookup.Provider registries) {
        root.putLong("Revision", revision);
        ListTag serialized = new ListTag();
        for (Map.Entry<StateKey, StoredEntry> item : entries.entrySet()) {
            Tag value = item.getValue().value();
            CompoundTag tag = new CompoundTag();
            tag.putString("Namespace", item.getKey().namespace().serializedName());
            tag.putString("Name", item.getKey().name());
            tag.putLong("Revision", item.getValue().revision());
            tag.putString("Source", item.getValue().sourceNodeId());
            tag.putLong("GameTick", item.getValue().gameTick());
            if (value != null) tag.put("Value", value.copy());
            serialized.add(tag);
        }
        root.put("Entries", serialized);
        root.put("Changes", writeChanges());
        return root;
    }

    public void load(CompoundTag root, HolderLookup.Provider registries) {
        entries.clear();
        changes.clear();
        revision = Math.max(0L, root.getLongOr("Revision", 0L));
        for (Tag raw : root.getListOrEmpty("Entries")) {
            if (entries.size() >= HARD_MAX_TOTAL_RECORDS) break;
            if (!(raw instanceof CompoundTag tag)) continue;
            String name = tag.getStringOr("Name", "");
            if (name.isEmpty()) continue;
            ScopedStateNamespace namespace = ScopedStateNamespace.fromSerializedName(
                    tag.getStringOr("Namespace", "public"));
            StateKey stateKey = new StateKey(namespace, name);
            Tag encoded = tag.get("Value");
            long entryRevision = Math.max(0L, tag.getLongOr("Revision", 0L));
            ScopedStateChange change = new ScopedStateChange(
                    entryRevision, tag.getStringOr("Source", ""), tag.getLongOr("GameTick", 0L));
            if (encoded != null) {
                entries.put(stateKey, new StoredEntry(encoded.copy(), entryRevision,
                        change.sourceNodeId(), change.gameTick()));
            }
            recordChange(stateKey, change);
            revision = Math.max(revision, entryRevision);
        }
        readChanges(root.getListOrEmpty("Changes"));
    }

    private ListTag writeChanges() {
        ListTag result = new ListTag();
        for (Map.Entry<StateKey, ScopedStateChange> item : changes.entrySet()) {
            CompoundTag tag = new CompoundTag();
            tag.putString("Namespace", item.getKey().namespace().serializedName());
            tag.putString("Name", item.getKey().name());
            tag.putLong("Revision", item.getValue().revision());
            tag.putString("Source", item.getValue().sourceNodeId());
            tag.putLong("GameTick", item.getValue().gameTick());
            result.add(tag);
        }
        return result;
    }

    private void readChanges(ListTag list) {
        for (Tag raw : list) {
            if (!(raw instanceof CompoundTag tag)) continue;
            String name = tag.getStringOr("Name", "");
            if (name.isEmpty()) continue;
            ScopedStateNamespace namespace = ScopedStateNamespace.fromSerializedName(
                    tag.getStringOr("Namespace", "public"));
            long changeRevision = Math.max(0L, tag.getLongOr("Revision", 0L));
            recordChange(new StateKey(namespace, name), new ScopedStateChange(changeRevision,
                    tag.getStringOr("Source", ""), tag.getLongOr("GameTick", 0L)));
            revision = Math.max(revision, changeRevision);
        }
    }

    private void recordChange(StateKey key, ScopedStateChange change) {
        changes.remove(key);
        changes.put(key, change);
        while (changes.size() > HARD_MAX_TOTAL_RECORDS) {
            changes.remove(changes.keySet().iterator().next());
        }
    }

    private record StateKey(ScopedStateNamespace namespace, String name) {
    }

    private record StoredEntry(Tag value, long revision,
                               String sourceNodeId, long gameTick) {
    }
}
