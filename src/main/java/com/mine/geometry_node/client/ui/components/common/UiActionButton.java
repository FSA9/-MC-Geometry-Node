package com.mine.geometry_node.client.ui.components.common;

import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.theme.UiTheme;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.Button;

/** Shared command button for dialog and popup action rows. */
public final class UiActionButton extends Button {
    public static final Style DEFAULT_STYLE = new Style(UiTheme.Metrics.RADIUS_SMALL,
            UiTheme.Metrics.BORDER, UiTheme.Control.BORDER, UiTheme.Metrics.DISABLED_ALPHA);

    public enum Role {
        PRIMARY(UiTheme.PRIMARY),
        SECONDARY(UiTheme.SECONDARY),
        INLINE(UiTheme.INLINE),
        DANGER(UiTheme.DANGER),
        QUIET(UiTheme.QUIET);

        private final Palette palette;

        Role(UiTheme.ButtonColors colors) {
            this.palette = new Palette(colors.normal(), colors.hover(), colors.pressed(), colors.text());
        }
    }

    public enum Density {
        COMPACT(UiTheme.Metrics.TEXT_COMPACT),
        NORMAL(UiTheme.Metrics.TEXT_CONTROL);

        private final float textSize;

        Density(float textSize) {
            this.textSize = textSize;
        }
    }

    private Palette palette;
    private final Style style;

    public UiActionButton(Context context, CharSequence text, Role role, Density density) {
        this(context, text, role, density, DEFAULT_STYLE);
    }

    public UiActionButton(Context context, CharSequence text, Role role, Density density, Style style) {
        this(context, text, density, style, (role != null ? role : Role.SECONDARY).palette);
    }

    private UiActionButton(Context context, CharSequence text, Density density, Style style, Palette palette) {
        super(context);
        this.palette = palette != null ? palette : Role.SECONDARY.palette;
        this.style = style != null ? style : DEFAULT_STYLE;
        Density resolvedDensity = density != null ? density : Density.NORMAL;
        setText(text != null ? text : "");
        setGravity(Gravity.CENTER);
        setTextColor(this.palette.text());
        UIUtils.setLockedTextSize(this, resolvedDensity.textSize);
        updateVisualState();
    }

    public static UiActionButton withPalette(Context context, CharSequence text, Palette palette,
                                              Density density, Style style) {
        return new UiActionButton(context, text, density, style, palette);
    }

    public Palette palette() {
        return palette;
    }

    /** A visual refresh must never execute the action or replace the caller's listeners. */
    public void setPalette(Palette palette) {
        this.palette = palette != null ? palette : Role.SECONDARY.palette;
        setTextColor(this.palette.text());
        updateVisualState();
    }

    public static UiActionButton create(Context context, CharSequence text, Role role,
                                        View.OnClickListener listener) {
        return create(context, text, role, Density.NORMAL, DEFAULT_STYLE, listener);
    }

    public static UiActionButton create(Context context, CharSequence text, Role role,
                                        Density density, Style style, View.OnClickListener listener) {
        UiActionButton button = new UiActionButton(context, text, role, density, style);
        button.setOnClickListener(listener);
        return button;
    }

    @Override
    public boolean performClick() {
        return isEnabled() && super.performClick();
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        if (style != null && palette != null) updateVisualState();
    }

    private void updateVisualState() {
        setAlpha(isEnabled() ? 1.0f : style.disabledAlpha());
        int color = !isEnabled() ? palette.normal()
                : isPressed() ? palette.pressed() : isHovered() ? palette.hover() : palette.normal();
        setBackground(background(color));
    }

    private ShapeDrawable background(int color) {
        ShapeDrawable drawable = new ShapeDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(UIUtils.dp2px(style.cornerRadiusDp()));
        if (isEnabled() && isFocused()) {
            drawable.setStroke(Math.max(1, UIUtils.dp2pxInt(UiTheme.Metrics.BORDER)), UiTheme.Control.FOCUS);
        } else if (style.strokeWidthDp() > 0.0f) {
            drawable.setStroke(Math.max(1, UIUtils.dp2pxInt(style.strokeWidthDp())), style.strokeColor());
        }
        return drawable;
    }

    /** Visual parameters in density-independent units; role and density remain semantic choices. */
    public record Style(float cornerRadiusDp, float strokeWidthDp, int strokeColor, float disabledAlpha) {
    }

    public record Palette(int normal, int hover, int pressed, int text) {
        /** Hover and press retain the toggle's current hue, including its enabled blue. */
        public static Palette toggle(boolean selected) {
            int base = selected ? UiTheme.Control.CHECKED : UiTheme.Control.ICON;
            return new Palette(base, shade(base, 0.12f), shade(base, -0.12f),
                    selected ? UiTheme.Text.STRONG : UiTheme.Text.NORMAL);
        }

        private static int shade(int color, float amount) {
            int target = amount > 0 ? 255 : 0;
            float fraction = Math.abs(amount);
            int r = (color >>> 16) & 255, g = (color >>> 8) & 255, b = color & 255;
            r += Math.round((target - r) * fraction);
            g += Math.round((target - g) * fraction);
            b += Math.round((target - b) * fraction);
            return (color & 0xFF000000) | (r << 16) | (g << 8) | b;
        }
    }
}
