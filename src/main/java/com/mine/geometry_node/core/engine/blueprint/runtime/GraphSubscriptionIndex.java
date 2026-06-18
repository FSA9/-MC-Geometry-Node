package com.mine.geometry_node.core.engine.blueprint.runtime;

import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Runtime subscription index for bound blueprint graphs.
 * RuntimeGraphIndex owns graph-local node lookup; this class only answers which
 * bound graph IDs are candidates for an event type in a given scope.
 */
final class GraphSubscriptionIndex {
    private final Map<String, Set<String>> globalEventGraphs = new HashMap<>();
    private final Map<String, Map<Entity, Set<String>>> entityEventGraphs = new HashMap<>();
    private final Map<String, RuntimeGraphIndex> registeredGlobalGraphs = new HashMap<>();
    private final Map<Entity, Map<String, RuntimeGraphIndex>> registeredEntityGraphs = new WeakHashMap<>();

    void registerGlobalGraph(String graphId, RuntimeGraphIndex index) {
        RuntimeGraphIndex previous = registeredGlobalGraphs.get(graphId);
        if (previous == index) return;
        if (previous != null) {
            removeGraphFromLookup(globalEventGraphs, graphId, previous);
        }
        registeredGlobalGraphs.put(graphId, index);
        for (String eventType : index.getNodeTypes()) {
            globalEventGraphs.computeIfAbsent(eventType, ignored -> new HashSet<>()).add(graphId);
        }
    }

    void unregisterGlobalGraph(String graphId, @Nullable RuntimeGraphIndex index) {
        RuntimeGraphIndex registeredIndex = registeredGlobalGraphs.remove(graphId);
        RuntimeGraphIndex cleanupIndex = registeredIndex != null ? registeredIndex : index;
        if (cleanupIndex == null) {
            removeGraphFromLookup(globalEventGraphs, graphId);
            return;
        }
        for (String eventType : cleanupIndex.getNodeTypes()) {
            removeGraph(globalEventGraphs, eventType, graphId);
        }
    }

    void registerEntityGraph(Entity entity, String graphId, RuntimeGraphIndex index) {
        Map<String, RuntimeGraphIndex> entityIndexes = registeredEntityGraphs.computeIfAbsent(entity, ignored -> new HashMap<>());
        RuntimeGraphIndex previous = entityIndexes.get(graphId);
        if (previous == index) return;
        if (previous != null) {
            removeEntityGraphFromLookup(entity, graphId, previous);
        }
        entityIndexes.put(graphId, index);
        for (String eventType : index.getNodeTypes()) {
            entityEventGraphs
                    .computeIfAbsent(eventType, ignored -> new WeakHashMap<>())
                    .computeIfAbsent(entity, ignored -> new HashSet<>())
                    .add(graphId);
        }
    }

    void unregisterEntityGraph(Entity entity, String graphId, @Nullable RuntimeGraphIndex index) {
        Map<String, RuntimeGraphIndex> entityIndexes = registeredEntityGraphs.get(entity);
        RuntimeGraphIndex registeredIndex = entityIndexes != null ? entityIndexes.remove(graphId) : null;
        if (entityIndexes != null && entityIndexes.isEmpty()) {
            registeredEntityGraphs.remove(entity);
        }

        RuntimeGraphIndex cleanupIndex = registeredIndex != null ? registeredIndex : index;
        if (cleanupIndex == null) {
            removeEntityGraphFromLookup(entity, graphId);
            return;
        }
        for (String eventType : cleanupIndex.getNodeTypes()) {
            removeEntityGraph(eventType, entity, graphId);
        }
    }

    Set<String> globalGraphsFor(String eventType) {
        Set<String> graphIds = globalEventGraphs.get(eventType);
        return graphIds != null ? Set.copyOf(graphIds) : Collections.emptySet();
    }

    Set<String> entityGraphsFor(Entity entity, String eventType) {
        Map<Entity, Set<String>> entities = entityEventGraphs.get(eventType);
        if (entities == null) return Collections.emptySet();
        Set<String> graphIds = entities.get(entity);
        return graphIds != null ? Set.copyOf(graphIds) : Collections.emptySet();
    }

    private static void removeGraphFromLookup(Map<String, Set<String>> lookup, String graphId) {
        lookup.entrySet().removeIf(entry -> {
            entry.getValue().remove(graphId);
            return entry.getValue().isEmpty();
        });
    }

    private static void removeGraphFromLookup(Map<String, Set<String>> lookup, String graphId, RuntimeGraphIndex index) {
        for (String eventType : index.getNodeTypes()) {
            removeGraph(lookup, eventType, graphId);
        }
    }

    private void removeEntityGraphFromLookup(Entity entity, String graphId) {
        entityEventGraphs.entrySet().removeIf(entry -> {
            Map<Entity, Set<String>> entities = entry.getValue();
            Set<String> graphIds = entities.get(entity);
            if (graphIds != null) {
                graphIds.remove(graphId);
                if (graphIds.isEmpty()) {
                    entities.remove(entity);
                }
            }
            return entities.isEmpty();
        });
    }

    private void removeEntityGraphFromLookup(Entity entity, String graphId, RuntimeGraphIndex index) {
        for (String eventType : index.getNodeTypes()) {
            removeEntityGraph(eventType, entity, graphId);
        }
    }

    private static void removeGraph(Map<String, Set<String>> lookup, String eventType, String graphId) {
        Set<String> graphIds = lookup.get(eventType);
        if (graphIds == null) return;
        graphIds.remove(graphId);
        if (graphIds.isEmpty()) {
            lookup.remove(eventType);
        }
    }

    private void removeEntityGraph(String eventType, Entity entity, String graphId) {
        Map<Entity, Set<String>> entities = entityEventGraphs.get(eventType);
        if (entities == null) return;
        Set<String> graphIds = entities.get(entity);
        if (graphIds != null) {
            graphIds.remove(graphId);
            if (graphIds.isEmpty()) {
                entities.remove(entity);
            }
        }
        if (entities.isEmpty()) {
            entityEventGraphs.remove(eventType);
        }
    }
}
