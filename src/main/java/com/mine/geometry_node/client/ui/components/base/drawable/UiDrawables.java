package com.mine.geometry_node.client.ui.components.base.drawable;

import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.graphics.drawable.ShapeDrawable;

/** Shared shape construction. Every call returns a drawable owned by its caller. */
public final class UiDrawables {
    private UiDrawables() {}

    public static ShapeDrawable solid(int color) {
        ShapeDrawable drawable = new ShapeDrawable();
        drawable.setColor(color);
        return drawable;
    }

    /** Radius and border width use the project's logical dp units. */
    public static ShapeDrawable rect(int color, float radiusDp, float strokeWidthDp, int strokeColor) {
        ShapeDrawable drawable = solid(color);
        drawable.setCornerRadius(UIUtils.dp2px(radiusDp));
        if (strokeWidthDp > 0) {
            drawable.setStroke(UIUtils.dp2pxInt(strokeWidthDp), strokeColor);
        }
        return drawable;
    }
}
