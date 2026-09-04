package com.mine.geometry_node.core.engine.blueprint.event.precheck;

/** Declares an optional compile-time precheck owned by an event node. */
public interface EventPrecheckProvider {
    EventPrecheckFactory eventPrecheckFactory();
}
