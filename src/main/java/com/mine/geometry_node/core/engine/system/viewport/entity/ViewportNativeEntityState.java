package com.mine.geometry_node.core.engine.system.viewport.entity;

import com.mine.geometry_node.core.engine.system.scene.SceneChange;
import com.mine.geometry_node.core.engine.system.scene.SceneDocument;
import com.mine.geometry_node.core.engine.system.scene.SceneObject;
import com.mine.geometry_node.core.engine.system.scene.SceneObjectTypes;
import com.mine.geometry_node.core.utils.nbt.EntityNbtCompat;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import net.minecraft.world.entity.Entity;
import java.util.*;
import java.util.function.LongSupplier;
import java.util.function.BiPredicate;

/** Transient, bounded native observations and full-NBT deletion proofs. Never persisted as authored objects. */
final class ViewportNativeEntityState {
    private static final int MAX_OBSERVATIONS = 4096;
    static final int MAX_NATIVE_BYTES = 256 * 1024;
    static final int MAX_RETAINED_BYTES = 32 * 1024 * 1024;
    private final Map<UUID, Observation> observed = new LinkedHashMap<>(16, .75f, true);
    private final Map<UUID, Tombstone> deleted = new LinkedHashMap<>(16, .75f, true);
    private final LongSupplier nextRevision;
    private long revision;
    private int retainedBytes;
    private Set<UUID> protectedTombstones = Set.of();

    ViewportNativeEntityState() { nextRevision = () -> Math.incrementExact(revision); }
    ViewportNativeEntityState(long revision, LongSupplier nextRevision) {
        this.revision = revision;
        this.nextRevision = nextRevision;
    }

    long revision() { return revision; }
    void protectTombstones(Collection<UUID> ids) { protectedTombstones = Set.copyOf(ids); }
    List<Observation> observations() { return List.copyOf(observed.values()); }
    Map<UUID, Tombstone> tombstones() { return Collections.unmodifiableMap(deleted); }
    Tombstone tombstone(UUID id) { return deleted.get(id); }

    SceneObject observe(Entity entity, ViewportNativeEntityEditing.NativeAdapter adapter) {
        SceneObject captured = adapter.capture(entity, 0);
        if (!entity.getUUID().equals(captured.id()) || !adapter.typeId().equals(captured.typeId()))
            throw new IllegalStateException("Native adapter returned an inconsistent identity");
        return observe(captured, EntityNbtCompat.saveWithoutId(entity), entity);
    }
    SceneObject observe(SceneObject live) { return observe(live, null, null); }
    SceneObject observe(SceneObject live, CompoundTag nativeData) { return observe(live, nativeData, null); }

    SceneObject observe(SceneObject live, CompoundTag nativeData, Entity entity) {
        SceneObjectTypes.Type type = SceneObjectTypes.require(live.typeId());
        if (type.storage() != SceneObjectTypes.Storage.NATIVE)
            throw new IllegalArgumentException("Not a registered native object");
        int bytes = nativeSize(nativeData); // Bound before retaining/copying arbitrary external entity NBT.
        Observation previous = observed.get(live.id());
        // Non-editor metadata matters too: undo must not overwrite a later external native change.
        boolean same = previous != null && live.sameContent(previous.object())
                && Objects.equals(nativeData, previous.nativeData())
                && (entity == null || previous.entity() == entity);
        boolean sameLifecycle = type.historyPolicy() == SceneObjectTypes.HistoryPolicy.LIFECYCLE && previous != null && previous.entity() == entity
                && previous.object().typeId().equals(live.typeId());
        SceneObject current = same ? previous.object() : live.withRevision(sameLifecycle ? previous.object().revision() : advance());
        Tombstone tombstone = deleted.get(current.id());
        int replacedBytes = (previous == null ? 0 : previous.bytes()) + (tombstone == null ? 0 : tombstone.bytes());
        makeRoom(current.id(), bytes - replacedBytes);
        observed.put(current.id(), new Observation(current, same ? previous.nativeData() : copy(nativeData), entity, bytes));
        deleted.remove(current.id());
        retainedBytes += bytes - replacedBytes;
        while (observed.size() > MAX_OBSERVATIONS) evictObservation(current.id());
        return current;
    }

    /** Called only for a proven removal, never simply because an entity left a bounded query or unloaded. */
    Tombstone missing(UUID id) {
        Observation previous = observed.remove(id);
        if (previous == null) return deleted.get(id);
        Tombstone result = new Tombstone(previous.object(), previous.nativeData(), advance(), previous.bytes());
        deleted.put(id, result);
        while (deleted.size() > SceneDocument.MAX_TOMBSTONES) {
            UUID oldest = deleted.keySet().stream().filter(key -> !protectedTombstones.contains(key)).findFirst()
                    .orElseThrow(() -> new IllegalStateException("Too many protected native tombstones"));
            retainedBytes -= deleted.remove(oldest).bytes();
        }
        return result;
    }

