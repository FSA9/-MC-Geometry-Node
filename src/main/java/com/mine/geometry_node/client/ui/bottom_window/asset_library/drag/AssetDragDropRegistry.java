package com.mine.geometry_node.client.ui.bottom_window.asset_library.drag;

public final class AssetDragDropRegistry {
    public interface DropTarget {
        boolean acceptDrop(AssetDragState.Payload payload, float rawX, float rawY);
    }

    private static DropTarget sDropTarget;
    private static int sModalBlockCount = 0;

    private AssetDragDropRegistry() {
    }

    public static void setDropTarget(DropTarget target) {
        sDropTarget = target;
    }

    public static void clearDropTarget() {
        sDropTarget = null;
    }

    public static void pushModalBlocker() {
        sModalBlockCount++;
    }

    public static void popModalBlocker() {
        sModalBlockCount = Math.max(0, sModalBlockCount - 1);
    }

    public static boolean isBlockedByModal() {
        return sModalBlockCount > 0;
    }

    public static boolean dispatchDrop(float rawX, float rawY) {
        if (isBlockedByModal()) return false;
        AssetDragState.Payload payload = AssetDragState.current();
        return payload != null && sDropTarget != null && sDropTarget.acceptDrop(payload, rawX, rawY);
    }
}
