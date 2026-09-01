package com.mine.geometry_node.client.ui.workspace.drag;

/** Facade for workspace-wide drag lifecycle and routing. */
public final class WorkspaceDragService {
    public static final WorkspaceDragService INSTANCE = new WorkspaceDragService();
    private WorkspaceDragService() {}

    public boolean begin(WorkspaceDragPayload payload, WorkspaceDragOperation operation, Object source) {
        if (WorkspaceDragDropRegistry.isBlockedByModal() || payload == null) return false;
        WorkspaceDragState.start(payload, operation, source);
        return true;
    }

    public boolean drop(float rawX, float rawY) {
        boolean accepted = WorkspaceDragDropRegistry.dispatchDrop(rawX, rawY);
        if (accepted) WorkspaceDragState.clear();
        return accepted;
    }

    public void cancel() { WorkspaceDragState.clear(); }
    public void cancelIfSource(Object source) { WorkspaceDragDropRegistry.cancelIfSource(source); }
    public WorkspaceDragState.Session current() { return WorkspaceDragState.current(); }
}
