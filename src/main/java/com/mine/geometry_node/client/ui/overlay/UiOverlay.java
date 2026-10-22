package com.mine.geometry_node.client.ui.overlay;

import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.FrameLayout;

/** Common lifecycle contract shared by transient and modal overlays. */
public interface UiOverlay {
    View createView(Context context);

    default FrameLayout.LayoutParams createLayoutParams(ViewGroup host) {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.gravity = Gravity.CENTER;
        return params;
    }

    default boolean canClose(OverlayCloseReason reason) {
        return true;
    }

    /**
     * Gives an overlay a chance to leave an internal editing state before the
     * layer manager closes it. Return true only when Escape was consumed.
     */
    default boolean onEscapePressed() {
        return false;
    }

    default boolean onPointerHover(float screenX, float screenY) {
        return false;
    }

    default void onShown(OverlayHandle handle) {
    }

    /** Presentation paused; retain the logical session and unfinished input. */
    default void onSuspended() {
    }

    /** Presentation restored on the same instance; do not reload its editing state. */
    default void onResumed() {
    }

    /** Cleanup callback. REPLACED and HOST_DESTROYED must not present another overlay. */
    default void onClosed(OverlayCloseReason reason) {
    }

    default void onDestroyed() {
    }
}
