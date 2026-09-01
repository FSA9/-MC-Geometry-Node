package com.mine.geometry_node.client.ui.components.common;

import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.MotionEvent;

/** Shared icon-only action control with bounded visual and hit areas. */
public class UiIconButton extends FrameLayout {
    private final Style style;
    private final View icon;
    private boolean hovered;

    public UiIconButton(Context context, View icon) {
        this(context, icon, Style.DEFAULT);
    }

    public UiIconButton(Context context, View icon, Style style) {
        super(context);
        this.style = style == null ? Style.DEFAULT : style;
        this.icon = icon;
        setClickable(true);
        setFocusable(true);
        if (style.buttonWidthDp() > 0) setMinimumWidth(UIUtils.dp2pxInt(style.buttonWidthDp()));
        if (style.buttonHeightDp() > 0) setMinimumHeight(UIUtils.dp2pxInt(style.buttonHeightDp()));
        if (icon != null) {
            icon.setClickable(false);
            int iconSize = this.style.iconSizeDp() > 0
                    ? UIUtils.dp2pxInt(this.style.iconSizeDp()) : LayoutParams.MATCH_PARENT;
            FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(iconSize, iconSize);
            iconParams.gravity = Gravity.CENTER;
            addView(icon, iconParams);
            applyIconColor(this.style.iconColor());
        }
        updateVisualState();
        setOnHoverListener((view, event) -> {
            if (!isEnabled()) return false;
            if (event.getAction() == MotionEvent.ACTION_HOVER_ENTER) {
                hovered = true;
                updateVisualState();
            } else if (event.getAction() == MotionEvent.ACTION_HOVER_EXIT) {
                hovered = false;
                updateVisualState();
            }
            return false;
        });
        setOnTouchListener((view, event) -> {
            if (!isEnabled()) return false;
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                setBackground(background(this.style.pressedColor(), this.style.strokeColor()));
            } else if (event.getActionMasked() == MotionEvent.ACTION_UP
                    || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                updateVisualState();
            }
            return false;
        });
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        hovered = false;
        updateVisualState();
    }

    public UiIconButton tooltip(CharSequence text) {
        setTooltipText(text);
        setContentDescription(text);
        return this;
    }

    private void updateVisualState() {
        if (!isEnabled()) {
            setAlpha(style.disabledAlpha());
            setBackground(background(style.disabledColor(), style.disabledStrokeColor()));
            applyIconColor(style.disabledIconColor());
        } else {
            setAlpha(1.0f);
            setBackground(background(hovered ? style.hoverColor() : style.normalColor(), style.strokeColor()));
            applyIconColor(style.iconColor());
        }
    }

    private void applyIconColor(int color) {
        if (icon instanceof SvgIconView svg && color != 0) svg.setIconColor(color);
    }

    private ShapeDrawable background(int color, int strokeColor) {
        ShapeDrawable drawable = new ShapeDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(UIUtils.dp2px(style.cornerRadiusDp()));
        if (strokeColor != 0 && style.strokeWidthDp() > 0) {
            drawable.setStroke(Math.max(1, UIUtils.dp2pxInt(style.strokeWidthDp())), strokeColor);
        }
        return drawable;
    }

    public record Style(float cornerRadiusDp, float strokeWidthDp, int normalColor, int hoverColor,
                        int pressedColor, int disabledColor, int strokeColor,
                        int disabledStrokeColor, int iconColor, int disabledIconColor,
                        float buttonWidthDp, float buttonHeightDp, float iconSizeDp, float disabledAlpha) {
        public static final Style DEFAULT = new Style(1.0f, 1.0f, 0xFF3B3B3B, 0xFF484848,
                0xFF303030, 0xFF252525, 0xFF444444, 0xFF383838,
                0xFFFFFFFF, 0xFF999999, 24.0f, 24.0f, 14.0f, 0.5f);

        public Style(float cornerRadiusDp, float strokeWidthDp, int normalColor,
                     int hoverColor, int strokeColor) {
            this(cornerRadiusDp, strokeWidthDp, normalColor, hoverColor,
                    normalColor, normalColor, strokeColor, strokeColor,
                    0, 0, 24.0f, 24.0f, 14.0f, 0.5f);
        }

        /** Compatibility constructor used by existing style helpers. */
        public Style(float cornerRadiusDp, float strokeWidthDp, int normalColor,
                     int hoverColor, int pressedColor, int disabledColor, int strokeColor,
                     float iconSizeDp, float disabledAlpha) {
            this(cornerRadiusDp, strokeWidthDp, normalColor, hoverColor, pressedColor, disabledColor,
                    strokeColor, strokeColor, 0, 0, 0, 0, iconSizeDp, disabledAlpha);
        }
    }
}
