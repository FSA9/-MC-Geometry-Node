package com.mine.geometry_node.core.engine.system.viewport.entity;

import com.mine.geometry_node.core.engine.system.scene.SceneNativeEntities;
import com.mine.geometry_node.core.engine.system.scene.SceneObject;
import com.mine.geometry_node.core.utils.nbt.EntityNbtCompat;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityProcessor;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Server-thread, per-level opaque native snapshots. Callers enforce editor authorization before access.
 * Eviction is explicit loss of restore capability; an expired token never yields a partial entity.
 */
public final class ViewportEntitySnapshots {
    public static final int MAX_RECORDS = 256;
    public static final int MAX_TOTAL_BYTES = 16 * 1024 * 1024;
    public static final int MAX_SNAPSHOT_BYTES = 256 * 1024;
    private static final int MAX_BINDINGS = 4096;

    private final ServerLevel level;
    private final int recordLimit, totalLimit, snapshotLimit;
    private final LinkedHashMap<UUID, Snapshot> snapshots = new LinkedHashMap<>(16, .75f, true);
    private final LinkedHashMap<UUID, UUID> bindings = new LinkedHashMap<>(16, .75f, true);
    private int totalBytes;

    public ViewportEntitySnapshots(ServerLevel level) {
        this(Objects.requireNonNull(level), MAX_RECORDS, MAX_TOTAL_BYTES, MAX_SNAPSHOT_BYTES);
    }

    ViewportEntitySnapshots(ServerLevel level, int recordLimit, int totalLimit, int snapshotLimit) {
        if (recordLimit <= 0 || totalLimit <= 0 || snapshotLimit <= 0 || snapshotLimit > totalLimit)
            throw new IllegalArgumentException("Invalid entity snapshot limits");
        this.level = level;
        this.recordLimit = recordLimit;
        this.totalLimit = totalLimit;
        this.snapshotLimit = snapshotLimit;
    }

    public SceneObject capture(Entity entity, long revision) {
        requireEntity(entity);
        CompoundTag data = EntityNbtCompat.saveWithoutId(entity);
        SceneObject description = SceneNativeEntities.capture(entity, UUID.randomUUID(), revision);
        return remember(description, data);
    }

    /** Checks every client-visible field against the trusted snapshot, allowing only UUID/position/revision changes. */
    public void validate(SceneObject object) { requireSnapshot(object); }

    /** Full saved native data; every result is detached from this store and from all other readers. */
    public CompoundTag data(SceneObject object) { return requireSnapshot(object).data.copy(); }

    /** Creates a detached entity. Adding it to the level, loaded-position checks and atomic rollback belong to the transaction. */
    public Entity create(SceneObject object) {
        Entity entity = instantiate(object, creationData(object));
        try {
            SceneObject actual = SceneNativeEntities.capture(entity, token(object), object.revision());
            CompoundTag actualData = EntityNbtCompat.saveWithoutId(entity);
            Snapshot snapshot = requireSnapshot(object);
            if (matches(snapshot, actual, content(actualData))) bind(entity.getUUID(), token(object));
            // A native loader may normalize its own data. Keep that canonical state without inventing a UI edit.
            else remember(actual, actualData);
            return entity;
        } catch (RuntimeException exception) {
            entity.discard();
            throw exception;
        }
    }

    /**
     * Restore the authoritative deletion tombstone, even if its old client token expired.
     * Both arguments must originate in the server transaction's retained tombstone, never client NBT.
     * The returned entity is detached and already has a fresh, captureable canonical snapshot binding.
     */
    public Entity restore(SceneObject tombstone, CompoundTag fullNbt) {
        Entity entity = instantiate(tombstone, restorationData(tombstone, fullNbt));
        try {
            capture(entity, tombstone.revision());
            return entity;
        } catch (RuntimeException exception) {
            entity.discard();
            throw exception;
        }
    }

    /** Preserve the original opaque token when a copy/undo restore assigns a different native UUID or position. */
    public void bindToken(Entity entity, SceneObject object) {
        requireEntity(entity);
        if (!entity.getUUID().equals(object.id())) throw new IllegalArgumentException("Entity snapshot identity mismatch");
        SceneObject actual = SceneNativeEntities.capture(entity, token(object), object.revision());
        bindSnapshot(actual, EntityNbtCompat.saveWithoutId(entity), object);
    }

