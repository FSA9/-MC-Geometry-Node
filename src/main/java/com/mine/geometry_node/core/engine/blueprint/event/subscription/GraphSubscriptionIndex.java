package com.mine.geometry_node.core.engine.blueprint.event.subscription;

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
    private final Map<String, CompiledGraphSubscriptions> templatesByGraphId = new HashMap<>();
    private final Map<String, CompiledGraphSubscriptions> registeredGlobalGraphs = new HashMap<>();
    private final Map<Entity, Map<String, CompiledGraphSubscriptions>> registeredEntityGraphs = new WeakHashMap<>();
    private final Map<String, Map<Entity, Set<String>>> receiveSubscribers = new HashMap<>();

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
        CompiledGraphSubscriptions template = template(graphId, plan);
        CompiledGraphSubscriptions previous = registeredGlobalGraphs.get(graphId);
        if (previous == template) return;
        if (previous != null) removeGlobalSubscriptions(graphId, previous);
        registeredGlobalGraphs.put(graphId, template);
        addGlobalSubscriptions(graphId, template);
    }

    public void unregisterGlobalGraph(String graphId, @Nullable BlueprintPlan plan) {
        CompiledGraphSubscriptions registered = registeredGlobalGraphs.remove(graphId);
        if (registered != null) {
            removeGlobalSubscriptions(graphId, registered);
        } else if (plan != null) {
            removeGlobalSubscriptions(graphId, cleanupTemplate(graphId, plan));
        } else {
            globalSubscriptions.values().removeIf(graphs -> {
                graphs.remove(graphId);
                return graphs.isEmpty();
            });
        }
    }

    public void registerEntityGraph(Entity entity, String graphId, BlueprintPlan plan) {
        CompiledGraphSubscriptions template = template(graphId, plan);
        Map<String, CompiledGraphSubscriptions> graphs =
                registeredEntityGraphs.computeIfAbsent(entity, ignored -> new HashMap<>());
        CompiledGraphSubscriptions previous = graphs.get(graphId);
        if (previous == template) return;
        if (previous != null) removeEntitySubscriptions(entity, graphId, previous);
        graphs.put(graphId, template);
        addEntitySubscriptions(entity, graphId, template);
    }

    public void unregisterEntityGraph(Entity entity, String graphId, @Nullable BlueprintPlan plan) {
        Map<String, CompiledGraphSubscriptions> graphs = registeredEntityGraphs.get(entity);
        CompiledGraphSubscriptions registered = graphs != null ? graphs.remove(graphId) : null;
        if (graphs != null && graphs.isEmpty()) registeredEntityGraphs.remove(entity);

        if (registered != null) {
            removeEntitySubscriptions(entity, graphId, registered);
        } else if (plan != null) {
            removeEntitySubscriptions(entity, graphId, cleanupTemplate(graphId, plan));
        } else {
            entitySubscriptions.values().removeIf(entities -> {
                removeEntityGraph(entities, entity, graphId);
                return entities.isEmpty();
            });
            receiveSubscribers.values().removeIf(entities -> {
                Set<String> graphIds = entities.get(entity);
                if (graphIds != null) {
                    graphIds.remove(graphId);
                    if (graphIds.isEmpty()) entities.remove(entity);
                }
                return entities.isEmpty();
            });
        }
    }

    public void unregisterEntity(Entity entity) {
        Map<String, CompiledGraphSubscriptions> graphs = registeredEntityGraphs.remove(entity);
        if (graphs == null) return;
        graphs.forEach((graphId, template) -> removeEntitySubscriptions(entity, graphId, template));
    }

    public void discardTemplate(String graphId) {
        templatesByGraphId.remove(graphId);
    }

    public Map<Entity, Set<String>> receiveSubscribersFor(String frequency) {
        Map<Entity, Set<String>> entities = receiveSubscribers.get(frequency);
        if (entities == null || entities.isEmpty()) return Collections.emptyMap();
        Map<Entity, Set<String>> snapshot = new LinkedHashMap<>();
        entities.forEach((entity, graphIds) -> snapshot.put(entity, Set.copyOf(graphIds)));
        return Map.copyOf(snapshot);
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

    private void addGlobalSubscriptions(String graphId, CompiledGraphSubscriptions template) {
        template.subscriptionsByEventType().forEach((eventType, subscriptions) ->
                globalSubscriptions.computeIfAbsent(eventType, ignored -> new LinkedHashMap<>())
                        .put(graphId, subscriptions));
    }

    private void addEntitySubscriptions(Entity entity, String graphId, CompiledGraphSubscriptions template) {
        template.subscriptionsByEventType().forEach((eventType, subscriptions) ->
                entitySubscriptions.computeIfAbsent(eventType, ignored -> new WeakHashMap<>())
                        .computeIfAbsent(entity, ignored -> new LinkedHashMap<>())
                        .put(graphId, subscriptions));
        for (String frequency : template.receiveFrequencies()) {
            receiveSubscribers.computeIfAbsent(frequency, ignored -> new WeakHashMap<>())
                    .computeIfAbsent(entity, ignored -> new HashSet<>()).add(graphId);
        }
    }

    private void removeGlobalSubscriptions(String graphId, CompiledGraphSubscriptions template) {
        for (String eventType : template.subscriptionsByEventType().keySet()) {
            Map<String, List<EventSubscription>> graphs = globalSubscriptions.get(eventType);
            if (graphs == null) continue;
            graphs.remove(graphId);
            if (graphs.isEmpty()) globalSubscriptions.remove(eventType);
        }
    }

    private void removeEntitySubscriptions(Entity entity, String graphId,
                                           CompiledGraphSubscriptions template) {
        for (String eventType : template.subscriptionsByEventType().keySet()) {
            Map<Entity, Map<String, List<EventSubscription>>> entities = entitySubscriptions.get(eventType);
            if (entities == null) continue;
            removeEntityGraph(entities, entity, graphId);
            if (entities.isEmpty()) entitySubscriptions.remove(eventType);
        }
        for (String frequency : template.receiveFrequencies()) {
            Map<Entity, Set<String>> entities = receiveSubscribers.get(frequency);
            if (entities == null) continue;
            Set<String> graphIds = entities.get(entity);
            if (graphIds != null) {
                graphIds.remove(graphId);
                if (graphIds.isEmpty()) entities.remove(entity);
            }
            if (entities.isEmpty()) receiveSubscribers.remove(frequency);
        }
    }

    private static void removeEntityGraph(Map<Entity, Map<String, List<EventSubscription>>> entities,
                                          Entity entity, String graphId) {
        Map<String, List<EventSubscription>> graphs = entities.get(entity);
        if (graphs == null) return;
        graphs.remove(graphId);
        if (graphs.isEmpty()) entities.remove(entity);
    }

    private CompiledGraphSubscriptions template(String graphId, BlueprintPlan plan) {
        CompiledGraphSubscriptions existing = templatesByGraphId.get(graphId);
        if (existing != null && existing.plan() == plan) return existing;
        CompiledGraphSubscriptions compiled = CompiledGraphSubscriptions.compile(graphId, plan);
        templatesByGraphId.put(graphId, compiled);
        return compiled;
    }

    private CompiledGraphSubscriptions cleanupTemplate(String graphId, BlueprintPlan plan) {
        CompiledGraphSubscriptions existing = templatesByGraphId.get(graphId);
        return existing != null && existing.plan() == plan
                ? existing : CompiledGraphSubscriptions.compile(graphId, plan);
    }

    private static List<EventSubscription> flatten(@Nullable Map<String, List<EventSubscription>> graphs) {
        if (graphs == null || graphs.isEmpty()) return Collections.emptyList();
        int size = graphs.values().stream().mapToInt(List::size).sum();
        List<EventSubscription> result = new ArrayList<>(size);
        graphs.values().forEach(result::addAll);
        return List.copyOf(result);
    }

    private static String canonicalEventType(String eventType) {
        return eventType == null || eventType.isBlank() ? "" : NodeDef.canonicalTypeId(eventType);
    }
}
