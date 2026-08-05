package com.mine.geometry_node.core.engine.blueprint.event.subscription;

import com.mine.geometry_node.core.engine.blueprint.event.precheck.EventPrecheckRegistry;
import com.mine.geometry_node.core.engine.blueprint.runtime.RuntimeGraphIndex;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Event subscription index for bound blueprint graphs.
 * RuntimeGraphIndex owns graph-local node lookup; this class expands bound graphs
 * into cached event-node subscriptions and keeps graph-level lookups for
 * stateful/special dispatchers.
 */
public final class GraphSubscriptionIndex {
    private final Map<String, Set<String>> globalEventGraphs = new HashMap<>();
    private final Map<String, Map<Entity, Set<String>>> entityEventGraphs = new HashMap<>();
    private final Map<String, List<EventSubscription>> globalEventSubscriptions = new HashMap<>();
    private final Map<String, Map<Entity, List<EventSubscription>>> entityEventSubscriptions = new HashMap<>();
    private final Map<String, RuntimeGraphIndex> registeredGlobalGraphs = new HashMap<>();
    private final Map<Entity, Map<String, RuntimeGraphIndex>> registeredEntityGraphs = new WeakHashMap<>();

    public void registerGlobalGraph(String graphId, RuntimeGraphIndex index) {
        RuntimeGraphIndex previous = registeredGlobalGraphs.get(graphId);
        if (previous == index) return;
        if (previous != null) {
            removeGraphFromLookup(globalEventGraphs, graphId, previous);
            removeGraphSubscriptionsFromLookup(globalEventSubscriptions, graphId, previous);
        }
        registeredGlobalGraphs.put(graphId, index);
        addGraphSubscriptions(globalEventGraphs, globalEventSubscriptions, graphId, index);
    }

    public void unregisterGlobalGraph(String graphId, @Nullable RuntimeGraphIndex index) {
        RuntimeGraphIndex registeredIndex = registeredGlobalGraphs.remove(graphId);
        RuntimeGraphIndex cleanupIndex = registeredIndex != null ? registeredIndex : index;
        if (cleanupIndex == null) {
            removeGraphFromLookup(globalEventGraphs, graphId);
            removeGraphSubscriptionsFromLookup(globalEventSubscriptions, graphId);
            return;
        }
        for (String eventType : cleanupIndex.getNodeTypes()) {
            removeGraph(globalEventGraphs, eventType, graphId);
            removeGraphSubscriptions(globalEventSubscriptions, eventType, graphId);
        }
    }

    public void registerEntityGraph(Entity entity, String graphId, RuntimeGraphIndex index) {
        Map<String, RuntimeGraphIndex> entityIndexes = registeredEntityGraphs.computeIfAbsent(entity, ignored -> new HashMap<>());
        RuntimeGraphIndex previous = entityIndexes.get(graphId);
        if (previous == index) return;
        if (previous != null) {
            removeEntityGraphFromLookup(entity, graphId, previous);
            removeEntityGraphSubscriptionsFromLookup(entity, graphId, previous);
        }
        entityIndexes.put(graphId, index);
        addEntityGraphSubscriptions(entity, graphId, index);
    }

    public void unregisterEntityGraph(Entity entity, String graphId, @Nullable RuntimeGraphIndex index) {
        Map<String, RuntimeGraphIndex> entityIndexes = registeredEntityGraphs.get(entity);
        RuntimeGraphIndex registeredIndex = entityIndexes != null ? entityIndexes.remove(graphId) : null;
        if (entityIndexes != null && entityIndexes.isEmpty()) {
            registeredEntityGraphs.remove(entity);
        }

        RuntimeGraphIndex cleanupIndex = registeredIndex != null ? registeredIndex : index;
        if (cleanupIndex == null) {
            removeEntityGraphFromLookup(entity, graphId);
            removeEntityGraphSubscriptionsFromLookup(entity, graphId);
            return;
        }
        for (String eventType : cleanupIndex.getNodeTypes()) {
            removeEntityGraph(eventType, entity, graphId);
            removeEntityGraphSubscription(eventType, entity, graphId);
        }
    }

    public Set<String> globalGraphsFor(String eventType) {
        Set<String> graphIds = globalEventGraphs.get(eventType);
        return graphIds != null ? Set.copyOf(graphIds) : Collections.emptySet();
    }

    public Set<String> entityGraphsFor(Entity entity, String eventType) {
        Map<Entity, Set<String>> entities = entityEventGraphs.get(eventType);
        if (entities == null) return Collections.emptySet();
        Set<String> graphIds = entities.get(entity);
        return graphIds != null ? Set.copyOf(graphIds) : Collections.emptySet();
    }

