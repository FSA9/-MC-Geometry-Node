package com.mine.geometry_node.client.ui.components.icon;

import icyllis.modernui.view.View;

/** Color adapter for the two icon renderers used by shared controls. */
public final class UiIconColors {
    private UiIconColors() {}

    public static void apply(View icon, int color) {
        // A zero style color means retain the caller's icon color.
        if (color == 0) return;
        if (icon instanceof SvgIconView svg) svg.setIconColor(color);
        else if (icon instanceof VectorIconView vector) vector.setIconColor(color);
    }
}
