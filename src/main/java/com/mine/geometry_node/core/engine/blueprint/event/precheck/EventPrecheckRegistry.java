package com.mine.geometry_node.core.engine.blueprint.event.precheck;

import com.mine.geometry_node.core.engine.blueprint.runtime.RuntimeGraphIndex;

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

        EventPrecheckFactory previous = FACTORIES.putIfAbsent(eventType, factory);
        if (previous != null && previous != factory) {
            throw new IllegalStateException("Event precheck factory already registered for " + eventType);
        }
    }

    public static EventPrecheck build(String graphId, RuntimeGraphIndex index, int nodeId, String eventType) {
        EventPrecheckContext context = new EventPrecheckContext(graphId, index, nodeId, eventType);
        EventPrecheck specPrecheck = EventPrecheckCompiler.compile(context);

        EventPrecheckFactory factory = FACTORIES.get(eventType);
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
