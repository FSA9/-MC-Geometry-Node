package com.mine.geometry_node.core.engine.system.entityindex;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

/** Identity-aware, weak loaded index with bounded admission candidates. Never discovers entities. */
final class EntityIndexLoadedIndex<L, T> {
    private static final int MAX_CANDIDATES = 4096, MAX_ATTEMPTS = 8;
    private final Map<UUID, List<Observation>> observed = new LinkedHashMap<>();
    private final ArrayDeque<Observation> pending = new ArrayDeque<>();
    private final BiPredicate<L, T> available;
    EntityIndexLoadedIndex(BiPredicate<L, T> available) { this.available = Objects.requireNonNull(available); }

    void join(L level, UUID uuid, T entity, boolean candidate) {
        List<Observation> values = observed.computeIfAbsent(uuid, ignored -> new ArrayList<>());
        for (Observation value : values) if (value.entity.get() == entity && value.level == level) {
            if (!candidate) {
                value.departing = false; value.attempts = 0;
                if (value.queued && isAvailable(value)) { pending.remove(value); value.queued = false; }
            }
            return;
        }
        Observation value = new Observation(level, uuid, entity); values.add(value);
        if (candidate || !isAvailable(value)) enqueue(value);
    }
    T current(UUID uuid) {
        List<Observation> values = observed.get(uuid);
        if (values == null) return null;
        values.removeIf(value -> value.retired || value.entity.get() == null);
        if (values.isEmpty()) { observed.remove(uuid); return null; }
        for (int i = values.size() - 1; i >= 0; i--) {
            Observation value = values.get(i);
            if (!value.departing && isAvailable(value)) return value.entity.get();
        }
        return null;
    }
    List<UUID> ids() {
        // Weakly collected confirmed entities must not leave permanent historical UUID keys.
        observed.entrySet().removeIf(entry -> {
            entry.getValue().removeIf(value -> value.retired || value.entity.get() == null);
            return entry.getValue().isEmpty();
        });
        return List.copyOf(observed.keySet());
    }
    void leave(L level, UUID uuid, T entity) {
        for (Observation value : observed.getOrDefault(uuid, List.of()))
            if (value.entity.get() == entity && value.level == level) {
                value.departing = true;
                enqueue(value);
                return;
            }
    }
    void remove(L level, UUID uuid, T entity) {
        List<Observation> values = observed.get(uuid);
        if (values == null) return;
        values.removeIf(value -> {
            if (value.entity.get() == null || value.entity.get() == entity && value.level == level) {
                value.retired = true; return true;
            }
            return false;
        });
        if (values.isEmpty()) observed.remove(uuid);
    }
    void removeLevel(L level) {
        for (UUID uuid : ids()) {
            List<Observation> values = observed.get(uuid);
            values.removeIf(value -> {
                if (value.level == level) { value.retired = true; return true; }
                return false;
            });
            if (values.isEmpty()) observed.remove(uuid);
        }
    }
    void reconcile(int budget, Consumer<T> confirmed) {
        // Requeued entries wait until the next call, even with spare budget this tick.
        int remaining = Math.min(budget, pending.size());
        while (remaining-- > 0) {
            Observation value = pending.removeFirst(); value.queued = false;
            if (value.retired) continue;
            T entity = value.entity.get();
            if (entity == null) { retire(value); continue; }
            value.departing = false;
            if (isAvailable(value)) {
                if (current(value.uuid) == entity) confirmed.accept(entity);
            } else if (++value.attempts >= MAX_ATTEMPTS) retire(value);
            else enqueue(value);
        }
    }
    int candidateCount() { return pending.size(); }
    private boolean isAvailable(Observation value) {
        T entity = value.entity.get();
        return entity != null && available.test(value.level, entity);
    }
    private void enqueue(Observation value) {
        if (value.queued || value.retired) return;
        if (pending.size() >= MAX_CANDIDATES) {
            Observation oldest = pending.removeFirst(); oldest.queued = false; retire(oldest);
        }
        value.queued = true; pending.addLast(value);
    }
    private void retire(Observation value) {
        value.retired = true;
        List<Observation> values = observed.get(value.uuid);
        if (values != null) {
            values.remove(value);
            if (values.isEmpty()) observed.remove(value.uuid);
        }
    }
    private final class Observation {
        final L level; final UUID uuid; final WeakReference<T> entity;
        boolean departing, queued, retired; int attempts;
        Observation(L level, UUID uuid, T entity) {
            this.level = level; this.uuid = uuid; this.entity = new WeakReference<>(entity);
        }
    }
}
