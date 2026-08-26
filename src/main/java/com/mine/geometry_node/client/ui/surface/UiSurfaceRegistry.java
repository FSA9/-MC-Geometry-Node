package com.mine.geometry_node.client.ui.surface;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Runtime registry for user-addressable editor surfaces. */
public final class UiSurfaceRegistry {
    public static final UiSurfaceRegistry INSTANCE = new UiSurfaceRegistry();

    private final Map<UiSurfaceType, BitSet> usedNumbers = new EnumMap<>(UiSurfaceType.class);
    private final Map<UiSurfaceId, Entry> entries = new LinkedHashMap<>();
    private long nextGeneration = 1L;
    private long interactionSerial;

    UiSurfaceRegistry() {
        for (UiSurfaceType type : UiSurfaceType.values()) usedNumbers.put(type, new BitSet());
    }

    public synchronized Registration register(UiSurfaceType type, Object owner) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(owner, "owner");
        BitSet used = usedNumbers.get(type);
        int number = used.nextClearBit(1);
        used.set(number);
        UiSurfaceId id = new UiSurfaceId(type, number);
        long generation = nextGeneration++;
        entries.put(id, new Entry(id, generation, owner));
        return new Registration(this, id, generation);
    }

    public synchronized List<Snapshot> snapshots() {
        return entries.values().stream().map(Entry::snapshot)
                .sorted(Comparator.comparing(Snapshot::id)).toList();
    }

    public synchronized Optional<Snapshot> snapshot(UiSurfaceId id) {
        Entry entry = entries.get(id);
        return entry == null ? Optional.empty() : Optional.of(entry.snapshot());
    }

    public synchronized <T> List<Lease<T>> leases(UiSurfaceType type, Class<T> ownerType) {
        List<Lease<T>> result = new ArrayList<>();
        for (Entry entry : entries.values()) {
            if (entry.id.type() == type && ownerType.isInstance(entry.owner)) {
                result.add(new Lease<>(entry.id, entry.generation, ownerType.cast(entry.owner), entry.visible,
                        entry.interactionSerial));
            }
        }
        result.sort(Comparator.comparing(Lease::id));
        return List.copyOf(result);
    }

    public synchronized <T> Optional<Lease<T>> lease(UiSurfaceId id, Class<T> ownerType) {
        Entry entry = entries.get(id);
        if (entry == null || !ownerType.isInstance(entry.owner)) return Optional.empty();
        return Optional.of(new Lease<>(entry.id, entry.generation, ownerType.cast(entry.owner), entry.visible,
                entry.interactionSerial));
    }

    public synchronized boolean isCurrent(UiSurfaceId id, long generation, Object owner) {
        Entry entry = entries.get(id);
        return entry != null && entry.generation == generation && entry.owner == owner;
    }

    private synchronized void setVisible(UiSurfaceId id, long generation, boolean visible) {
        Entry entry = current(id, generation);
        if (entry != null) entry.visible = visible;
    }

    private synchronized void markInteracted(UiSurfaceId id, long generation) {
        Entry entry = current(id, generation);
        if (entry != null) entry.interactionSerial = ++interactionSerial;
    }

    private synchronized void unregister(UiSurfaceId id, long generation) {
        Entry entry = current(id, generation);
        if (entry == null) return;
        entries.remove(id);
        usedNumbers.get(id.type()).clear(id.number());
    }

    private Entry current(UiSurfaceId id, long generation) {
        Entry entry = entries.get(id);
        return entry != null && entry.generation == generation ? entry : null;
    }

    public record Snapshot(UiSurfaceId id, long generation, boolean visible, long interactionSerial) {}

    public record Lease<T>(UiSurfaceId id, long generation, T owner, boolean visible, long interactionSerial) {
        public boolean isCurrent() {
            return UiSurfaceRegistry.INSTANCE.isCurrent(id, generation, owner);
        }
    }

    public static final class Registration implements AutoCloseable {
        private final UiSurfaceRegistry registry;
        private final UiSurfaceId id;
        private final long generation;
        private boolean closed;

        private Registration(UiSurfaceRegistry registry, UiSurfaceId id, long generation) {
            this.registry = registry;
            this.id = id;
            this.generation = generation;
        }

        public UiSurfaceId id() { return id; }
        public long generation() { return generation; }
        public void setVisible(boolean visible) { if (!closed) registry.setVisible(id, generation, visible); }
        public void markInteracted() { if (!closed) registry.markInteracted(id, generation); }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            registry.unregister(id, generation);
        }
    }

    private static final class Entry {
        private final UiSurfaceId id;
        private final long generation;
        private final Object owner;
        private boolean visible;
        private long interactionSerial;

        private Entry(UiSurfaceId id, long generation, Object owner) {
            this.id = id;
            this.generation = generation;
            this.owner = owner;
        }

        private Snapshot snapshot() {
            return new Snapshot(id, generation, visible, interactionSerial);
        }
    }
}
