package com.mine.geometry_node.core.engine.blueprint.event.precheck;

import com.mine.geometry_node.core.node.NodeRegistry;
import com.mine.geometry_node.core.node.event.EventPrecheckSpec;
import com.mine.geometry_node.core.node.nodes.NodeDef;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class EventPrecheckCompiler {
    private EventPrecheckCompiler() {
    }

    static EventPrecheck compile(EventPrecheckContext context) {
        NodeDef definition = NodeRegistry.INSTANCE.getDefaultDefinition(context.eventType());
        if (definition == null) {
            return EventPrecheck.ALWAYS;
        }

        EventPrecheckSpec spec = definition.getMeta(EventPrecheckSpec.META_KEY).orElse(EventPrecheckSpec.EMPTY);
        if (spec.rules().isEmpty()) {
            return EventPrecheck.ALWAYS;
        }

        List<EventPrecheck> checks = new ArrayList<>();
        for (EventPrecheckSpec.Rule rule : spec.rules()) {
            EventPrecheck check = compileRule(context, rule);
            if (check != EventPrecheck.ALWAYS) {
                checks.add(check);
            }
        }
        return combineAnd(checks);
    }

    private static EventPrecheck compileRule(EventPrecheckContext context, EventPrecheckSpec.Rule rule) {
        if (rule instanceof EventPrecheckSpec.StaticValueEqualsEventField equalsRule) {
            return compileStaticEquals(context, equalsRule);
        }
        if (rule instanceof EventPrecheckSpec.TickInterval tickRule) {
            return compileTickInterval(context, tickRule);
        }
        return EventPrecheck.ALWAYS;
    }

    private static EventPrecheck compileStaticEquals(EventPrecheckContext context, EventPrecheckSpec.StaticValueEqualsEventField rule) {
        requireStaticOnly(context, rule.inputPortId());

        String expected = normalizeString(context.staticInput(rule.inputPortId()));
        if (expected.isEmpty()) {
            return EventPrecheck.ALWAYS;
        }

        return (level, target, eventData) -> expected.equals(readEventString(eventData, rule.eventFieldId()));
    }

    private static EventPrecheck compileTickInterval(EventPrecheckContext context, EventPrecheckSpec.TickInterval rule) {
        requireStaticOnly(context, rule.intervalPortId());
        requireStaticOnly(context, rule.offsetPortId());

        int interval = Math.max(1, context.staticInput(rule.intervalPortId(), Integer.class, 1));
        int offset = Math.floorMod(context.staticInput(rule.offsetPortId(), Integer.class, 0), interval);
        if (interval == 1) {
            return EventPrecheck.ALWAYS;
        }

        return (level, target, eventData) -> level.getGameTime() % interval == offset;
    }

    private static EventPrecheck combineAnd(List<EventPrecheck> checks) {
        if (checks == null || checks.isEmpty()) {
            return EventPrecheck.ALWAYS;
        }
        if (checks.size() == 1) {
            return checks.get(0);
        }
        return (level, target, eventData) -> {
            for (EventPrecheck check : checks) {
                if (!check.test(level, target, eventData)) {
                    return false;
                }
            }
            return true;
        };
    }

    private static void requireStaticOnly(EventPrecheckContext context, String inputPortId) {
        if (context.index().findInputSource(context.nodeId(), inputPortId) != null) {
            throw new IllegalStateException("Event precheck port must be static: " + context.eventType() + "." + inputPortId);
        }
    }

    private static String readEventString(Map<String, Object> eventData, String key) {
        if (eventData == null) {
            return "";
        }
        return normalizeString(eventData.get(key));
    }

    private static String normalizeString(Object raw) {
        if (raw == null) {
            return "";
        }
        return String.valueOf(raw).trim().toLowerCase(Locale.ROOT);
    }
}
