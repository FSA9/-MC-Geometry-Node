package com.mine.geometry_node.client.ui.components.scroll;

import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;

/** Local mouse hit policy; never changes ModernUI's global configuration or private caches. */
final class UiScrollBehavior {
    private static final float BAR_SIZE_DP = 6;
    private final boolean horizontal;
    private boolean scrollbarGesture;
    private boolean suppressed;

    UiScrollBehavior(boolean horizontal) {
        this.horizontal = horizontal;
    }

    void configure(View host) {
        host.setScrollBarSize(Math.max(1, UIUtils.dp2pxInt(BAR_SIZE_DP)));
        // Outside means the track sits at the view edge, independently of user padding.
        // Inset reserves its width so no child button is painted underneath it.
        host.setScrollBarStyle(View.SCROLLBARS_OUTSIDE_INSET);
    }

    void syncSize(View host) {
        int size = Math.max(1, UIUtils.dp2pxInt(BAR_SIZE_DP));
        if (host.getScrollBarSize() != size) {
            host.setScrollBarSize(size);
            host.resolvePadding();
        }
    }

    void beginGesture(View host, MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            scrollbarGesture = event.isButtonPressed(MotionEvent.BUTTON_PRIMARY) && contains(host, event);
        }
    }

    void endGesture(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) scrollbarGesture = false;
    }

    boolean filter(View host, MotionEvent event) {
        boolean previous = suppressed;
        // A drag that started on the scrollbar must continue after the pointer leaves its track.
        suppressed = !scrollbarGesture && !contains(host, event);
        return previous;
    }

    void restore(boolean previous) {
        suppressed = previous;
    }

    boolean suppressed() {
        return suppressed;
    }

    void reset() {
        scrollbarGesture = false;
        suppressed = false;
    }

    private boolean contains(View host, MotionEvent event) {
        float x = event.getX(), y = event.getY();
        if (x < 0 || y < 0 || x >= host.getWidth() || y >= host.getHeight()) return false;
        int size = host.getScrollBarSize();
        if (horizontal) return y >= host.getHeight() - size;
        return host.isLayoutRtl() ? x < size : x >= host.getWidth() - size;
    }
}