    /** Clears all capabilities when the owning level/service is discarded. */
    public void clear() { snapshots.clear(); bindings.clear(); totalBytes = 0; }

    // Shared seams keep snapshot/UUID/budget invariants testable without booting a server level.
    SceneObject remember(SceneObject description, CompoundTag data) {
        SceneNativeEntities.validate(description);
        rejectRelationships(data);
        checkDataType(description, data);
        int bytes = size(data, snapshotLimit);
        CompoundTag comparable = content(data);
        UUID bound = bindings.get(description.id());
        Snapshot candidate = bound == null ? null : snapshots.get(bound);
        if (candidate != null && matches(candidate, description, comparable))
            return envelope(candidate, description);
        for (Snapshot snapshot : snapshots.values()) {
            if (snapshot.source.equals(description.id()) && matches(snapshot, description, comparable)) {
                UUID existingToken = token(snapshot.description);
                // Move in access-order only after ending the iterator.
                candidate = snapshot;
                bound = existingToken;
                break;
            }
        }
        if (candidate != null && bound != null && matches(candidate, description, comparable)) {
            snapshots.get(bound);
            bind(description.id(), bound);
            return envelope(candidate, description);
        }
        UUID newToken = UUID.randomUUID();
        Map<String, String> parameters = new java.util.LinkedHashMap<>(description.parameters());
        parameters.put(SceneNativeEntities.SNAPSHOT, newToken.toString());
        SceneObject stored = description.withProperties(description.name(), description.color(), description.enabled(), parameters);
        Snapshot snapshot = new Snapshot(description.id(), stored, data.copy(), bytes);
        while (snapshots.size() >= recordLimit || totalBytes > totalLimit - bytes) evictOldest();
        snapshots.put(newToken, snapshot);
        totalBytes += bytes;
        bind(description.id(), newToken);
        return stored;
    }

    CompoundTag creationData(SceneObject object) {
        Snapshot snapshot = requireSnapshot(object);
        return spawnData(object, snapshot.data);
    }

    CompoundTag restorationData(SceneObject tombstone, CompoundTag fullNbt) {
        SceneNativeEntities.validate(tombstone);
        rejectRelationships(fullNbt);
        checkDataType(tombstone, fullNbt);
        size(fullNbt, snapshotLimit);
        return spawnData(tombstone, fullNbt);
    }

    private Entity instantiate(SceneObject object, CompoundTag spawn) {
        if (level == null) throw new IllegalStateException("Entity snapshot store is not attached to a level");
        Entity entity = EntityType.loadEntityRecursive(spawn, level, EntitySpawnReason.LOAD, EntityProcessor.NOP);
        if (entity == null) throw new IllegalStateException("Unable to restore entity snapshot");
        try {
            requireEntity(entity);
            entity.setUUID(object.id());
            entity.setPos(object.position().x, object.position().y, object.position().z);
            return entity;
        } catch (RuntimeException exception) {
            entity.discard();
            throw exception;
        }
    }

    private static CompoundTag spawnData(SceneObject object, CompoundTag data) {
        CompoundTag spawn = data.copy();
        spawn.remove("UUID");
        ListTag position = new ListTag();
        position.add(DoubleTag.valueOf(object.position().x));
        position.add(DoubleTag.valueOf(object.position().y));
        position.add(DoubleTag.valueOf(object.position().z));
        spawn.put("Pos", position);
        spawn.remove("Passengers");
        spawn.remove("Vehicle");
        spawn.putString("id", object.parameters().get(SceneNativeEntities.ENTITY_TYPE));
        return spawn;
    }

    void bindSnapshot(SceneObject actual, CompoundTag data, SceneObject expected) {
        SceneNativeEntities.validate(actual);
        Snapshot snapshot = requireSnapshot(expected);
        if (!actual.id().equals(expected.id()) || !actual.position().equals(expected.position())
                || !sameMetadata(snapshot.description, actual) || !content(snapshot.data).equals(content(data)))
            throw new IllegalStateException("Restored entity differs from its immutable snapshot");
        bind(actual.id(), token(expected));
    }

    int recordCount() { return snapshots.size(); }
    int totalBytes() { return totalBytes; }

