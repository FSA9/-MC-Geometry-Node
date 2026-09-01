package com.mine.geometry_node.client.ui.workspace.drag;

import java.util.ArrayList;
import java.util.List;

/** Routes the active workspace drag to the most recently registered target. */
public final class WorkspaceDragDropRegistry {
    @FunctionalInterface
    public interface DropTarget {
        boolean acceptDrop(WorkspaceDragState.Session session, float rawX, float rawY);
    }

    private static final List<DropTarget> targets = new ArrayList<>();
    private static int modalBlockCount;

    private WorkspaceDragDropRegistry() {
    }

    public static synchronized Registration register(DropTarget target) {
        if (target == null) return Registration.EMPTY;
        targets.remove(target);
        targets.add(target);
        return new Registration(target);
    }

    public static synchronized void unregister(DropTarget target) {
        targets.remove(target);
    }

    public static synchronized void pushModalBlocker() {
        modalBlockCount++;
    }

    public static synchronized void popModalBlocker() {
        modalBlockCount = Math.max(0, modalBlockCount - 1);
    }

    public static synchronized boolean isBlockedByModal() {
        return modalBlockCount > 0;
    }

    public static synchronized void cancelIfSource(Object source) {
        if (WorkspaceDragState.isSource(source)) WorkspaceDragState.clear();
    }

    public static final class Registration implements AutoCloseable {
        private static final Registration EMPTY = new Registration(null);
        private final DropTarget target;
        private boolean closed;
        private Registration(DropTarget target) { this.target = target; }
        @Override public void close() {
            if (closed || target == null) return;
            closed = true;
            WorkspaceDragDropRegistry.unregister(target);
        }
    }

    public static final class ModalBlocker implements AutoCloseable {
        private boolean closed;
        private ModalBlocker() { pushModalBlocker(); }
        @Override public void close() {
            if (closed) return;
            closed = true;
            popModalBlocker();
        }
    }

    public static ModalBlocker modalBlocker() { return new ModalBlocker(); }

    public static boolean dispatchDrop(float rawX, float rawY) {
        List<DropTarget> snapshot;
        WorkspaceDragState.Session session;
        synchronized (WorkspaceDragDropRegistry.class) {
            if (modalBlockCount > 0 || targets.isEmpty()) return false;
            session = WorkspaceDragState.current();
            if (session == null) return false;
            snapshot = List.copyOf(targets);
        }
        for (int index = snapshot.size() - 1; index >= 0; index--) {
            if (snapshot.get(index).acceptDrop(session, rawX, rawY)) return true;
        }
        return false;
    }
}
