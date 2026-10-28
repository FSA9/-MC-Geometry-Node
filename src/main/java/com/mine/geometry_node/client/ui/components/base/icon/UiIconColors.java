package com.mine.geometry_node.client.ui.components.base.icon;

import icyllis.modernui.view.View;

/** Color adapter for resource icons used by shared controls. */
public final class UiIconColors {
    private UiIconColors() {}

    /** Returns the renderer's resolved tint, or null for an unsupported caller-owned view. */
    public static Integer get(View icon) {
        if (icon instanceof SvgIconView svg) return svg.getIconColor();
        return null;
    }

    public static void apply(View icon, int color) {
        // A zero style color means retain the caller's icon color.
        if (color == 0) return;
        if (icon instanceof SvgIconView svg) svg.setIconColor(color);
    }
}
