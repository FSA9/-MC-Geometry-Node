package com.mine.geometry_node.client.ui.components.base.layout;

import icyllis.modernui.core.Context;
import com.mine.geometry_node.client.ui.theme.UiTheme;
import com.mine.geometry_node.client.ui.theme.UiThemes;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.widget.LinearLayout;

/** One-dp line with a transparent eight-dp drag target overlapping the adjacent panes.
 * Orientation denotes the resize axis, just as in {@link ResizableDivider}.
 */
public final class ThinResizableDivider extends ResizableDivider {
    public ThinResizableDivider(Context context, Orientation orientation, DragListener listener) {
        this(context, orientation, listener, UiThemes.EDITOR);
    }

    public ThinResizableDivider(Context context, Orientation orientation, DragListener listener, UiTheme theme) {
        this(context, orientation, listener, false, DEFAULT_MIN_WEIGHT, theme);
    }

    private ThinResizableDivider(Context context, Orientation orientation, DragListener listener,
                                 boolean weighted, float minimumWeight, UiTheme theme) {
        super(context, orientation, listener, weighted, true, minimumWeight, theme);
        LinearLayout.LayoutParams params = layoutParams(orientation);
        // Only the line occupies layout space. The rest of the hit target lies over
        // the panes, so their backgrounds reach the line instead of exposing a gutter.
        int hit = orientation == Orientation.VERTICAL ? params.height : params.width;
        int overlap = Math.max(0, hit - Math.max(1, UIUtils.dp2pxInt(1)));
        if (orientation == Orientation.VERTICAL) {
            params.topMargin = -overlap / 2;
            params.bottomMargin = -(overlap - overlap / 2);
        } else {
            params.leftMargin = -overlap / 2;
            params.rightMargin = -(overlap - overlap / 2);
        }
        setLayoutParams(params);
    }

    public static ThinResizableDivider weighted(Context context, Orientation orientation) {
        return weighted(context, orientation, null);
    }

    public static ThinResizableDivider weighted(Context context, Orientation orientation, DragListener listener) {
        return weighted(context, orientation, DEFAULT_MIN_WEIGHT, listener);
    }

    public static ThinResizableDivider weighted(Context context, Orientation orientation, float minimumWeight) {
        return weighted(context, orientation, minimumWeight, null);
    }

    public static ThinResizableDivider weighted(Context context, Orientation orientation,
                                                float minimumWeight, DragListener listener) {
        return weighted(context, orientation, minimumWeight, listener, UiThemes.EDITOR);
    }

    public static ThinResizableDivider weighted(Context context, Orientation orientation,
                                                float minimumWeight, DragListener listener, UiTheme theme) {
        return new ThinResizableDivider(context, orientation, listener, true, minimumWeight, theme);
    }
}
