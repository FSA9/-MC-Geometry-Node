package com.mine.geometry_node.core.engine.blueprint.event.precheck;

import com.mine.geometry_node.core.engine.blueprint.plan.BlueprintPlan;
import com.mine.geometry_node.core.node.NodeRegistry;
import com.mine.geometry_node.core.node.definition.node.NodeDef;

public final class EventPrecheckRegistry {
    private EventPrecheckRegistry() {
    }

    public static EventPrecheck build(String graphId, BlueprintPlan index, int nodeId, String eventType) {
        String canonicalType = NodeDef.canonicalTypeId(eventType);
        EventPrecheckContext context = new EventPrecheckContext(graphId, index, nodeId, canonicalType);
        EventPrecheck specPrecheck = EventPrecheckCompiler.compile(context);

        EventPrecheckFactory factory = NodeRegistry.INSTANCE.getEventPrecheckFactory(canonicalType);
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
