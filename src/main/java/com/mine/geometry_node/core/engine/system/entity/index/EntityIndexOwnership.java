package com.mine.geometry_node.core.engine.system.entity.index;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Server-thread admission proof. Persisted UUIDs and pre-join candidates confer no ownership. */
final class EntityIndexOwnership<T> {
    private final Map<UUID, Owner<T>> owners = new HashMap<>();
    private final ReferenceQueue<T> collected = new ReferenceQueue<>();

    /** Call only after successful admission or a confirmed tracking start, including hidden successors. */
    void admit(UUID id, T entity) {
        Objects.requireNonNull(id);
        Objects.requireNonNull(entity);
        expungeCollected();
        Owner<T> previous = owners.get(id);
        if (previous == null || previous.get() != entity)
            owners.put(id, new Owner<>(id, entity, collected));
    }

    /** Tracking end must retain this proof until actual removal or replacement admission. */
    boolean ownsRemoval(UUID id, T entity) {
        Objects.requireNonNull(id);
        Objects.requireNonNull(entity);
        expungeCollected();
        Owner<T> owner = owners.get(id);
        return owner != null && owner.get() == entity;
    }

    void forget(UUID id) {
        expungeCollected();
        owners.remove(Objects.requireNonNull(id));
    }

    /** A late old-instance removal must not forget the admitted successor. */
    void forget(UUID id, T entity) {
        if (ownsRemoval(id, entity)) owners.remove(id);
    }

    private void expungeCollected() {
        Owner<?> owner;
        while ((owner = (Owner<?>) collected.poll()) != null) owners.remove(owner.id, owner);
    }

    private static final class Owner<T> extends WeakReference<T> {
        private final UUID id;

        private Owner(UUID id, T entity, ReferenceQueue<T> collected) {
            super(entity, collected);
            this.id = id;
        }
    }
}
