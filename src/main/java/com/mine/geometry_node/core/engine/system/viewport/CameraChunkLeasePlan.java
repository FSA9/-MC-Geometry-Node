package com.mine.geometry_node.core.engine.system.viewport;

import net.minecraft.server.level.ServerLevel;

import java.util.HashSet;
import java.util.Set;

/** Immutable ticket coverage, reused while observer chunk boundaries stay unchanged. */
record CameraChunkLeasePlan(Set<Observer> observers, Set<Lease> leases) {
    static final CameraChunkLeasePlan EMPTY = new CameraChunkLeasePlan(Set.of(), Set.of());

    CameraChunkLeasePlan {
        observers = Set.copyOf(observers);
        leases = Set.copyOf(leases);
    }

    CameraChunkLeasePlan update(Set<Observer> next) {
        if (observers.equals(next)) return this;
        if (next.isEmpty()) return EMPTY;
        Set<Lease> desired = new HashSet<>();
        for (Observer observer : next) {
            var loading = new CameraChunkRegions.Region(observer.x, observer.z, observer.viewRadius);
            var simulation = new CameraChunkRegions.Region(observer.x, observer.z, observer.simulationRadius);
            loading.forEach(pos -> {
                desired.add(new Lease(observer.level, pos, false));
                if (simulation.contains((int) pos, (int) (pos >> 32), false)) {
                    desired.add(new Lease(observer.level, pos, true));
                }
            });
        }
        return new CameraChunkLeasePlan(next, desired);
    }

    record Observer(ServerLevel level, int x, int z, int viewRadius, int simulationRadius) {}
    record Lease(ServerLevel level, long pos, boolean simulate) {}
}
