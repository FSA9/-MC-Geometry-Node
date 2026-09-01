package com.mine.geometry_node.client.ui.workspace.drag;

import java.util.Objects;

/** Owns the single active workspace drag session. UI components only publish and clear it. */
public final class WorkspaceDragState {
    private static Session current;

    private WorkspaceDragState() {
    }

    public static synchronized void start(WorkspaceDragPayload payload, WorkspaceDragOperation operation,
                                          Object source) {
        if (payload == null) return;
        current = new Session(payload, operation != null ? operation : WorkspaceDragOperation.MOVE, source);
    }

    public static synchronized Session current() {
        return current;
    }

    public static synchronized void clear() {
        current = null;
    }

    public static synchronized boolean isSource(Object source) {
        return current != null && current.source() == source;
    }

    public record Session(WorkspaceDragPayload payload, WorkspaceDragOperation operation, Object source) {
        public Session {
            Objects.requireNonNull(payload, "payload");
            Objects.requireNonNull(operation, "operation");
        }
    }
}
