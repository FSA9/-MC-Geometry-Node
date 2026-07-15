package com.mine.geometry_node.core.engine.blueprint.event.precheck;

@FunctionalInterface
public interface EventPrecheckFactory {
    EventPrecheck create(EventPrecheckContext context);
}
