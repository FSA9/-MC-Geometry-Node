package com.mine.geometry_node.client.ui.components.base.button;

import com.mine.geometry_node.client.ui.theme.UiMetrics;
import com.mine.geometry_node.client.ui.theme.UiThemes;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.theme.UiTheme;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.Button;

/** Shared command button for dialog and popup action rows. */
public class UiActionButton extends Button {
    public static final Style DEFAULT_STYLE = Style.forTheme(UiThemes.EDITOR);

    public enum Role { PRIMARY, SECONDARY, INLINE, DANGER, QUIET }

    public enum Density {
        COMPACT(UiMetrics.TEXT_COMPACT),
        NORMAL(UiMetrics.TEXT_CONTROL);

        private final float textSize;

        Density(float textSize) {
            this.textSize = textSize;
        }
    }

    private final UiTheme theme;
    private Palette palette;
    private Style style;
    private final ShapeDrawable stateBackground = new ShapeDrawable();

    public UiActionButton(Context context, CharSequence text, Role role, Density density) {
        this(context, text, role, density, DEFAULT_STYLE);
    }

    public UiActionButton(Context context, CharSequence text, Role role, Density density, Style style) {
        this(context, UiThemes.EDITOR, text, role, density, style);
    }

    public UiActionButton(Context context, UiTheme theme, CharSequence text, Role role,
                          Density density, Style style) {
        this(context, theme, text, density, style, Palette.forRole(theme, role));
    }

    private UiActionButton(Context context, UiTheme theme, CharSequence text, Density density, Style style, Palette palette) {
        super(context);
        this.theme = java.util.Objects.requireNonNull(theme, "theme");
        this.palette = palette != null ? palette : Palette.forRole(theme, Role.SECONDARY);
        this.style = style != null ? style : Style.forTheme(theme);
        stateBackground.setCornerRadius(UIUtils.dp2px(this.style.cornerRadiusDp()));
        // Button's native background may supply padding. Install ours only once and retain it.
        int left = getPaddingLeft(), top = getPaddingTop();
        int right = getPaddingRight(), bottom = getPaddingBottom();
        setBackground(stateBackground);
        setPadding(left, top, right, bottom);
        Density resolvedDensity = density != null ? density : Density.NORMAL;
        setText(text != null ? text : "");
        setGravity(Gravity.CENTER);
        setTextColor(this.palette.text());
        UIUtils.setLockedTextSize(this, resolvedDensity.textSize);
        updateVisualState();
    }

    public static UiActionButton withPalette(Context context, CharSequence text, Palette palette,
                                              Density density, Style style) {
        return new UiActionButton(context, UiThemes.EDITOR, text, density, style, palette);
    }

    public Palette palette() {
        return palette;
    }

    /** A visual refresh must never execute the action or replace the caller's listeners. */
    public void setPalette(Palette palette) {
        this.palette = palette != null ? palette : Palette.forRole(theme, Role.SECONDARY);
        setTextColor(this.palette.text());
        updateVisualState();
    }

    public void setStyle(Style style) {
        this.style = style != null ? style : Style.forTheme(theme);
        stateBackground.setCornerRadius(UIUtils.dp2px(this.style.cornerRadiusDp()));
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

    public static UiActionButton createThemed(Context context, UiTheme theme, CharSequence text,
                                              Role role, View.OnClickListener listener) {
        return createThemed(context, theme, text, role, Density.NORMAL, listener);
    }

    public static UiActionButton createThemed(Context context, UiTheme theme, CharSequence text,
                                              Role role, Density density, View.OnClickListener listener) {
        return createThemed(context, theme, text, role, density, Style.forTheme(theme), listener);
    }

    public static UiActionButton createThemed(Context context, UiTheme theme, CharSequence text,
                                              Role role, Density density, Style style,
                                              View.OnClickListener listener) {
        UiActionButton button = new UiActionButton(context, theme, text, role, density, style);
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
        if (style != null && palette != null && stateBackground != null) updateVisualState();
    }

    private void updateVisualState() {
        setAlpha(isEnabled() ? 1.0f : style.disabledAlpha());
        int color = !isEnabled() ? palette.normal()
                : isPressed() ? palette.pressed() : isHovered() ? palette.hover() : palette.normal();
        stateBackground.setColor(color);
        if (isEnabled() && isFocused()) {
            stateBackground.setStroke(Math.max(1, UIUtils.dp2pxInt(UiMetrics.BORDER)), style.focusColor());
        } else if (style.strokeWidthDp() > 0.0f) {
            stateBackground.setStroke(Math.max(1, UIUtils.dp2pxInt(style.strokeWidthDp())), style.strokeColor());
        } else {
            stateBackground.setStroke(0, 0);
        }
    }

    /** Visual parameters in density-independent units; role and density remain semantic choices. */
    public record Style(float cornerRadiusDp, float strokeWidthDp, int strokeColor, float disabledAlpha,
                        int focusColor) {
        /** Existing explicit styles retain editor interaction defaults. Zero colors stay literal. */
        public Style(float cornerRadiusDp, float strokeWidthDp, int strokeColor, float disabledAlpha) {
            this(cornerRadiusDp, strokeWidthDp, strokeColor, disabledAlpha, UiThemes.EDITOR.border().focus());
        }

        public static Style forTheme(UiTheme theme) {
            return new Style(UiMetrics.RADIUS_SMALL, UiMetrics.BORDER, theme.border().strong(),
                    UiMetrics.DISABLED_ALPHA, theme.border().focus());
        }
    }

    public record Palette(int normal, int hover, int pressed, int text) {
        public static Palette forRole(UiTheme theme, Role role) {
            var buttons = theme.buttons();
            UiTheme.ButtonColors colors = switch (role != null ? role : Role.SECONDARY) {
                case PRIMARY -> buttons.primary();
                case SECONDARY -> buttons.secondary();
                case INLINE -> buttons.inline();
                case DANGER -> buttons.danger();
                case QUIET -> buttons.quiet();
            };
            return new Palette(colors.normal(), colors.hover(), colors.pressed(), colors.text());
        }

        /** Hover and press retain the toggle's current hue, including its enabled blue. */
        public static Palette toggle(boolean selected) {
            return toggle(UiThemes.EDITOR, selected);
        }

        public static Palette toggle(UiTheme theme, boolean selected) {
            return new Palette(selected ? theme.selection().fill() : theme.control().icon(),
                    selected ? theme.selection().hover() : theme.control().iconHover(),
                    selected ? theme.selection().pressed() : theme.control().iconPressed(),
                    selected ? theme.text().strong() : theme.text().normal());
        }

        /** Selection-aware defaults shared by table and settings rows; base may be transparent. */
        public static Palette row(boolean selected, int base) {
            return row(UiThemes.EDITOR, selected, base);
        }

        public static Palette row(UiTheme theme, boolean selected, int base) {
            return new Palette(selected ? theme.selection().fill() : base,
                    selected ? theme.selection().hover() : theme.control().iconHover(),
                    selected ? theme.selection().pressed() : theme.control().iconPressed(), theme.text().normal());
        }
    }
}
