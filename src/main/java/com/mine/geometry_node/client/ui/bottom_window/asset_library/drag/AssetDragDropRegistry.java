package com.mine.geometry_node.client.ui.bottom_window.asset_library.drag;

import java.util.ArrayList;
import java.util.List;

public final class AssetDragDropRegistry {
    public interface DropTarget {
        boolean acceptDrop(AssetDragState.Payload payload, float rawX, float rawY);
    }

    private static final List<DropTarget> sDropTargets = new ArrayList<>();
    private static int sModalBlockCount = 0;

    private AssetDragDropRegistry() {
    }

    public static void setDropTarget(DropTarget target) {
        sDropTargets.clear();
        registerDropTarget(target);
    }

    public static void clearDropTarget() {
        sDropTargets.clear();
    }

    public static void registerDropTarget(DropTarget target) {
        if (target == null) {
            return;
        }
        sDropTargets.remove(target);
        sDropTargets.add(target);
    }

    public static void unregisterDropTarget(DropTarget target) {
        sDropTargets.remove(target);
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
        if (payload == null || sDropTargets.isEmpty()) {
            return false;
        }
        List<DropTarget> targets = List.copyOf(sDropTargets);
        for (int i = targets.size() - 1; i >= 0; i--) {
            if (targets.get(i).acceptDrop(payload, rawX, rawY)) {
                return true;
            }
        }
        return false;
    }
}
