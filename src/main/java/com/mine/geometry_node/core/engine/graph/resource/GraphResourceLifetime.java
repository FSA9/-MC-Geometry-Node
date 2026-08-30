package com.mine.geometry_node.core.engine.graph.resource;

/** Determines which graph lifecycle boundary releases a runtime resource. */
public enum GraphResourceLifetime {
    BINDING,
    PROCESS
}
