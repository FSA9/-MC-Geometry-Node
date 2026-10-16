package com.mine.geometry_node.client.runtime.game.render;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Bounded publication leases. Retired in-flight images continue to count toward capacity. */
final class FramePublicationPool<T> {
    private final int capacity;
    private final Consumer<T> dispose;
    private final List<Lease<T>> leases = new ArrayList<>();

    FramePublicationPool(int capacity, Consumer<T> dispose) {
        if (capacity < 2) throw new IllegalArgumentException("Publication needs a current and a replacement image");
        this.capacity = capacity;
        this.dispose = dispose;
    }

    synchronized Lease<T> acquire(Predicate<T> compatible, Supplier<T> allocate) {
        for (var iterator = leases.iterator(); iterator.hasNext();) {
            Lease<T> lease = iterator.next();
            if (!lease.busy && (lease.retired || !compatible.test(lease.resource))) {
                dispose.accept(lease.resource);
                iterator.remove();
            }
        }
        for (Lease<T> lease : leases) {
            if (!lease.busy && !lease.retired) { lease.busy = true; return lease; }
        }
        if (leases.size() >= capacity) return null;
        Lease<T> lease = new Lease<>(allocate.get());
        leases.add(lease);
        return lease;
    }

    synchronized void release(Lease<T> lease) {
        if (!leases.contains(lease) || !lease.busy) return;
        lease.busy = false;
        if (lease.retired) {
            leases.remove(lease);
            dispose.accept(lease.resource);
        }
    }

    synchronized void clear() {
        for (var iterator = leases.iterator(); iterator.hasNext();) {
            Lease<T> lease = iterator.next();
            lease.retired = true;
            if (!lease.busy) { dispose.accept(lease.resource); iterator.remove(); }
        }
    }

    static final class Lease<T> {
        final T resource;
        private boolean busy = true;
        private boolean retired;
        Lease(T resource) { this.resource = resource; }
    }
}
