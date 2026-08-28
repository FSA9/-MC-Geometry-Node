package com.mine.geometry_node.core.engine.graph.scoped;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.core.engine.graph.value.GraphValueCodecRegistry;
import com.mine.geometry_node.core.node.port.PortType;
import com.mojang.serialization.Codec;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Persistent storage for named shared, scoreboard group, and dimension blackboards. */
public final class ScopedStateStorage extends SavedData {
    private static final int VERSION = 3;
    private static final int MAX_BUCKETS = 4_096;
    private static final int HARD_MAX_RECORDS_PER_BUCKET =
            ScopedStateServerConfig.HARD_MAX_ENTRIES;
    private static final Codec<ScopedStateStorage> CODEC = CompoundTag.CODEC.xmap(
            ScopedStateStorage::load, storage -> storage.save(new CompoundTag()));

    public static final SavedDataType<ScopedStateStorage> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(GeometryNode.MODID, "scoped_state"),
            ScopedStateStorage::new, CODEC);

    private final Map<ScopeKey, Bucket> buckets = new LinkedHashMap<>();

    public static ScopedStateStorage get(ServerLevel level) {
        return level.getServer().getDataStorage().computeIfAbsent(TYPE);
    }

    public ScopedStateProvider provider(ScopedStateNamespace namespace,
                                        ScopedStateScope scope, String stableIdentity,
                                        ServerLevel level, int maxEntries) {
        if (scope != ScopedStateScope.SHARED && scope != ScopedStateScope.GROUP
                && scope != ScopedStateScope.WORLD) {
            throw new IllegalArgumentException("Scope is not stored in server SavedData: " + scope);
        }
        String identity = scope == ScopedStateScope.SHARED
                ? "server" : normalizeIdentity(stableIdentity);
        if (identity.isEmpty()) throw new IllegalArgumentException("Blackboard identity cannot be empty");
        return new StoredProvider(namespace, scope,
                new ScopeKey(namespace, scope, identity), level, maxEntries);
    }

    public boolean removeScope(ScopedStateScope scope, String stableIdentity) {
        return removeScope(ScopedStateNamespace.PUBLIC, scope, stableIdentity);
    }

    public boolean removeScope(ScopedStateNamespace namespace,
                               ScopedStateScope scope, String stableIdentity) {
        String identity = scope == ScopedStateScope.SHARED
                ? "server" : normalizeIdentity(stableIdentity);
        boolean removed = buckets.remove(new ScopeKey(namespace, scope, identity)) != null;
        if (removed) setDirty();
        return removed;
    }

    private static ScopedStateStorage load(CompoundTag root) {
        ScopedStateStorage storage = new ScopedStateStorage();
        for (Tag rawBucket : root.getListOrEmpty("Buckets")) {
            if (!(rawBucket instanceof CompoundTag tag)) continue;
            ScopedStateScope scope;
            try {
                scope = ScopedStateScope.valueOf(tag.getStringOr("Scope", ""));
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            if (scope != ScopedStateScope.SHARED && scope != ScopedStateScope.GROUP
                    && scope != ScopedStateScope.WORLD) continue;
            ScopedStateNamespace namespace = ScopedStateNamespace.fromSerializedName(
                    tag.getStringOr("Namespace", "public"));
            String identity;
            try {
                identity = scope == ScopedStateScope.SHARED ? "server"
                        : normalizeIdentity(tag.getStringOr("Identity", ""));
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            if (identity.isEmpty()) continue;
            ScopeKey scopeKey = new ScopeKey(namespace, scope, identity);
            Bucket bucket = storage.buckets.get(scopeKey);
            if (bucket == null) {
                if (storage.buckets.size() >= MAX_BUCKETS) break;
                bucket = new Bucket();
                storage.buckets.put(scopeKey, bucket);
            }
            bucket.revision = Math.max(bucket.revision,
                    Math.max(0L, tag.getLongOr("Revision", 0L)));
            for (Tag rawEntry : tag.getListOrEmpty("Entries")) {
                if (bucket.entries.size() >= HARD_MAX_RECORDS_PER_BUCKET) break;
                if (!(rawEntry instanceof CompoundTag entryTag)) continue;
                String name = entryTag.getStringOr("Name", "");
                if (name.isEmpty()) continue;
                long revision = Math.max(0L, entryTag.getLongOr("Revision", 0L));
                String source = entryTag.getStringOr("Source", "");
                long gameTick = entryTag.getLongOr("GameTick", 0L);
                Tag value = entryTag.get("Value");
                ScopedStateChange change = new ScopedStateChange(
                        revision, source, gameTick);
                if (value != null) {
                    bucket.entries.put(name, new StoredEntry(value.copy(), revision, source, gameTick));
                }
                recordChange(bucket, name, change);
                bucket.revision = Math.max(bucket.revision, revision);
            }
            readChanges(bucket, tag.getListOrEmpty("Changes"));
        }
        return storage;
    }

    private CompoundTag save(CompoundTag root) {
        root.putInt("Version", VERSION);
        ListTag bucketTags = new ListTag();
        for (Map.Entry<ScopeKey, Bucket> bucketEntry : buckets.entrySet()) {
            CompoundTag tag = new CompoundTag();
            tag.putString("Namespace", bucketEntry.getKey().namespace().serializedName());
            tag.putString("Scope", bucketEntry.getKey().scope().name());
            tag.putString("Identity", bucketEntry.getKey().identity());
            tag.putLong("Revision", bucketEntry.getValue().revision);
            ListTag entries = new ListTag();
            for (Map.Entry<String, StoredEntry> stored : bucketEntry.getValue().entries.entrySet()) {
                CompoundTag entryTag = new CompoundTag();
                entryTag.putString("Name", stored.getKey());
                entryTag.putLong("Revision", stored.getValue().revision());
                entryTag.putString("Source", stored.getValue().sourceNodeId());
                entryTag.putLong("GameTick", stored.getValue().gameTick());
                entryTag.put("Value", stored.getValue().value().copy());
                entries.add(entryTag);
            }
            tag.put("Entries", entries);
            tag.put("Changes", writeChanges(bucketEntry.getValue()));
            bucketTags.add(tag);
        }
        root.put("Buckets", bucketTags);
        return root;
    }

    private static String normalizeReference(String value) {
        return value != null ? value.trim() : "";
    }

    private static String normalizeIdentity(String value) {
        String normalized = normalizeReference(value);
        if (normalized.length() > 256) {
            throw new IllegalArgumentException("Blackboard identity exceeds 256 characters");
        }
        return normalized;
    }

    private final class StoredProvider implements ScopedStateProvider {
        private final ScopedStateNamespace namespace;
        private final ScopedStateScope scope;
        private final ScopeKey storageKey;
        private final ServerLevel level;
        private final HolderLookup.Provider registries;
        private final int maxEntries;

        private StoredProvider(ScopedStateNamespace namespace, ScopedStateScope scope,
                               ScopeKey storageKey, ServerLevel level, int maxEntries) {
            this.namespace = namespace;
            this.scope = scope;
            this.storageKey = storageKey;
            this.level = level;
            this.registries = level.registryAccess();
            this.maxEntries = Math.min(maxEntries, HARD_MAX_RECORDS_PER_BUCKET);
        }

        @Override public ScopedStateScope scope() { return scope; }
        @Override public String identity() { return storageKey.identity(); }

        @Override
        public @Nullable ScopedStateEntry get(String name) {
            Bucket bucket = buckets.get(storageKey);
            StoredEntry stored = bucket != null ? bucket.entries.get(name) : null;
            if (stored == null) return null;
            Object value = GraphValueCodecRegistry.fromTag(stored.value(), registries);
            if (value == null) {
                throw new ScopedStateAccessException(
                        "Persistent blackboard value cannot be decoded: " + scope + "/" + name);
            }
            ScopedStateValueCodec.validate(value, scope + "/" + name);
            Object frozen = ScopedStateValueCodec.freeze(value);
            return new ScopedStateEntry(frozen, PortType.getTypeOf(frozen), stored.revision(),
                    stored.sourceNodeId(), stored.gameTick());
        }

        @Override
        public ScopedStateEntry put(String name, Object value,
                                            String sourceNodeId, long gameTick) {
            Objects.requireNonNull(value, "value");
            Tag encoded = ScopedStateValueCodec.encode(
                    value, registries, scope + "/" + name);
            Object frozen = ScopedStateValueCodec.freeze(value);
            Bucket bucket = bucketForMutation(storageKey);
            StoredEntry previous = bucket.entries.get(name);
            int currentSize = ScopedStateStorage.size(bucket);
            if (previous == null
                    && currentSize >= maxEntries) {
                ScopedStateLimitNotifier.notifyLimit(level, namespace, scope,
                        storageKey.identity(), maxEntries);
                throw new ScopedStateAccessException(
                        "Scoped-state namespace entry limit exceeded: " + maxEntries);
            }
            long revision = ++bucket.revision;
            String source = sourceNodeId != null ? sourceNodeId : "";
            bucket.entries.put(name, new StoredEntry(encoded.copy(), revision, source, gameTick));
            recordChange(bucket, name,
                    new ScopedStateChange(revision, source, gameTick));
            setDirty();
            if (previous == null && currentSize + 1 == maxEntries) {
                ScopedStateLimitNotifier.notifyLimit(level, namespace, scope,
                        storageKey.identity(), maxEntries);
            }
            return new ScopedStateEntry(
                    frozen, PortType.getTypeOf(frozen), revision, source, gameTick);
        }

        @Override
        public ScopedStateChange remove(
                String name, String sourceNodeId, long gameTick) {
            Bucket bucket = buckets.get(storageKey);
            if (bucket == null || bucket.entries.remove(name) == null) {
                throw new ScopedStateAccessException(
                        "Cannot clear a missing persistent blackboard record: " + scope + "/" + name);
            }
            long revision = ++bucket.revision;
            String source = sourceNodeId != null ? sourceNodeId : "";
            ScopedStateChange change = new ScopedStateChange(
                    revision, source, gameTick);
            recordChange(bucket, name, change);
            setDirty();
            return change;
        }

        @Override
        public @Nullable ScopedStateChange lastChange(String name) {
            Bucket bucket = buckets.get(storageKey);
            return bucket != null ? bucket.changes.get(name) : null;
        }

        @Override public boolean hasRecord(String name) {
            Bucket bucket = buckets.get(storageKey);
            return bucket != null && bucket.entries.containsKey(name);
        }

        @Override public long revision() {
            Bucket bucket = buckets.get(storageKey);
            return bucket != null ? bucket.revision : 0L;
        }

        @Override public int size() {
            Bucket bucket = buckets.get(storageKey);
            return bucket != null ? ScopedStateStorage.size(bucket) : 0;
        }

        @Override public Map<String, ScopedStateEntry> entries() {
            Bucket bucket = buckets.get(storageKey);
            if (bucket == null || bucket.entries.isEmpty()) return Map.of();
            Map<String, ScopedStateEntry> result = new LinkedHashMap<>();
            for (String name : bucket.entries.keySet()) {
                try {
                    ScopedStateEntry entry = get(name);
                    if (entry != null) result.put(name, entry);
                } catch (RuntimeException ignored) {
                    // Snapshot enumeration is best-effort; direct get keeps the diagnostic failure.
                }
            }
            return Map.copyOf(result);
        }
    }

    private static int size(Bucket bucket) {
        return bucket.entries.size();
    }

    private static ListTag writeChanges(Bucket bucket) {
        ListTag result = new ListTag();
        for (Map.Entry<String, ScopedStateChange> item : bucket.changes.entrySet()) {
            CompoundTag tag = new CompoundTag();
            tag.putString("Name", item.getKey());
            tag.putLong("Revision", item.getValue().revision());
            tag.putString("Source", item.getValue().sourceNodeId());
            tag.putLong("GameTick", item.getValue().gameTick());
            result.add(tag);
        }
        return result;
    }

    private static void readChanges(Bucket bucket, ListTag list) {
        for (Tag raw : list) {
            if (!(raw instanceof CompoundTag tag)) continue;
            String name = tag.getStringOr("Name", "");
            if (name.isEmpty()) continue;
            long revision = Math.max(0L, tag.getLongOr("Revision", 0L));
            recordChange(bucket, name, new ScopedStateChange(revision,
                    tag.getStringOr("Source", ""), tag.getLongOr("GameTick", 0L)));
            bucket.revision = Math.max(bucket.revision, revision);
        }
    }

    private static void recordChange(Bucket bucket, String name,
                                     ScopedStateChange change) {
        bucket.changes.remove(name);
        bucket.changes.put(name, change);
        while (bucket.changes.size() > HARD_MAX_RECORDS_PER_BUCKET) {
            bucket.changes.remove(bucket.changes.keySet().iterator().next());
        }
    }

    private Bucket bucketForMutation(ScopeKey key) {
        Bucket existing = buckets.get(key);
        if (existing != null) return existing;
        if (buckets.size() >= MAX_BUCKETS) {
            throw new ScopedStateAccessException(
                    "Persistent blackboard scope limit exceeded: " + MAX_BUCKETS);
        }
        Bucket created = new Bucket();
        buckets.put(key, created);
        return created;
    }

    private record ScopeKey(ScopedStateNamespace namespace,
                            ScopedStateScope scope, String identity) {
    }

    private static final class Bucket {
        private final Map<String, StoredEntry> entries = new LinkedHashMap<>();
        private final Map<String, ScopedStateChange> changes = new LinkedHashMap<>();
        private long revision;
    }

    private record StoredEntry(Tag value, long revision,
                               String sourceNodeId, long gameTick) {
    }
}
