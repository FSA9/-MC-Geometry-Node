package com.mine.geometry_node.core.engine.service;

/**
 * Root placeholder for services shared by graph runtimes.
 * Concrete services should live here only when they are not blueprint,
 * dialogue, or behavior-tree specific.
 */
public final class GraphEngineServices {
    public static final GraphEngineServices INSTANCE = new GraphEngineServices();

    private GraphEngineServices() {
    }
}
