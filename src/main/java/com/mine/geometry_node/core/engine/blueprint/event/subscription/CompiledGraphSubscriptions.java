package com.mine.geometry_node.core.engine.blueprint.event.subscription;

import com.mine.geometry_node.core.engine.blueprint.event.precheck.EventPrecheckRegistry;
import com.mine.geometry_node.core.engine.blueprint.plan.BlueprintPlan;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Immutable event-subscription template shared by every binding of one compiled graph. */
final class CompiledGraphSubscriptions {
    private final BlueprintPlan plan;
    private final Map<String, List<EventSubscription>> subscriptionsByEventType;
    private final Set<String> receiveFrequencies;

    private CompiledGraphSubscriptions(BlueprintPlan plan,
                                       Map<String, List<EventSubscription>> subscriptionsByEventType,
                                       Set<String> receiveFrequencies) {
        this.plan = plan;
        this.subscriptionsByEventType = Map.copyOf(subscriptionsByEventType);
        this.receiveFrequencies = Set.copyOf(receiveFrequencies);
    }

    static CompiledGraphSubscriptions compile(String graphId, BlueprintPlan plan) {
        Map<String, List<EventSubscription>> subscriptions = new LinkedHashMap<>();
        for (String eventType : plan.getEventTypes()) {
            List<Integer> nodeIds = plan.findNodesByType(eventType);
            if (nodeIds.isEmpty()) continue;
            List<EventSubscription> compiled = new ArrayList<>(nodeIds.size());
            for (int nodeId : nodeIds) {
                compiled.add(new EventSubscription(graphId, plan, nodeId, eventType,
                        EventPrecheckRegistry.build(graphId, plan, nodeId, eventType)));
            }
            subscriptions.put(eventType, List.copyOf(compiled));
        }
        return new CompiledGraphSubscriptions(plan, subscriptions, plan.getReceiveBlueprintFrequencies());
    }

    BlueprintPlan plan() {
        return plan;
    }

    Map<String, List<EventSubscription>> subscriptionsByEventType() {
        return subscriptionsByEventType;
    }

    Set<String> receiveFrequencies() {
        return receiveFrequencies;
    }
}
