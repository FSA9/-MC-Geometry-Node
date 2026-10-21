package com.mine.geometry_node.client.ui.components.base.scroll;

import com.mine.geometry_node.client.ui.theme.UiTheme;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.graphics.drawable.Drawable;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
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

    void configure(View host, UiTheme.Scroll colors) {
        host.setScrollBarSize(Math.max(1, UIUtils.dp2pxInt(BAR_SIZE_DP)));
        // Outside means the track sits at the view edge, independently of user padding.
        // Inset reserves its width so no child button is painted underneath it.
        host.setScrollBarStyle(View.SCROLLBARS_OUTSIDE_INSET);
        applyColors(host, horizontal, colors, -1);
    }

    /** Drawable colors only, also usable by native text scrolling without changing its geometry. */
    static void applyColors(View host, boolean horizontal, UiTheme.Scroll colors) {
        Drawable track = horizontal ? host.getHorizontalScrollbarTrackDrawable() : host.getVerticalScrollbarTrackDrawable();
        Drawable sizing = track != null ? track : horizontal
                ? host.getHorizontalScrollbarThumbDrawable() : host.getVerticalScrollbarThumbDrawable();
        int intrinsicSize = sizing == null ? -1 : horizontal ? sizing.getIntrinsicHeight() : sizing.getIntrinsicWidth();
        applyColors(host, horizontal, colors, intrinsicSize);
    }

    private static void applyColors(View host, boolean horizontal, UiTheme.Scroll colors, int intrinsicSize) {
        java.util.Objects.requireNonNull(colors, "colors");
        // Filled rectangles stay inside the native scrollbar's fading bounds.
        ShapeDrawable thumb = barDrawable(colors.thumb());
        ShapeDrawable track = barDrawable(colors.track());
        // ModernUI gives the track's intrinsic thickness priority over getScrollBarSize().
        // Preserve that choice for native text; shared scroll containers explicitly use BAR_SIZE_DP.
        if (horizontal) {
            track.setSize(-1, intrinsicSize);
            thumb.setSize(-1, intrinsicSize);
        } else {
            track.setSize(intrinsicSize, -1);
            thumb.setSize(intrinsicSize, -1);
        }
        if (horizontal) {
            host.setHorizontalScrollbarThumbDrawable(thumb);
            host.setHorizontalScrollbarTrackDrawable(track);
        } else {
            host.setVerticalScrollbarThumbDrawable(thumb);
            host.setVerticalScrollbarTrackDrawable(track);
        }
    }

    private static ShapeDrawable barDrawable(int color) {
        ShapeDrawable drawable = new ShapeDrawable();
        drawable.setShape(ShapeDrawable.RECTANGLE);
        drawable.setColor(color);
        return drawable;
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
