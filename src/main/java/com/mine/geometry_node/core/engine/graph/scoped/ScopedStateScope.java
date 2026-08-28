package com.mine.geometry_node.core.engine.graph.scoped;

/** Common state scopes. INSTANCE is reserved for a behavior runtime frame. */
public enum ScopedStateScope {
    INSTANCE(false),
    OWNER(true),
    SHARED(true),
    GROUP(true),
    WORLD(true);

    private final boolean persistent;

    ScopedStateScope(boolean persistent) {
        this.persistent = persistent;
    }

    public boolean isPersistent() {
        return persistent;
    }
}