    private void requireEntity(Entity entity) {
        if (level == null || entity.level() != level || !SceneNativeEntities.eligible(entity))
            throw new IllegalArgumentException("Entity is not an editable member of this level");
    }

    private Snapshot requireSnapshot(SceneObject object) {
        SceneNativeEntities.validate(object);
        Snapshot snapshot = snapshots.get(token(object));
        if (snapshot == null) throw new IllegalStateException("Entity snapshot has expired; capture the entity again");
        if (!sameMetadata(snapshot.description, object)) throw new IllegalArgumentException("Entity snapshot fields do not match token");
        return snapshot;
    }

    private static boolean matches(Snapshot snapshot, SceneObject description, CompoundTag comparable) {
        return sameMetadata(snapshot.description, description) && content(snapshot.data).equals(comparable);
    }

    private static boolean sameMetadata(SceneObject expected, SceneObject actual) {
        if (!expected.typeId().equals(actual.typeId()) || !expected.name().equals(actual.name())
                || !expected.color().equals(actual.color()) || expected.enabled() != actual.enabled()
                || !expected.rotation().equals(actual.rotation()) || !expected.scale().equals(actual.scale())) return false;
        for (var entry : expected.parameters().entrySet())
            if (!entry.getKey().equals(SceneNativeEntities.SNAPSHOT)
                    && !entry.getValue().equals(actual.parameters().get(entry.getKey()))) return false;
        return expected.parameters().size() == actual.parameters().size();
    }

    private static SceneObject envelope(Snapshot snapshot, SceneObject observed) {
        return new SceneObject(observed.id(), snapshot.description.typeId(), snapshot.description.name(),
                observed.position(), snapshot.description.color(), snapshot.description.enabled(), observed.revision(),
                snapshot.description.parameters(), snapshot.description.rotation(), snapshot.description.scale());
    }

    private static UUID token(SceneObject object) {
        return SceneNativeEntities.snapshotToken(object.parameters().get(SceneNativeEntities.SNAPSHOT));
    }

    private static CompoundTag content(CompoundTag data) {
        CompoundTag result = data.copy();
        result.remove("UUID");
        result.remove("Pos");
        return result;
    }

    private static void rejectRelationships(CompoundTag data) {
        if (data.contains("Passengers") || data.contains("Vehicle"))
            throw new IllegalArgumentException("Entity riding relationships require an atomic lifecycle adapter");
    }

    private static void checkDataType(SceneObject object, CompoundTag data) {
        if (data.contains("id") && !data.getStringOr("id", "").equals(object.parameters().get(SceneNativeEntities.ENTITY_TYPE)))
            throw new IllegalArgumentException("Entity snapshot NBT type does not match its descriptor");
    }

    private void bind(UUID entity, UUID token) {
        bindings.put(entity, token);
        while (bindings.size() > MAX_BINDINGS) bindings.remove(bindings.keySet().iterator().next());
    }

    private void evictOldest() {
        Iterator<Map.Entry<UUID, Snapshot>> iterator = snapshots.entrySet().iterator();
        var oldest = iterator.next();
        UUID token = oldest.getKey();
        totalBytes -= oldest.getValue().bytes;
        iterator.remove();
        bindings.values().removeIf(token::equals);
    }

    private static int size(CompoundTag data, int limit) {
        CountingOutputStream counter = new CountingOutputStream(limit);
        try {
            NbtIo.write(data, new DataOutputStream(counter));
            int retainedBytes = Math.max(counter.count, data.sizeInBytes());
            if (retainedBytes < 0 || retainedBytes > limit) throw new IOException("Entity snapshot too large in memory");
            return retainedBytes;
        } catch (IOException exception) {
            throw new IllegalArgumentException("Entity snapshot exceeds byte budget or cannot be serialized", exception);
        }
    }

    private record Snapshot(UUID source, SceneObject description, CompoundTag data, int bytes) {}

    private static final class CountingOutputStream extends OutputStream {
        private final int limit;
        private int count;
        private CountingOutputStream(int limit) { this.limit = limit; }
        @Override public void write(int value) throws IOException { add(1); }
        @Override public void write(byte[] values, int offset, int length) throws IOException { add(length); }
        private void add(int amount) throws IOException {
            if (amount < 0 || amount > limit - count) throw new IOException("Entity snapshot too large");
            count += amount;
        }
    }
}
