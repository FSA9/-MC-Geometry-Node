package com.mine.geometry_node.core.engine.graph.scoped;

import com.mine.geometry_node.core.engine.graph.value.GraphValueSnapshot;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Typed OWNER blackboard partition serialized independently from blueprint attributes. */
public final class OwnerScopedStateStore {
    private static final int HARD_MAX_RECORDS = ScopedStateServerConfig.HARD_MAX_ENTRIES;
    private static final int HARD_MAX_TOTAL_RECORDS =
            HARD_MAX_RECORDS * ScopedStateNamespace.values().length;
    private final Map<StateKey, PersistentScopedStateEntry> entries = new LinkedHashMap<>();
    private final Map<ScopedStateNamespace, Integer> namespaceSizes =
            new EnumMap<>(ScopedStateNamespace.class);
    private long revision;

    public @Nullable ScopedStateEntry get(String name, HolderLookup.Provider registries) {
        return get(ScopedStateNamespace.PUBLIC, name, registries);
    }

    public @Nullable ScopedStateEntry get(ScopedStateNamespace namespace, String name,
                                          HolderLookup.Provider registries) {
        PersistentScopedStateEntry stored = entries.get(new StateKey(namespace, name));
        if (stored == null) return null;
        return stored.read(registries, namespace.serializedName() + "/OWNER/" + name);
    }

    public ScopedStateEntry put(String name, Object value, int maxEntries,
                                HolderLookup.Provider registries) {
        return put(ScopedStateNamespace.PUBLIC, name, value,
                maxEntries, registries, () -> {});
    }

    public ScopedStateEntry put(ScopedStateNamespace namespace, String name, Object value,
                                int maxEntries,
                                HolderLookup.Provider registries, Runnable limitNotifier) {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(limitNotifier, "limitNotifier");
        StateKey stateKey = new StateKey(namespace, name);
        PersistentScopedStateEntry previous = entries.get(stateKey);
        int limit = Math.min(maxEntries, HARD_MAX_RECORDS);
        if (previous == null && size(namespace) >= limit) {
            limitNotifier.run();
            throw new ScopedStateAccessException(
                    "Scoped-state namespace entry limit exceeded: " + limit);
        }
        GraphValueSnapshot.FrozenValue frozen = GraphValueSnapshot.freeze(value);
        Tag encoded = ScopedStateValueCodec.encode(frozen.value(), registries,
                namespace.serializedName() + "/OWNER/" + name);
        PersistentScopedStateEntry stored = PersistentScopedStateEntry.written(encoded, frozen);
        revision++;
        entries.put(stateKey, stored);
        if (previous == null) {
            int newSize = size(namespace) + 1;
            namespaceSizes.put(namespace, newSize);
            if (newSize == limit) limitNotifier.run();
        }
        return stored.read(registries, namespace.serializedName() + "/OWNER/" + name);
    }

    public boolean remove(String name) {
        return remove(ScopedStateNamespace.PUBLIC, name);
    }

    public boolean remove(ScopedStateNamespace namespace, String name) {
        StateKey stateKey = new StateKey(namespace, name);
        if (entries.remove(stateKey) == null) return false;
        namespaceSizes.computeIfPresent(namespace, (ignored, size) -> size > 1 ? size - 1 : null);
        revision++;
        return true;
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
        return namespaceSizes.getOrDefault(namespace, 0);
    }

    public boolean isEmpty() { return entries.isEmpty(); }

    public Map<String, ScopedStateEntry> entries(HolderLookup.Provider registries) {
        return entries(ScopedStateNamespace.PUBLIC, registries, Integer.MAX_VALUE);
    }

    public Map<String, ScopedStateEntry> entries(ScopedStateNamespace namespace,
                                                  HolderLookup.Provider registries) {
        return entries(namespace, registries, Integer.MAX_VALUE);
    }

    public Map<String, ScopedStateEntry> entries(ScopedStateNamespace namespace,
                                                 HolderLookup.Provider registries, int limit) {
        if (limit <= 0) return Map.of();
        Map<String, ScopedStateEntry> result = new LinkedHashMap<>();
        for (StateKey key : entries.keySet()) {
            if (key.namespace() != namespace) continue;
            try {
                ScopedStateEntry entry = get(namespace, key.name(), registries);
                if (entry != null) result.put(key.name(), entry);
            } catch (RuntimeException ignored) {
                // Snapshot enumeration is best-effort; direct get keeps the diagnostic failure.
            }
            if (result.size() >= limit) break;
        }
        return Map.copyOf(result);
    }

    public CompoundTag save(CompoundTag root, HolderLookup.Provider registries) {
        ListTag serialized = new ListTag();
        for (Map.Entry<StateKey, PersistentScopedStateEntry> item : entries.entrySet()) {
            CompoundTag tag = new CompoundTag();
            tag.putString("Namespace", item.getKey().namespace().serializedName());
            tag.putString("Name", item.getKey().name());
            tag.put("Value", item.getValue().encodedCopy());
            serialized.add(tag);
        }
        root.put("Entries", serialized);
        return root;
    }

    public void load(CompoundTag root, HolderLookup.Provider registries) {
        entries.clear();
        namespaceSizes.clear();
        revision = 0L;
        for (Tag raw : root.getListOrEmpty("Entries")) {
            if (entries.size() >= HARD_MAX_TOTAL_RECORDS) break;
            if (!(raw instanceof CompoundTag tag)) continue;
            String name = tag.getStringOr("Name", "");
            ScopedStateNamespace namespace;
            if (!tag.contains("Namespace")) {
                namespace = ScopedStateNamespace.PUBLIC;
            } else {
                namespace = ScopedStateNamespace.fromSerializedName(
                        tag.getStringOr("Namespace", "")).orElse(null);
                if (namespace == null) continue;
            }
            StateKey stateKey = new StateKey(namespace, name);
            Tag encoded = tag.get("Value");
            if (encoded != null) {
                PersistentScopedStateEntry previous = entries.put(
                        stateKey, PersistentScopedStateEntry.loaded(encoded));
                if (previous == null) namespaceSizes.merge(namespace, 1, Integer::sum);
            }
        }
    }

    private record StateKey(ScopedStateNamespace namespace, String name) {
    }
}
