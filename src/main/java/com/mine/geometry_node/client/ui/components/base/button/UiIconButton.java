package com.mine.geometry_node.client.ui.components.base.button;

import com.mine.geometry_node.client.ui.theme.UiMetrics;
import com.mine.geometry_node.client.ui.theme.UiThemes;
import com.mine.geometry_node.client.ui.theme.UiTheme;
import com.mine.geometry_node.client.ui.components.base.icon.UiIconColors;
import com.mine.geometry_node.client.ui.components.base.icon.SvgIconView;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.view.Gravity;

/** Shared icon-only action control with bounded visual and hit areas. */
public class UiIconButton extends FrameLayout {
    /** Placement controls chrome; the owning page supplies the action and its meaning. */
    public enum Preset { TOOLBAR, INLINE, GLYPH }

    private final Style style;
    private final View icon;
    private Integer callerIconColor;
    private Integer appliedIconColor;
    private final ShapeDrawable stateBackground = new ShapeDrawable();

    public UiIconButton(Context context, View icon) {
        this(context, icon, Style.DEFAULT);
    }

    public static UiIconButton createThemed(Context context, UiTheme theme, View icon) {
        return createThemed(context, theme, icon, Preset.TOOLBAR);
    }

    public static UiIconButton createThemed(Context context, UiTheme theme, View icon, Preset preset) {
        return new UiIconButton(context, icon, Style.forPreset(theme, preset));
    }

    public static UiIconButton createThemed(Context context, UiTheme theme, SvgIconView.Icon icon,
                                           CharSequence description) {
        return createThemed(context, theme, icon, description, Preset.TOOLBAR);
    }

    public static UiIconButton createThemed(Context context, UiTheme theme, SvgIconView.Icon icon,
                                           CharSequence description, Preset preset) {
        return createThemed(context, theme, new SvgIconView(context, icon, theme.text().secondary()), preset)
                .tooltip(description);
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
        if (icon == null || icon.getParent() != this) return;
        updateIconColor(color);
    }

    private void updateIconColor(int color) {
        Integer current = UiIconColors.get(icon);
        if (current == null) return;
        // A caller edit supersedes our saved tint, including edits made while disabled.
        if (appliedIconColor == null || !current.equals(appliedIconColor)) callerIconColor = current;
        if (color == 0) {
            if (appliedIconColor != null && !current.equals(callerIconColor)) {
                UiIconColors.apply(icon, callerIconColor);
            }
            appliedIconColor = null;
        } else {
            UiIconColors.apply(icon, color);
            appliedIconColor = UiIconColors.get(icon);
        }
    }

    @Override
    protected void onViewAdded(View child) {
        super.onViewAdded(child);
        if (child == icon && style != null) {
            applyIconColor(isEnabled() ? style.iconColor() : style.disabledIconColor());
        }
    }

    @Override
    protected void onViewRemoved(View child) {
        if (child == icon) updateIconColor(0);
        super.onViewRemoved(child);
    }

    private void updateBackground(int color, int strokeColor) {
        stateBackground.setColor(color);
        if (isEnabled() && isFocused()) {
            stateBackground.setStroke(Math.max(1, UIUtils.dp2pxInt(UiMetrics.BORDER)), style.focusColor());
        } else if (strokeColor != 0 && style.strokeWidthDp() > 0) {
            stateBackground.setStroke(Math.max(1, UIUtils.dp2pxInt(style.strokeWidthDp())), strokeColor);
        } else {
            stateBackground.setStroke(0, 0);
        }
    }

    public record Style(float cornerRadiusDp, float strokeWidthDp, int normalColor, int hoverColor,
                        int pressedColor, int disabledColor, int strokeColor,
                        int disabledStrokeColor, int iconColor, int disabledIconColor,
                        float buttonWidthDp, float buttonHeightDp, float iconSizeDp, float disabledAlpha,
                        int focusColor) {
        public static final Style DEFAULT = forTheme(UiThemes.EDITOR);

        public static Style forTheme(UiTheme theme) {
            return forPreset(theme, Preset.TOOLBAR);
        }

        public static Style forPreset(UiTheme theme, Preset preset) {
            if (preset == Preset.GLYPH) {
                // Only the caller-owned SVG changes state; never paint a button rectangle.
                return new Style(0, 0, 0, 0, 0, 0, 0, 0, 0, theme.text().disabled(),
                        UiMetrics.ICON_BUTTON_SIZE, UiMetrics.ICON_BUTTON_SIZE, UiMetrics.ICON_SIZE,
                        UiMetrics.DISABLED_ALPHA, 0);
            }
            if (preset == Preset.INLINE) {
                // Inline actions keep their supplied tint, including status and selection colors.
                return new Style(1.0f, 0, 0, theme.control().iconHover(), theme.control().iconPressed(),
                        0, 0, 0, 0, theme.text().disabled(), UiMetrics.ICON_BUTTON_SIZE,
                        UiMetrics.ICON_BUTTON_SIZE, UiMetrics.ICON_SIZE, UiMetrics.DISABLED_ALPHA,
                        theme.border().focus());
            }
            return new Style(1.0f, UiMetrics.BORDER,
                    theme.control().icon(), theme.control().iconHover(), theme.control().iconPressed(),
                    theme.control().disabledBackground(), theme.control().iconBorder(), theme.control().iconDisabledBorder(),
                    theme.text().strong(), theme.text().disabled(), UiMetrics.ICON_BUTTON_SIZE,
                    UiMetrics.ICON_BUTTON_SIZE, UiMetrics.ICON_SIZE, UiMetrics.DISABLED_ALPHA,
                    theme.border().focus());
        }

        /** Adapt compact layouts without duplicating their state palette. */
        public Style withSize(float minimumWidthDp, float minimumHeightDp, float iconSizeDp) {
            return new Style(cornerRadiusDp, strokeWidthDp, normalColor, hoverColor, pressedColor,
                    disabledColor, strokeColor, disabledStrokeColor, iconColor, disabledIconColor,
                    minimumWidthDp, minimumHeightDp, iconSizeDp, disabledAlpha, focusColor);
        }

        public Style(float cornerRadiusDp, float strokeWidthDp, int normalColor, int hoverColor,
                     int pressedColor, int disabledColor, int strokeColor,
                     int disabledStrokeColor, int iconColor, int disabledIconColor,
                     float buttonWidthDp, float buttonHeightDp, float iconSizeDp, float disabledAlpha) {
            this(cornerRadiusDp, strokeWidthDp, normalColor, hoverColor, pressedColor, disabledColor,
                    strokeColor, disabledStrokeColor, iconColor, disabledIconColor,
                    buttonWidthDp, buttonHeightDp, iconSizeDp, disabledAlpha, UiThemes.EDITOR.border().focus());
        }

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
