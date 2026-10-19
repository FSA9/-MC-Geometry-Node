package com.mine.geometry_node.core.engine.graph.scoped.storage;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateAccessException;
import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateEntry;
import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateNamespace;
import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateProvider;
import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateScope;
import com.mine.geometry_node.core.config.ScopedStateServerConfig;
import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateLimitNotifier;
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
import java.util.function.UnaryOperator;

/** Persistent storage for named shared, scoreboard group, and dimension scoped state. */
public final class ServerScopedStateSavedData extends SavedData {
    private static final int VERSION = 4;
    private static final int MAX_BUCKETS = 4_096;
    private static final int HARD_MAX_RECORDS_PER_BUCKET = ScopedStateServerConfig.HARD_MAX_ENTRIES;
    private static final Codec<ServerScopedStateSavedData> CODEC = CompoundTag.CODEC.xmap(
            ServerScopedStateSavedData::load, storage -> storage.save(new CompoundTag()));

    public static final SavedDataType<ServerScopedStateSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(GeometryNode.MODID, "scoped_state"),
            ServerScopedStateSavedData::new, CODEC);

    private final Map<ScopeKey, PersistentScopedStateBucket> buckets = new LinkedHashMap<>();
    private boolean updating;

    public static ServerScopedStateSavedData get(ServerLevel level) {
        ServerScopedStateSavedData storage = level.getServer().getDataStorage().computeIfAbsent(TYPE);
        return storage;
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
        requireNotUpdating();
        String identity = scope == ScopedStateScope.SHARED
                ? "server" : normalizeIdentity(stableIdentity);
        boolean removed = buckets.remove(new ScopeKey(namespace, scope, identity)) != null;
        if (removed) setDirty();
        return removed;
    }

    /** Removes every namespace bucket owned by a scoreboard team that no longer exists. */
    public static boolean removeGroup(MinecraftServer server, String teamName) {
        String identity = "scoreboard:" + normalizeIdentity(teamName);
        ServerScopedStateSavedData storage = get(server.overworld());
        storage.requireNotUpdating();
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
        ServerScopedStateSavedData storage = get(server.overworld());
        storage.requireNotUpdating();
        int before = storage.buckets.size();
        storage.buckets.keySet().removeIf(key -> key.scope() == ScopedStateScope.GROUP
                && !activeIdentities.contains(key.identity()));
        int removed = before - storage.buckets.size();
        if (removed > 0) storage.setDirty();
        return removed;
    }

    private static ServerScopedStateSavedData load(CompoundTag root) {
        ServerScopedStateSavedData storage = new ServerScopedStateSavedData();
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
            if (bucket.loadEntries(tag.getListOrEmpty("Entries"),
                    HARD_MAX_RECORDS_PER_BUCKET, locationPrefix(scopeKey))) {
                storage.setDirty();
            }
            if (bucket.isEmpty()) storage.buckets.remove(scopeKey);
        }
        return storage;
    }

    private CompoundTag save(CompoundTag root) {
        root.putInt("Version", VERSION);
        ListTag bucketTags = new ListTag();
        for (Map.Entry<ScopeKey, PersistentScopedStateBucket> bucketEntry : buckets.entrySet()) {
            if (bucketEntry.getValue().isEmpty()) continue;
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

    private void requireNotUpdating() {
        if (updating) throw new IllegalStateException("Reentrant scoped metadata mutation");
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
        @Override public boolean available() { return !level.getServer().isStopped(); }
        @Override public com.mine.geometry_node.core.metadata.DataValuePolicy valuePolicy() {
            return com.mine.geometry_node.core.engine.metadata.GraphMetadataValuePolicy.scoped(registries);
        }

        @Override
        public @Nullable ScopedStateEntry get(String name) {
            requireThread();
            PersistentScopedStateBucket bucket = buckets.get(storageKey);
            return bucket != null ? bucket.get(name, registries, location(name)) : null;
        }

        @Override
        public void put(String name, Object value) {
            update(current -> {
                Map<String, Object> next = new LinkedHashMap<>(current);
                next.put(name, value);
                return next;
            });
        }

        @Override public Map<String, Object> read() {
            requireThread();
            PersistentScopedStateBucket bucket = buckets.get(storageKey);
            return bucket == null ? Map.of() : bucket.readValues(registries, locationPrefix());
        }

        @Override public boolean update(UnaryOperator<Map<String, Object>> transformation) {
            requireThread();
            requireNotUpdating();
            updating = true;
            try {
                PersistentScopedStateBucket bucket = buckets.get(storageKey);
                if (bucket == null) bucket = new PersistentScopedStateBucket();
                int oldSize = bucket.size();
                boolean changed = bucket.update(current -> {
                    Map<String, Object> candidate = transformation.apply(current);
                    if (oldSize == 0 && candidate != null && !candidate.isEmpty()
                            && buckets.values().stream().filter(value -> !value.isEmpty()).count() >= MAX_BUCKETS)
                        throw new ScopedStateAccessException("Persistent blackboard scope limit exceeded: " + MAX_BUCKETS);
                    return candidate;
                }, maxEntries, registries, locationPrefix(), () -> {});
                if (changed) {
                    buckets.put(storageKey, bucket);
                    setDirty();
                    if (oldSize < maxEntries && bucket.size() == maxEntries)
                        PersistentScopedStateBucket.notifyCommitted(this::notifyLimit);
                }
                return changed;
            } finally { updating = false; }
        }

        private void requireThread() {
            if (!level.getServer().isSameThread() || level.getServer().isStopped())
                throw new ScopedStateAccessException("Scoped state must be accessed on the server owner thread");
        }

        @Override
        public boolean remove(String name) {
            requireThread();
            requireNotUpdating();
            PersistentScopedStateBucket bucket = buckets.get(storageKey);
            if (bucket == null || !bucket.remove(name)) return false;
            setDirty();
            return true;
        }

        @Override public boolean hasRecord(String name) {
            return get(name) != null;
        }

        @Override public long revision() {
            requireThread();
            PersistentScopedStateBucket bucket = buckets.get(storageKey);
            return bucket != null ? bucket.revision() : 0L;
        }

        @Override public int size() {
            requireThread();
            PersistentScopedStateBucket bucket = buckets.get(storageKey);
            return bucket != null ? bucket.size() : 0;
        }

        @Override public Map<String, ScopedStateEntry> entries(int limit) {
            requireThread();
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
            return ServerScopedStateSavedData.locationPrefix(storageKey);
        }
    }

    private static String locationPrefix(ScopeKey key) {
        return key.namespace().serializedName() + "/" + key.scope() + "/"
                + key.identity() + "/";
    }

    private record ScopeKey(ScopedStateNamespace namespace,
                            ScopedStateScope scope, String identity) {
    }
}
