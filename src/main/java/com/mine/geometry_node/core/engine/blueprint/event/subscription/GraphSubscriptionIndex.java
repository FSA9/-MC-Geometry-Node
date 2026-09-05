package com.mine.geometry_node.core.engine.blueprint.event.subscription;

import com.mine.geometry_node.core.engine.blueprint.event.precheck.EventPrecheckRegistry;
import com.mine.geometry_node.core.engine.blueprint.plan.BlueprintPlan;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Event subscription index for bound blueprint graphs. A graph's identity and
 * subscriptions share one index entry so registration cannot become partially stale.
 */
public final class GraphSubscriptionIndex {
    private final Map<String, Map<String, List<EventSubscription>>> globalSubscriptions = new HashMap<>();
    private final Map<String, Map<Entity, Map<String, List<EventSubscription>>>> entitySubscriptions = new HashMap<>();
    private final Map<String, BlueprintPlan> registeredGlobalGraphs = new HashMap<>();
    private final Map<Entity, Map<String, BlueprintPlan>> registeredEntityGraphs = new WeakHashMap<>();

    public boolean isGlobalGraphRegistered(String graphId) {
        return registeredGlobalGraphs.containsKey(graphId);
    }

    public Set<Entity> registeredEntitiesForGraph(String graphId) {
        Set<Entity> result = new HashSet<>();
        registeredEntityGraphs.forEach((entity, graphs) -> {
            if (graphs.containsKey(graphId)) result.add(entity);
        });
        return Set.copyOf(result);
    }

    public Map<String, Set<Entity>> registeredEntitiesForGraphs(Set<String> graphIds) {
        Map<String, Set<Entity>> result = new LinkedHashMap<>();
        for (String graphId : graphIds) {
            result.put(graphId, new HashSet<>());
        }
        registeredEntityGraphs.forEach((entity, graphs) -> {
            for (String graphId : graphs.keySet()) {
                Set<Entity> entities = result.get(graphId);
                if (entities != null) entities.add(entity);
            }
        });
        result.replaceAll((graphId, entities) -> Set.copyOf(entities));
        return Map.copyOf(result);
    }

    public void registerGlobalGraph(String graphId, BlueprintPlan plan) {
        BlueprintPlan previous = registeredGlobalGraphs.get(graphId);
        if (previous == plan) return;
        if (previous != null) removeGlobalSubscriptions(graphId, previous);
        registeredGlobalGraphs.put(graphId, plan);
        addGlobalSubscriptions(graphId, plan);
    }

    public void unregisterGlobalGraph(String graphId, @Nullable BlueprintPlan plan) {
        BlueprintPlan registered = registeredGlobalGraphs.remove(graphId);
        BlueprintPlan cleanupPlan = registered != null ? registered : plan;
        if (cleanupPlan != null) {
            removeGlobalSubscriptions(graphId, cleanupPlan);
        } else {
            globalSubscriptions.values().removeIf(graphs -> {
                graphs.remove(graphId);
                return graphs.isEmpty();
            });
        }
    }

    public void registerEntityGraph(Entity entity, String graphId, BlueprintPlan plan) {
        Map<String, BlueprintPlan> graphs = registeredEntityGraphs.computeIfAbsent(entity, ignored -> new HashMap<>());
        BlueprintPlan previous = graphs.get(graphId);
        if (previous == plan) return;
        if (previous != null) removeEntitySubscriptions(entity, graphId, previous);
        graphs.put(graphId, plan);
        addEntitySubscriptions(entity, graphId, plan);
    }

    public void unregisterEntityGraph(Entity entity, String graphId, @Nullable BlueprintPlan plan) {
        Map<String, BlueprintPlan> graphs = registeredEntityGraphs.get(entity);
        BlueprintPlan registered = graphs != null ? graphs.remove(graphId) : null;
        if (graphs != null && graphs.isEmpty()) registeredEntityGraphs.remove(entity);

        BlueprintPlan cleanupPlan = registered != null ? registered : plan;
        if (cleanupPlan != null) {
            removeEntitySubscriptions(entity, graphId, cleanupPlan);
        } else {
            entitySubscriptions.values().removeIf(entities -> {
                removeEntityGraph(entities, entity, graphId);
                return entities.isEmpty();
            });
        }
    }

    public Set<String> globalGraphsFor(String eventType) {
        Map<String, List<EventSubscription>> graphs = globalSubscriptions.get(canonicalEventType(eventType));
        return graphs != null ? Set.copyOf(graphs.keySet()) : Collections.emptySet();
    }

    public boolean hasGlobalSubscriptions(String eventType) {
        Map<String, List<EventSubscription>> graphs = globalSubscriptions.get(canonicalEventType(eventType));
        return graphs != null && !graphs.isEmpty();
    }

