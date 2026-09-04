package com.mine.geometry_node.core.engine.graph.scoped;

public final class ScopedStateAccessException extends IllegalStateException {
    public ScopedStateAccessException(String message) {
        super(message);
    }

    public ScopedStateAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
