package com.mine.geometry_node.core.node.event;

import com.mine.geometry_node.core.node.meta.MetaKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record EventPrecheckSpec(List<Rule> rules) {
    public static final MetaKey<EventPrecheckSpec> META_KEY = new MetaKey<>("event_precheck_spec");
    public static final EventPrecheckSpec EMPTY = new EventPrecheckSpec(List.of());

    public EventPrecheckSpec {
        rules = rules != null ? List.copyOf(rules) : List.of();
    }

    public static Builder builder() {
        return new Builder();
    }

    public interface Rule {
    }

    public record StaticValueEqualsEventField(String inputPortId, String eventFieldId) implements Rule {
        public StaticValueEqualsEventField {
            requireId(inputPortId, "inputPortId");
            requireId(eventFieldId, "eventFieldId");
        }
    }

    public record TickInterval(String intervalPortId, String offsetPortId) implements Rule {
        public TickInterval {
            requireId(intervalPortId, "intervalPortId");
            requireId(offsetPortId, "offsetPortId");
        }
    }

    public static final class Builder {
        private final List<Rule> rules = new ArrayList<>();

        public Builder staticValueEqualsEventField(String inputPortId, String eventFieldId) {
            rules.add(new StaticValueEqualsEventField(inputPortId, eventFieldId));
            return this;
        }

        public Builder tickInterval(String intervalPortId, String offsetPortId) {
            rules.add(new TickInterval(intervalPortId, offsetPortId));
            return this;
        }

        public EventPrecheckSpec build() {
            return rules.isEmpty() ? EMPTY : new EventPrecheckSpec(rules);
        }
    }

    private static void requireId(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