    public Set<String> entityGraphsFor(Entity entity, String eventType) {
        Map<Entity, Map<String, List<EventSubscription>>> entities = entitySubscriptions.get(canonicalEventType(eventType));
        if (entities == null) return Collections.emptySet();
        Map<String, List<EventSubscription>> graphs = entities.get(entity);
        return graphs != null ? Set.copyOf(graphs.keySet()) : Collections.emptySet();
    }

    public boolean hasEntitySubscriptions(Entity entity, String eventType) {
        Map<Entity, Map<String, List<EventSubscription>>> entities =
                entitySubscriptions.get(canonicalEventType(eventType));
        Map<String, List<EventSubscription>> graphs = entities != null ? entities.get(entity) : null;
        return graphs != null && !graphs.isEmpty();
    }

    public List<EventSubscription> globalSubscriptionsFor(String eventType) {
        Map<String, List<EventSubscription>> graphs = globalSubscriptions.get(canonicalEventType(eventType));
        return flatten(graphs);
    }

    public List<EventSubscription> entitySubscriptionsFor(Entity entity, String eventType) {
        Map<Entity, Map<String, List<EventSubscription>>> entities = entitySubscriptions.get(canonicalEventType(eventType));
        return entities != null ? flatten(entities.get(entity)) : Collections.emptyList();
    }

    private void addGlobalSubscriptions(String graphId, BlueprintPlan plan) {
        for (String eventType : eventTypes(plan)) {
            List<EventSubscription> subscriptions = buildSubscriptions(graphId, plan, eventType);
            if (!subscriptions.isEmpty()) {
                globalSubscriptions.computeIfAbsent(eventType, ignored -> new LinkedHashMap<>())
                        .put(graphId, subscriptions);
            }
        }
    }

    private void addEntitySubscriptions(Entity entity, String graphId, BlueprintPlan plan) {
        for (String eventType : eventTypes(plan)) {
            List<EventSubscription> subscriptions = buildSubscriptions(graphId, plan, eventType);
            if (!subscriptions.isEmpty()) {
                entitySubscriptions.computeIfAbsent(eventType, ignored -> new WeakHashMap<>())
                        .computeIfAbsent(entity, ignored -> new LinkedHashMap<>())
                        .put(graphId, subscriptions);
            }
        }
    }

    private void removeGlobalSubscriptions(String graphId, BlueprintPlan plan) {
        for (String eventType : eventTypes(plan)) {
            Map<String, List<EventSubscription>> graphs = globalSubscriptions.get(eventType);
            if (graphs == null) continue;
            graphs.remove(graphId);
            if (graphs.isEmpty()) globalSubscriptions.remove(eventType);
        }
    }

    private void removeEntitySubscriptions(Entity entity, String graphId, BlueprintPlan plan) {
        for (String eventType : eventTypes(plan)) {
            Map<Entity, Map<String, List<EventSubscription>>> entities = entitySubscriptions.get(eventType);
            if (entities == null) continue;
            removeEntityGraph(entities, entity, graphId);
            if (entities.isEmpty()) entitySubscriptions.remove(eventType);
        }
    }

    private static void removeEntityGraph(Map<Entity, Map<String, List<EventSubscription>>> entities,
                                          Entity entity, String graphId) {
        Map<String, List<EventSubscription>> graphs = entities.get(entity);
        if (graphs == null) return;
        graphs.remove(graphId);
        if (graphs.isEmpty()) entities.remove(entity);
    }

    private static List<EventSubscription> buildSubscriptions(String graphId, BlueprintPlan plan, String eventType) {
        List<Integer> nodeIds = plan.findNodesByType(eventType);
        if (nodeIds.isEmpty()) return Collections.emptyList();

        List<EventSubscription> subscriptions = new ArrayList<>(nodeIds.size());
        for (int nodeId : nodeIds) {
            subscriptions.add(new EventSubscription(
                    graphId, plan, nodeId, eventType,
                    EventPrecheckRegistry.build(graphId, plan, nodeId, eventType)));
        }
        return List.copyOf(subscriptions);
    }

    private static List<EventSubscription> flatten(@Nullable Map<String, List<EventSubscription>> graphs) {
        if (graphs == null || graphs.isEmpty()) return Collections.emptyList();
        int size = graphs.values().stream().mapToInt(List::size).sum();
        List<EventSubscription> result = new ArrayList<>(size);
        graphs.values().forEach(result::addAll);
        return List.copyOf(result);
    }

    private static Set<String> eventTypes(BlueprintPlan plan) {
        return plan.getEventTypes();
    }

    private static String canonicalEventType(String eventType) {
        return eventType == null || eventType.isBlank() ? "" : NodeDef.canonicalTypeId(eventType);
    }
}
