package com.mine.geometry_node.core.engine.behavior.contract;

/** Final author-visible blackboard scopes. Evaluation cache is intentionally internal. */
public enum BlackboardScope {
    INSTANCE(false),
    OWNER(true),
    SHARED(true),
    GROUP(true),
    WORLD(true);

    private final boolean persistent;

    BlackboardScope(boolean persistent) {
        this.persistent = persistent;
    }

    public boolean isPersistent() {
        return persistent;
    }
}