    public List<EventSubscription> globalSubscriptionsFor(String eventType) {
        List<EventSubscription> subscriptions = globalEventSubscriptions.get(eventType);
        return subscriptions != null ? List.copyOf(subscriptions) : Collections.emptyList();
    }

    public List<EventSubscription> entitySubscriptionsFor(Entity entity, String eventType) {
        Map<Entity, List<EventSubscription>> entities = entityEventSubscriptions.get(eventType);
        if (entities == null) return Collections.emptyList();
        List<EventSubscription> subscriptions = entities.get(entity);
        return subscriptions != null ? List.copyOf(subscriptions) : Collections.emptyList();
    }

    private void addEntityGraphSubscriptions(Entity entity, String graphId, RuntimeGraphIndex index) {
        for (String eventType : index.getNodeTypes()) {
            List<EventSubscription> subscriptions = buildSubscriptions(graphId, index, eventType);
            if (subscriptions.isEmpty()) continue;

            entityEventGraphs
                    .computeIfAbsent(eventType, ignored -> new WeakHashMap<>())
                    .computeIfAbsent(entity, ignored -> new HashSet<>())
                    .add(graphId);
            entityEventSubscriptions
                    .computeIfAbsent(eventType, ignored -> new WeakHashMap<>())
                    .computeIfAbsent(entity, ignored -> new ArrayList<>())
                    .addAll(subscriptions);
        }
    }

    private static void addGraphSubscriptions(Map<String, Set<String>> graphLookup,
                                              Map<String, List<EventSubscription>> subscriptionLookup,
                                              String graphId,
                                              RuntimeGraphIndex index) {
        for (String eventType : index.getNodeTypes()) {
            List<EventSubscription> subscriptions = buildSubscriptions(graphId, index, eventType);
            if (subscriptions.isEmpty()) continue;

            graphLookup.computeIfAbsent(eventType, ignored -> new HashSet<>()).add(graphId);
            subscriptionLookup.computeIfAbsent(eventType, ignored -> new ArrayList<>()).addAll(subscriptions);
        }
    }

    private static List<EventSubscription> buildSubscriptions(String graphId, RuntimeGraphIndex index, String eventType) {
        List<Integer> nodeIds = index.findNodesByType(eventType);
        if (nodeIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<EventSubscription> subscriptions = new ArrayList<>(nodeIds.size());
        for (int nodeId : nodeIds) {
            subscriptions.add(new EventSubscription(
                    graphId, index, nodeId, eventType,
                    EventPrecheckRegistry.build(graphId, index, nodeId, eventType)));
        }
        return subscriptions;
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

    private static void removeGraphSubscriptionsFromLookup(Map<String, List<EventSubscription>> lookup, String graphId) {
        lookup.entrySet().removeIf(entry -> {
            entry.getValue().removeIf(subscription -> subscription.graphId().equals(graphId));
            return entry.getValue().isEmpty();
        });
    }

    private static void removeGraphSubscriptionsFromLookup(Map<String, List<EventSubscription>> lookup, String graphId, RuntimeGraphIndex index) {
        for (String eventType : index.getNodeTypes()) {
            removeGraphSubscriptions(lookup, eventType, graphId);
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

    private void removeEntityGraphSubscriptionsFromLookup(Entity entity, String graphId) {
        entityEventSubscriptions.entrySet().removeIf(entry -> {
            Map<Entity, List<EventSubscription>> entities = entry.getValue();
            List<EventSubscription> subscriptions = entities.get(entity);
            if (subscriptions != null) {
                subscriptions.removeIf(subscription -> subscription.graphId().equals(graphId));
                if (subscriptions.isEmpty()) {
                    entities.remove(entity);
                }
            }
            return entities.isEmpty();
        });
    }

    private void removeEntityGraphSubscriptionsFromLookup(Entity entity, String graphId, RuntimeGraphIndex index) {
        for (String eventType : index.getNodeTypes()) {
            removeEntityGraphSubscription(eventType, entity, graphId);
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

    private static void removeGraphSubscriptions(Map<String, List<EventSubscription>> lookup, String eventType, String graphId) {
        List<EventSubscription> subscriptions = lookup.get(eventType);
        if (subscriptions == null) return;
        subscriptions.removeIf(subscription -> subscription.graphId().equals(graphId));
        if (subscriptions.isEmpty()) {
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

    private void removeEntityGraphSubscription(String eventType, Entity entity, String graphId) {
        Map<Entity, List<EventSubscription>> entities = entityEventSubscriptions.get(eventType);
        if (entities == null) return;
        List<EventSubscription> subscriptions = entities.get(entity);
        if (subscriptions != null) {
            subscriptions.removeIf(subscription -> subscription.graphId().equals(graphId));
            if (subscriptions.isEmpty()) {
                entities.remove(entity);
            }
        }
        if (entities.isEmpty()) {
            entityEventSubscriptions.remove(eventType);
        }
    }
}
