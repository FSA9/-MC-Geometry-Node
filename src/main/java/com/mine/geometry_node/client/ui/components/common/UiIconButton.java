package com.mine.geometry_node.client.ui.components.common;

import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.FrameLayout;

/** Shared icon-only action control with bounded visual and hit areas. */
public final class UiIconButton extends FrameLayout {
    private final Style style;

    public UiIconButton(Context context, View icon) {
        this(context, icon, Style.DEFAULT);
    }

    public UiIconButton(Context context, View icon, Style style) {
        super(context);
        this.style = style == null ? Style.DEFAULT : style;
        setClickable(true);
        setFocusable(true);
        setBackground(background(this.style.normalColor()));
        if (icon != null) addView(icon, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        setOnHoverListener((view, event) -> {
            setBackground(background(event.getAction() == icyllis.modernui.view.MotionEvent.ACTION_HOVER_ENTER
                    ? this.style.hoverColor() : this.style.normalColor()));
            return false;
        });
    }

    private ShapeDrawable background(int color) {
        ShapeDrawable drawable = new ShapeDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(UIUtils.dp2px(style.cornerRadiusDp()));
        if (style.strokeColor() != 0) drawable.setStroke(Math.max(1, UIUtils.dp2pxInt(style.strokeWidthDp())), style.strokeColor());
        return drawable;
    }

    public record Style(float cornerRadiusDp, float strokeWidthDp, int normalColor, int hoverColor, int strokeColor) {
        public static final Style DEFAULT = new Style(1.0f, 1.0f, 0xFF3B3B3B, 0xFF484848, 0xFF444444);
    }
}