    /** Validate the entire batch before any entity is changed. Absent IDs need an exact retained deletion proof. */
    void validate(List<SceneChange> changes, Map<UUID, SceneObject> live) {
        validate(changes, live, SceneObjectTypes::sameHistoryState);
    }
    void validate(List<SceneChange> changes, Map<UUID, SceneObject> live,
                  BiPredicate<SceneObject, SceneObject> restoreMatches) {
        Set<UUID> seen = new HashSet<>();
        for (SceneChange change : changes) {
            if (!seen.add(change.id())) throw new IllegalArgumentException("Duplicate native entity in batch");
            SceneObject current = live.get(change.id());
            Tombstone tombstone = deleted.get(change.id());
            SceneObject replacement = change.replacement();
            if (current != null) {
                if (change.expectedRevision() != current.revision()) throw conflict();
            } else if (change.expectedRevision() == -1) {
                if (replacement == null || tombstone != null || observed.containsKey(change.id())) throw conflict();
            } else {
                if (replacement == null || tombstone == null || tombstone.nativeData() == null
                        || change.expectedRevision() != tombstone.version()) throw conflict();
                // A restore is an identity restoration, not a way to edit hidden native metadata or editor values.
                if (!restoreMatches.test(replacement, tombstone.object()))
                    throw new IllegalArgumentException("Native entity restore must match the deleted object");
            }
            if (replacement == null) continue;
            if (SceneObjectTypes.require(replacement.typeId()).storage() != SceneObjectTypes.Storage.NATIVE
                    || current != null && !replacement.typeId().equals(current.typeId()))
                throw new IllegalArgumentException("Native object type cannot change");
            SceneObjectTypes.validate(replacement);
        }
    }

    private static IllegalStateException conflict() {
        return new IllegalStateException("Native entity changed or its deletion proof expired; refresh and retry");
    }
    private long advance() {
        long next = nextRevision.getAsLong();
        if (next <= revision) throw new IllegalStateException("Native entity revision must advance");
        return revision = next;
    }
    private static CompoundTag copy(CompoundTag data) { return data == null ? null : data.copy(); }
    /** Reject an oversized protected batch before spawning/removing its first real entity. */
    void validateBatchBudget(Map<UUID, CompoundTag> replacements) {
        long bytes = 0;
        for (UUID id : protectedTombstones) {
            if (replacements.containsKey(id)) bytes += nativeSize(replacements.get(id));
            else {
                Observation observation = observed.get(id);
                Tombstone tombstone = deleted.get(id);
                bytes += observation != null ? observation.bytes() : tombstone == null ? 0 : tombstone.bytes();
            }
            if (bytes > MAX_RETAINED_BYTES)
                throw new IllegalArgumentException("Native batch exceeds retained NBT budget");
        }
    }
    int retainedBytes() { return retainedBytes; }
    private void makeRoom(UUID id, int additionalBytes) {
        while (retainedBytes > MAX_RETAINED_BYTES - additionalBytes) {
            UUID liveVictim = observed.keySet().stream().filter(key -> !key.equals(id) && !protectedTombstones.contains(key))
                    .findFirst().orElse(null);
            UUID deletedVictim = deleted.keySet().stream().filter(key -> !key.equals(id) && !protectedTombstones.contains(key))
                    .findFirst().orElse(null);
            if (deletedVictim != null && (liveVictim == null
                    || deleted.get(deletedVictim).version() < observed.get(liveVictim).object().revision()))
                retainedBytes -= deleted.remove(deletedVictim).bytes();
            else if (liveVictim != null) retainedBytes -= observed.remove(liveVictim).bytes();
            else throw new IllegalArgumentException("Native batch exceeds retained NBT budget");
        }
    }
    private void evictObservation(UUID id) {
        UUID oldest = observed.keySet().stream().filter(key -> !key.equals(id) && !protectedTombstones.contains(key))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Native observation budget exceeded"));
        retainedBytes -= observed.remove(oldest).bytes();
    }
    private static int nativeSize(CompoundTag data) {
        if (data == null) return 0;
        class BoundedOutput extends OutputStream {
            int count;
            @Override public void write(int value) throws IOException { add(1); }
            @Override public void write(byte[] bytes, int offset, int length) throws IOException { add(length); }
            private void add(int length) throws IOException {
                if (length < 0 || length > MAX_NATIVE_BYTES - count) throw new IOException("Native NBT budget exceeded");
                count += length;
            }
        }
        BoundedOutput output = new BoundedOutput();
        try {
            NbtIo.write(data, new DataOutputStream(output));
            int retained = Math.max(output.count, data.sizeInBytes());
            if(retained<0||retained>MAX_NATIVE_BYTES)throw new IOException("Native NBT memory budget exceeded");
            return retained;
        } catch (IOException failure) {
            throw new IllegalArgumentException("Native entity NBT exceeds 256 KiB budget", failure);
        }
    }
    record Observation(SceneObject object, CompoundTag nativeData, Entity entity, int bytes) {}
    record Tombstone(SceneObject object, CompoundTag nativeData, long version, int bytes) {}
}
