package com.mine.geometry_node.core.engine.blueprint.event.precheck;

import com.mine.geometry_node.core.engine.blueprint.plan.BlueprintPlan;
import com.mine.geometry_node.core.node.definition.node.NodeDef;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class EventPrecheckRegistry {
    private static final ConcurrentMap<String, EventPrecheckFactory> FACTORIES = new ConcurrentHashMap<>();

    private EventPrecheckRegistry() {
    }

    public static void register(String eventType, EventPrecheckFactory factory) {
        Objects.requireNonNull(factory, "factory");
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("eventType must not be blank");
        }

        String canonicalType = NodeDef.canonicalTypeId(eventType);
        EventPrecheckFactory previous = FACTORIES.putIfAbsent(canonicalType, factory);
        if (previous != null && previous != factory) {
            throw new IllegalStateException("Event precheck factory already registered for " + canonicalType);
        }
    }

    public static EventPrecheck build(String graphId, BlueprintPlan index, int nodeId, String eventType) {
        String canonicalType = NodeDef.canonicalTypeId(eventType);
        EventPrecheckContext context = new EventPrecheckContext(graphId, index, nodeId, canonicalType);
        EventPrecheck specPrecheck = EventPrecheckCompiler.compile(context);

        EventPrecheckFactory factory = FACTORIES.get(canonicalType);
        if (factory == null) {
            return specPrecheck;
        }

        EventPrecheck customPrecheck = factory.create(context);
        return combine(specPrecheck, customPrecheck != null ? customPrecheck : EventPrecheck.ALWAYS);
    }

    private static EventPrecheck combine(EventPrecheck left, EventPrecheck right) {
        if (left == EventPrecheck.ALWAYS) {
            return right;
        }
        if (right == EventPrecheck.ALWAYS) {
            return left;
        }
        return (level, target, eventData) -> left.test(level, target, eventData) && right.test(level, target, eventData);
    }
}
