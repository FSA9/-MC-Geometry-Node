package com.mine.geometry_node.client.ui.components.base.menu;

import com.mine.geometry_node.client.ui.theme.UiMetrics;
import com.mine.geometry_node.client.ui.theme.UiThemes;
import com.mine.geometry_node.client.ui.theme.UiTheme;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.R;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.util.ColorStateList;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;

/** Native drawable states shared by text rows and cards; callers own item geometry. */
public final class UiMenuItems {
    private UiMenuItems() {}

    public static TextView row(Context context, String text, boolean selected,
                               boolean enabled, Runnable action) {
        return row(context, text, selected, enabled, action, UiThemes.EDITOR);
    }

    public static TextView row(Context context, String text, boolean selected,
                               boolean enabled, Runnable action, UiTheme theme) {
        java.util.Objects.requireNonNull(theme, "theme");
        TextView row = new TextView(context) {
            @Override public boolean performClick() { return isEnabled() && super.performClick(); }
        };
        row.setText(text);
        UIUtils.setLockedTextSize(row, 12);
        row.setTextColor(textColors(theme.text().normal(), theme));
        row.setSingleLine(true);
        row.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        row.setPadding(UIUtils.dp2pxInt(10), 0, UIUtils.dp2pxInt(10), 0);
        decorate(row, false, theme);
        row.setSelected(selected);
        row.setEnabled(enabled);
        row.setOnClickListener(view -> action.run());
        return row;
    }

    /** Preview children must be passive; the card owns selection and click handling. */
    public static LinearLayout card(Context context, boolean selected, boolean enabled) {
        return card(context, selected, enabled, UiThemes.EDITOR);
    }

    public static LinearLayout card(Context context, boolean selected, boolean enabled, UiTheme theme) {
        java.util.Objects.requireNonNull(theme, "theme");
        LinearLayout card = new LinearLayout(context) {
            @Override public boolean performClick() { return isEnabled() && super.performClick(); }
            @Override protected void drawableStateChanged() {
                super.drawableStateChanged();
                // The card owns dimming; passive preview and caption children retain their colors.
                setAlpha(isEnabled() ? 1f : UiMetrics.DISABLED_ALPHA);
            }
        };
        card.setOrientation(LinearLayout.VERTICAL);
        decorate(card, true, theme);
        card.setSelected(selected);
        card.setEnabled(enabled);
        return card;
    }

    /** Keeps one background object through hover, focus, press and selection changes. */
    public static void decorate(View item, boolean card) {
        decorate(item, card, UiThemes.EDITOR);
    }

    public static void decorate(View item, boolean card, UiTheme theme) {
        java.util.Objects.requireNonNull(theme, "theme");
        decorate(item, card ? theme.control().input() : 0, 4, theme);
    }

    /** Native row states for headers with an owner-specified resting surface and radius. */
    public static void decorate(View item, int base, float cornerRadiusDp, UiTheme theme) {
        java.util.Objects.requireNonNull(theme, "theme");
        ShapeDrawable background = new ShapeDrawable();
        background.setCornerRadius(UIUtils.dp2px(cornerRadiusDp));
        background.setColor(new ColorStateList(new int[][]{
                {-R.attr.state_enabled, R.attr.state_selected}, {-R.attr.state_enabled},
                {R.attr.state_selected, R.attr.state_pressed},
                {R.attr.state_selected, R.attr.state_hovered},
                {R.attr.state_selected}, {R.attr.state_pressed}, {R.attr.state_hovered}, {}
        }, new int[]{theme.selection().subtle(), base, theme.selection().subtlePressed(),
                theme.selection().subtleHover(), theme.selection().subtle(),
                theme.control().iconPressed(), theme.control().iconHover(), base}));
        background.setStroke(Math.max(1, UIUtils.dp2pxInt(1)), new ColorStateList(new int[][]{
                {R.attr.state_selected}, {R.attr.state_enabled, R.attr.state_focused}, {}
        }, new int[]{theme.selection().outline(), theme.border().focus(), 0}));
        item.setBackground(background);
        item.setClickable(true);
        item.setFocusable(true);
    }

    public static ColorStateList textColors(int normal) {
        return textColors(normal, UiThemes.EDITOR);
    }

    public static ColorStateList textColors(int normal, UiTheme theme) {
        java.util.Objects.requireNonNull(theme, "theme");
        return new ColorStateList(new int[][]{
                {-R.attr.state_enabled}, {R.attr.state_selected}, {R.attr.state_hovered}, {}
        }, new int[]{theme.text().disabled(), theme.text().strong(), theme.text().strong(), normal});
    }
}
