package com.mine.geometry_node.core.engine.blueprint.event.precheck;

/** Creates an immutable, stateless precheck suitable for reuse across graph bindings. */
@FunctionalInterface
public interface EventPrecheckFactory {
    EventPrecheck create(EventPrecheckContext context);
}
