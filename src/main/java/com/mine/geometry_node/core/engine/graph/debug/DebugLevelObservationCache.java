package com.mine.geometry_node.core.engine.graph.debug;

import com.mine.geometry_node.core.engine.graph.debug.PathfindingDebugProvider.Subject;
import com.mine.geometry_node.core.engine.graph.debug.geometry.GeometryDebugElement;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

import java.util.List;

final class DebugLevelObservationCache {
    private final InteractionDebugProvider interactionProvider = new InteractionDebugProvider();
    private final PathfindingDebugProvider pathfindingProvider = new PathfindingDebugProvider();
    private List<GeometryDebugElement> interactions = List.of();
    private List<Subject> pathfinding = List.of();
    private long interactionTick = Long.MIN_VALUE;
    private long pathfindingTick = Long.MIN_VALUE;

    void refresh(ServerLevel level,
                 List<DebugObserverArea> interactionObservers,
                 List<DebugObserverArea> pathObservers,
                 PathDebugStateStore pathState,
                 boolean refreshInteraction,
                 boolean refreshPathfinding,
                 boolean force,
                 int maxPathSubjectsPerObserver) {
        long tick = level.getGameTime();
        if (refreshInteraction && !interactionObservers.isEmpty() && (force || interactionTick != tick)) {
            List<AABB> regions = DebugObservationRegions.merge(interactionObservers);
            interactions = interactionProvider.collect(level, regions);
            interactionTick = tick;
        } else if (refreshInteraction && interactionObservers.isEmpty()) {
            interactions = List.of();
        }
        if (refreshPathfinding && !pathObservers.isEmpty() && (force || pathfindingTick != tick)) {
            List<AABB> regions = DebugObservationRegions.merge(pathObservers);
            pathfinding = pathfindingProvider.collect(
                    level, regions, pathObservers, pathState, maxPathSubjectsPerObserver);
            pathfindingTick = tick;
        } else if (refreshPathfinding && pathObservers.isEmpty()) {
            pathfinding = List.of();
        }
    }

    List<GeometryDebugElement> interactions() {
        return interactions;
    }

    List<Subject> pathfinding() {
        return pathfinding;
    }

    void clearPathfinding() {
        pathfinding = List.of();
        pathfindingTick = Long.MIN_VALUE;
    }

    void invalidatePathfinding() {
        pathfindingTick = Long.MIN_VALUE;
    }

    boolean needsPathfindingRefresh() {
        return pathfindingTick == Long.MIN_VALUE;
    }

    void clearInteractions() {
        interactions = List.of();
        interactionTick = Long.MIN_VALUE;
    }
}
