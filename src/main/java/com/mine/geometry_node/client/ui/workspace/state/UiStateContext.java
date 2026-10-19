package com.mine.geometry_node.client.ui.workspace.state;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mine.geometry_node.client.ui.workspace.persistence.EditorSessionState;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Snapshot access to one stable area owner. Memory deliberately outlives UI instances. */
public final class UiStateContext {
    private static final Gson GSON = new Gson();
    private static final class OwnerMemory {
        final Map<String, Map<String, EditorSessionState.StateSlot>> scopes = new LinkedHashMap<>();
        boolean revoked;
    }
    private static final Map<String, OwnerMemory> MEMORY = new HashMap<>();
    private final EditorSessionState.AreaState state;
    private final Runnable changed;
    private final OwnerMemory ownerMemory;
    private static final int MAX_SCOPES = 256, MAX_KEYS_PER_SCOPE = 128, MAX_PAYLOAD_CHARS = 65536;

    private UiStateContext(EditorSessionState.AreaState state, Runnable changed) {
        this(state, changed, false);
    }

    private UiStateContext(EditorSessionState.AreaState state, Runnable changed, boolean unmanaged) {
        this.state = Objects.requireNonNull(state);
        synchronized (MEMORY) {
            try { state.ownerId = UUID.fromString(state.ownerId).toString(); }
            catch (RuntimeException ignored) { state.ownerId = UUID.randomUUID().toString(); }
            if (state.slots == null) state.slots = new LinkedHashMap<>();
            ownerMemory = unmanaged ? new OwnerMemory()
                    : MEMORY.computeIfAbsent(state.ownerId, ignored -> new OwnerMemory());
        }
        this.changed = changed == null ? () -> {} : changed;
    }

    public static UiStateContext forArea(EditorSessionState.AreaState state, Runnable changed) {
        return new UiStateContext(state, changed);
    }

    public static UiStateContext ephemeral() {
        return new UiStateContext(new EditorSessionState.AreaState(), null, true);
    }

    /** Explicit area removal only; UI teardown must keep managed process memory. */
    public static void forgetOwner(String ownerId) {
        synchronized (MEMORY) {
            OwnerMemory removed = MEMORY.remove(ownerId);
            if (removed != null) {
                removed.revoked = true;
                removed.scopes.clear();
            }
        }
    }

    /** Serializes slots under the same lock as writes. Layout access stays on the UI thread. */
    public static EditorSessionState snapshotSession(EditorSessionState session) {
        synchronized (MEMORY) {
            return GSON.fromJson(GSON.toJson(session), EditorSessionState.class);
        }
    }

    /** True even for corrupt or newer-version slots, preventing legacy reseeding. */
    public boolean contains(UiStateKey<?> key, String scope) {
        Objects.requireNonNull(scope, "scope");
        if (scope.length() > 1024) throw new IllegalArgumentException("scope too long");
        synchronized (MEMORY) {
            if (ownerMemory.revoked) return false;
            var entries = slots(key).get(scope);
            return entries != null && entries.containsKey(key.id());
        }
    }

    public <T> T read(UiStateKey<T> key, String scope) {
        Objects.requireNonNull(scope, "scope");
        if (scope.length() > 1024) throw new IllegalArgumentException("scope too long");
        synchronized (MEMORY) {
            if (ownerMemory.revoked) return normalize(key, key.defaults().get());
            var scopes = slots(key);
            var entries = scopes.get(scope);
            var slot = entries == null ? null : entries.get(key.id());
            if (slot != null && slot.version > 0 && slot.version <= key.version()) {
                try {
                    if (slot.value == null || slot.value.isJsonNull()) throw new IllegalArgumentException("null payload");
                    JsonElement payload = slot.value.deepCopy();
                    if (slot.version < key.version()) payload = key.migration().migrate(slot.version, payload);
                    if (payload == null || payload.isJsonNull()) throw new IllegalArgumentException("unsupported migration");
                    if (payload.toString().length() > MAX_PAYLOAD_CHARS)
                        throw new IllegalArgumentException("State payload too large");
                    return normalize(key, GSON.fromJson(payload, key.type()));
                }
                catch (RuntimeException ignored) { /* Corrupt payload: safe defaults. */ }
            }
            return normalize(key, key.defaults().get());
        }
    }

    public <T> void write(UiStateKey<T> key, String scope, T value) {
        Objects.requireNonNull(scope, "scope");
        if (scope.length() > 1024) throw new IllegalArgumentException("scope too long");
        boolean notify = false;
        synchronized (MEMORY) {
            if (ownerMemory.revoked) return;
            var scopes = slots(key);
            if (!scopes.containsKey(scope) && scopes.size() >= MAX_SCOPES)
                throw new IllegalStateException("Too many state scopes");
            var entries = scopes.computeIfAbsent(scope, ignored -> new LinkedHashMap<>());
            if (!entries.containsKey(key.id()) && entries.size() >= MAX_KEYS_PER_SCOPE)
                throw new IllegalStateException("Too many state keys");
            var old = entries.get(key.id());
            if (old != null && old.version > key.version()) return;
            JsonElement snapshot = GSON.toJsonTree(normalize(key, value), key.type());
            if (snapshot.toString().length() > MAX_PAYLOAD_CHARS)
                throw new IllegalArgumentException("State payload too large");
            if (old != null && old.version == key.version() && Objects.equals(old.value, snapshot)) return;
            entries.put(key.id(), new EditorSessionState.StateSlot(key.version(), snapshot));
            notify = key.lifetime() == UiStateLifetime.PERSISTENT;
        }
        if (notify) changed.run();
    }

    private Map<String, Map<String, EditorSessionState.StateSlot>> slots(UiStateKey<?> key) {
        return key.lifetime() == UiStateLifetime.PERSISTENT ? state.slots
                : ownerMemory.scopes;
    }

    private static <T> T normalize(UiStateKey<T> key, T value) {
        T copy = GSON.fromJson(GSON.toJsonTree(value == null ? key.defaults().get() : value, key.type()), key.type());
        T clean = Objects.requireNonNull(key.sanitize().apply(copy), "sanitized state");
        return GSON.fromJson(GSON.toJsonTree(clean, key.type()), key.type());
    }
}
