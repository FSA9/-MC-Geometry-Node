package com.mine.geometry_node.core.engine.graph.debug;

import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

final class DebugObservationRegions {
    private DebugObservationRegions() {
    }

    static List<AABB> merge(List<DebugObserverArea> observers) {
        List<AABB> regions = new ArrayList<>();
        for (DebugObserverArea observer : observers) {
            AABB pending = observer.bounds();
            boolean merged;
            do {
                merged = false;
                for (int i = 0; i < regions.size(); i++) {
                    AABB existing = regions.get(i);
                    AABB combined = union(existing, pending);
                    if (!existing.intersects(pending) || volume(combined) > volume(existing) + volume(pending)) {
                        continue;
                    }
                    pending = combined;
                    regions.remove(i);
                    merged = true;
                    break;
                }
            } while (merged);
            regions.add(pending);
        }
        return List.copyOf(regions);
    }

    private static AABB union(AABB left, AABB right) {
        return new AABB(
                Math.min(left.minX, right.minX), Math.min(left.minY, right.minY), Math.min(left.minZ, right.minZ),
                Math.max(left.maxX, right.maxX), Math.max(left.maxY, right.maxY), Math.max(left.maxZ, right.maxZ)
        );
    }

    private static double volume(AABB bounds) {
        return bounds.getXsize() * bounds.getYsize() * bounds.getZsize();
    }
}
