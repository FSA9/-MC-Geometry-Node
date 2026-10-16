package com.mine.geometry_node.client.runtime.game;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Sparse overflow slots for vanilla's player-centered ring. Negative IDs cannot
 * alias its nonnegative array indices. Native load/unload/light callbacks still run. */
public final class EditChunkCache<T> {
    private final ConcurrentHashMap<Long, Slot<T>> byPosition = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, Slot<T>> byIndex = new ConcurrentHashMap<>();
    private final AtomicInteger nextIndex = new AtomicInteger(-1);
    private volatile Region player = new Region(0, 0, 5);
    private volatile Region camera;
    private volatile boolean enabled;

    public boolean enabled() { return enabled; }
    public void playerCenter(int x, int z) { player = new Region(x, z, player.radius); prune(); }
    public void playerRadius(int radius) { player = new Region(player.x, player.z, Math.max(2, radius) + 3); prune(); }
    public void camera(boolean active, int x, int z, int radius) {
        if (active) enabled = true;
        camera = active ? new Region(x, z, Math.max(2, Math.min(32, radius)) + 3) : null;
        prune();
    }
    public boolean accepts(int x, int z) {
        Region other = camera;
        return enabled && (player.contains(x, z) || (other != null && other.contains(x, z)));
    }
    public boolean has(int x, int z) {
        Slot<T> slot = byPosition.get(key(x, z));
        return slot != null && slot.value.get() != null;
    }
    public int index(int x, int z, int nativeIndex, boolean nativeRange, boolean nativeContainsChunk) {
        if (nativeContainsChunk) return nativeIndex;
        Slot<T> existing = byPosition.get(key(x, z));
        if (existing != null && (existing.value.get() != null || !nativeRange)) return existing.index;
        if (nativeRange) return nativeIndex;
        return slot(x, z).index;
    }
    public T get(int index) { Slot<T> slot = byIndex.get(index); return slot == null ? null : slot.value.get(); }
    public T replace(int index, T value) {
        Slot<T> slot = byIndex.get(index);
        if (slot == null) throw new IllegalStateException("Missing camera chunk slot " + index);
        return slot.value.getAndSet(value);
    }
    public boolean drop(int index, T expected) {
        Slot<T> slot = byIndex.get(index);
        return slot != null && slot.value.compareAndSet(expected, null);
    }
    public void retain(int x, int z, T value) { slot(x, z).value.set(value); }
    private Slot<T> slot(int x, int z) {
        return byPosition.computeIfAbsent(key(x, z), ignored -> {
            Slot<T> slot = new Slot<>(nextIndex.getAndDecrement(), x, z);
            byIndex.put(slot.index, slot);
            return slot;
        });
    }
    private void prune() {
        byPosition.forEach((key, slot) -> {
            // Loaded chunks live until the server's forget packet, even after
            // centers change. Never discard data just because an acknowledgement arrived.
            if (!accepts(slot.x, slot.z) && slot.value.get() == null && byPosition.remove(key, slot)) {
                byIndex.remove(slot.index, slot);
            }
        });
    }
    private static long key(int x, int z) { return (x & 0xffffffffL) | ((long) z << 32); }
    private record Region(int x, int z, int radius) {
        boolean contains(int cx, int cz) { return Math.abs((long) cx - x) <= radius && Math.abs((long) cz - z) <= radius; }
    }
    private static final class Slot<T> {
        final int index, x, z;
        final AtomicReference<T> value = new AtomicReference<>();
        Slot(int index, int x, int z) { this.index = index; this.x = x; this.z = z; }
    }
}
