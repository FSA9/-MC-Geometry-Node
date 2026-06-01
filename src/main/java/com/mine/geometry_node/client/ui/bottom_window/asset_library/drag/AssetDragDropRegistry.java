package com.mine.geometry_node.client.ui.bottom_window.asset_library.drag;

public final class AssetDragDropRegistry {
    public interface DropTarget {
        boolean acceptDrop(AssetDragState.Payload payload, float rawX, float rawY);
    }

    private static DropTarget sDropTarget;

    private AssetDragDropRegistry() {
    }

    public static void setDropTarget(DropTarget target) {
        sDropTarget = target;
    }

    public static void clearDropTarget() {
        sDropTarget = null;
    }

    public static boolean dispatchDrop(float rawX, float rawY) {
        AssetDragState.Payload payload = AssetDragState.current();
        return payload != null && sDropTarget != null && sDropTarget.acceptDrop(payload, rawX, rawY);
    }
}
