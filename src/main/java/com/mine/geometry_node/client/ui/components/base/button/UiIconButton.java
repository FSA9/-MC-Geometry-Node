package com.mine.geometry_node.client.ui.components.base.button;

import com.mine.geometry_node.client.ui.components.base.icon.UiIconColors;

import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.theme.UiTheme;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.view.Gravity;

/** Shared icon-only action control with bounded visual and hit areas. */
public class UiIconButton extends FrameLayout {
    private final Style style;
    private final View icon;
    private final ShapeDrawable stateBackground = new ShapeDrawable();

    public UiIconButton(Context context, View icon) {
        this(context, icon, Style.DEFAULT);
    }

    public UiIconButton(Context context, View icon, Style style) {
        super(context);
        this.style = style == null ? Style.DEFAULT : style;
        this.icon = icon;
        stateBackground.setCornerRadius(UIUtils.dp2px(this.style.cornerRadiusDp()));
        setBackground(stateBackground);
        setClickable(true);
        setFocusable(true);
        if (this.style.buttonWidthDp() > 0) setMinimumWidth(UIUtils.dp2pxInt(this.style.buttonWidthDp()));
        if (this.style.buttonHeightDp() > 0) setMinimumHeight(UIUtils.dp2pxInt(this.style.buttonHeightDp()));
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
    }

    @Override
    public boolean performClick() {
        return isEnabled() && super.performClick();
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        if (style != null && stateBackground != null) updateVisualState();
    }

    public UiIconButton tooltip(CharSequence text) {
        setTooltipText(text);
        setContentDescription(text);
        return this;
    }

    private void updateVisualState() {
        if (!isEnabled()) {
            setAlpha(style.disabledAlpha());
            updateBackground(style.disabledColor(), style.disabledStrokeColor());
            applyIconColor(style.disabledIconColor());
        } else {
            setAlpha(1.0f);
            int color = isPressed() ? style.pressedColor() : isHovered() ? style.hoverColor() : style.normalColor();
            updateBackground(color, style.strokeColor());
            applyIconColor(style.iconColor());
        }
    }

    private void applyIconColor(int color) {
        UiIconColors.apply(icon, color);
    }

    private void updateBackground(int color, int strokeColor) {
        stateBackground.setColor(color);
        if (isEnabled() && isFocused()) {
            stateBackground.setStroke(Math.max(1, UIUtils.dp2pxInt(UiTheme.Metrics.BORDER)), UiTheme.Control.FOCUS);
        } else if (strokeColor != 0 && style.strokeWidthDp() > 0) {
            stateBackground.setStroke(Math.max(1, UIUtils.dp2pxInt(style.strokeWidthDp())), strokeColor);
        } else {
            stateBackground.setStroke(0, 0);
        }
    }

    public record Style(float cornerRadiusDp, float strokeWidthDp, int normalColor, int hoverColor,
                        int pressedColor, int disabledColor, int strokeColor,
                        int disabledStrokeColor, int iconColor, int disabledIconColor,
                        float buttonWidthDp, float buttonHeightDp, float iconSizeDp, float disabledAlpha) {
        public static final Style DEFAULT = new Style(1.0f, UiTheme.Metrics.BORDER,
                UiTheme.Control.ICON, UiTheme.Control.ICON_HOVER, UiTheme.Control.ICON_PRESSED,
                UiTheme.Surface.PANEL, UiTheme.Control.ICON_BORDER, UiTheme.Control.ICON_DISABLED_BORDER,
                UiTheme.Text.STRONG, UiTheme.Text.DISABLED, UiTheme.Metrics.ICON_BUTTON_SIZE,
                UiTheme.Metrics.ICON_BUTTON_SIZE, UiTheme.Metrics.ICON_SIZE, 0.5f);

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
