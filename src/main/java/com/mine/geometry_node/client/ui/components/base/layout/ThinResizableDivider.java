package com.mine.geometry_node.client.ui.components.base.layout;

import icyllis.modernui.core.Context;

/** One-dp resting line with the standard eight-dp drag target and blue hover feedback.
 * Orientation denotes the resize axis, just as in {@link ResizableDivider}.
 */
public final class ThinResizableDivider extends ResizableDivider {
    public ThinResizableDivider(Context context, Orientation orientation, DragListener listener) {
        this(context, orientation, listener, false);
    }

    private ThinResizableDivider(Context context, Orientation orientation, DragListener listener, boolean weighted) {
        super(context, orientation, listener, weighted, true);
        setLayoutParams(layoutParams(orientation));
    }

    public static ThinResizableDivider weighted(Context context, Orientation orientation) {
        return weighted(context, orientation, null);
    }

    public static ThinResizableDivider weighted(Context context, Orientation orientation, DragListener listener) {
        return new ThinResizableDivider(context, orientation, listener, true);
    }
}
