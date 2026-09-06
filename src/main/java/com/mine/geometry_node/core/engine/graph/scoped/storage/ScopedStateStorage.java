package com.mine.geometry_node.core.engine.graph.scoped.storage;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateAccessException;
import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateEntry;
import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateNamespace;
import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateProvider;
import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateScope;
import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateServerConfig;
import com.mojang.serialization.Codec;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/** Persistent storage for named shared, scoreboard group, and dimension scoped state. */
public final class ScopedStateStorage extends SavedData {
    private static final int VERSION = 4;
    private static final int MAX_BUCKETS = 4_096;
    private static final int HARD_MAX_RECORDS_PER_BUCKET = ScopedStateServerConfig.HARD_MAX_ENTRIES;
    private static final Codec<ScopedStateStorage> CODEC = CompoundTag.CODEC.xmap(
            ScopedStateStorage::load, storage -> storage.save(new CompoundTag()));

    public static final SavedDataType<ScopedStateStorage> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(GeometryNode.MODID, "scoped_state"),
            ScopedStateStorage::new, CODEC);

    private final Map<ScopeKey, PersistentScopedStateBucket> buckets = new LinkedHashMap<>();

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

    /** Removes every namespace bucket owned by a scoreboard team that no longer exists. */
    public static boolean removeGroup(MinecraftServer server, String teamName) {
        String identity = "scoreboard:" + normalizeIdentity(teamName);
        ScopedStateStorage storage = get(server.overworld());
        boolean removed = storage.buckets.keySet().removeIf(key ->
                key.scope() == ScopedStateScope.GROUP && key.identity().equals(identity));
        if (removed) storage.setDirty();
        return removed;
    }

    /** Clears orphaned GROUP buckets left by worlds saved before team deletion cleanup existed. */
    public static int reconcileGroups(MinecraftServer server) {
        java.util.Set<String> activeIdentities = server.getScoreboard().getTeamNames().stream()
                .map(name -> "scoreboard:" + name)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        ScopedStateStorage storage = get(server.overworld());
        int before = storage.buckets.size();
        storage.buckets.keySet().removeIf(key -> key.scope() == ScopedStateScope.GROUP
                && !activeIdentities.contains(key.identity()));
        int removed = before - storage.buckets.size();
        if (removed > 0) storage.setDirty();
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
            ScopedStateNamespace namespace;
            if (!tag.contains("Namespace")) {
                namespace = ScopedStateNamespace.PUBLIC;
            } else {
                namespace = ScopedStateNamespace.fromSerializedName(
                        tag.getStringOr("Namespace", "")).orElse(null);
                if (namespace == null) continue;
            }
            String identity;
            try {
                identity = scope == ScopedStateScope.SHARED ? "server"
                        : normalizeIdentity(tag.getStringOr("Identity", ""));
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            if (identity.isEmpty()) continue;
            ScopeKey scopeKey = new ScopeKey(namespace, scope, identity);
            PersistentScopedStateBucket bucket = storage.buckets.get(scopeKey);
            if (bucket == null) {
                if (storage.buckets.size() >= MAX_BUCKETS) continue;
                bucket = new PersistentScopedStateBucket();
                storage.buckets.put(scopeKey, bucket);
            }
            bucket.loadEntries(tag.getListOrEmpty("Entries"), HARD_MAX_RECORDS_PER_BUCKET);
            if (bucket.isEmpty()) storage.buckets.remove(scopeKey);
        }
        return storage;
    }

    private CompoundTag save(CompoundTag root) {
        root.putInt("Version", VERSION);
        ListTag bucketTags = new ListTag();
        for (Map.Entry<ScopeKey, PersistentScopedStateBucket> bucketEntry : buckets.entrySet()) {
            CompoundTag tag = new CompoundTag();
            tag.putString("Namespace", bucketEntry.getKey().namespace().serializedName());
            tag.putString("Scope", bucketEntry.getKey().scope().name());
            tag.putString("Identity", bucketEntry.getKey().identity());
            tag.put("Entries", bucketEntry.getValue().saveEntries());
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
            PersistentScopedStateBucket bucket = buckets.get(storageKey);
            return bucket != null ? bucket.get(name, registries, location(name)) : null;
        }

        @Override
        public void put(String name, Object value) {
            PersistentScopedStateBucket bucket = bucketForMutation(storageKey);
            boolean changed;
            try {
                changed = bucket.put(name, value, maxEntries, registries, location(name),
                        this::notifyLimit);
            } catch (RuntimeException exception) {
                if (bucket.isEmpty()) buckets.remove(storageKey);
                throw exception;
            }
            if (changed) setDirty();
        }

        @Override
        public boolean remove(String name) {
            PersistentScopedStateBucket bucket = buckets.get(storageKey);
            if (bucket == null || !bucket.remove(name)) return false;
            if (bucket.isEmpty()) buckets.remove(storageKey);
            setDirty();
            return true;
        }

        @Override public boolean hasRecord(String name) {
            PersistentScopedStateBucket bucket = buckets.get(storageKey);
            return bucket != null && bucket.hasRecord(name);
        }

        @Override public long revision() {
            PersistentScopedStateBucket bucket = buckets.get(storageKey);
            return bucket != null ? bucket.revision() : 0L;
        }

        @Override public int size() {
            PersistentScopedStateBucket bucket = buckets.get(storageKey);
            return bucket != null ? bucket.size() : 0;
        }

        @Override public Map<String, ScopedStateEntry> entries(int limit) {
            PersistentScopedStateBucket bucket = buckets.get(storageKey);
            return bucket != null
                    ? bucket.entries(registries, locationPrefix(), limit) : Map.of();
        }

        private void notifyLimit() {
            ScopedStateLimitNotifier.notifyLimit(level, namespace, scope,
                    storageKey.identity(), maxEntries);
        }

        private String location(String name) {
            return locationPrefix() + name;
        }

        private String locationPrefix() {
            return namespace.serializedName() + "/" + scope + "/";
        }
    }

    private PersistentScopedStateBucket bucketForMutation(ScopeKey key) {
        PersistentScopedStateBucket existing = buckets.get(key);
        if (existing != null) return existing;
        if (buckets.size() >= MAX_BUCKETS) {
            throw new ScopedStateAccessException(
                    "Persistent blackboard scope limit exceeded: " + MAX_BUCKETS);
        }
        PersistentScopedStateBucket created = new PersistentScopedStateBucket();
        buckets.put(key, created);
        return created;
    }

    private record ScopeKey(ScopedStateNamespace namespace,
                            ScopedStateScope scope, String identity) {
    }
}
