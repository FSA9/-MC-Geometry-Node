package com.mine.geometry_node.client.ui.components.base.menu;

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
        TextView row = new TextView(context) {
            @Override public boolean performClick() { return isEnabled() && super.performClick(); }
        };
        row.setText(text);
        UIUtils.setLockedTextSize(row, 12);
        row.setTextColor(textColors(UiTheme.Text.NORMAL));
        row.setSingleLine(true);
        row.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        row.setPadding(UIUtils.dp2pxInt(10), 0, UIUtils.dp2pxInt(10), 0);
        decorate(row, false);
        row.setSelected(selected);
        row.setEnabled(enabled);
        row.setOnClickListener(view -> action.run());
        return row;
    }

    /** Preview children must be passive; the card owns selection and click handling. */
    public static LinearLayout card(Context context, boolean selected, boolean enabled) {
        LinearLayout card = new LinearLayout(context) {
            @Override public boolean performClick() { return isEnabled() && super.performClick(); }
        };
        card.setOrientation(LinearLayout.VERTICAL);
        decorate(card, true);
        card.setSelected(selected);
        card.setEnabled(enabled);
        card.setAlpha(enabled ? 1f : UiTheme.Metrics.DISABLED_ALPHA);
        return card;
    }

    /** Keeps one background object through hover, focus, press and selection changes. */
    public static void decorate(View item, boolean card) {
        int base = card ? UiTheme.Control.INPUT : 0;
        ShapeDrawable background = new ShapeDrawable();
        background.setCornerRadius(UIUtils.dp2px(4));
        background.setColor(new ColorStateList(new int[][]{
                {-R.attr.state_enabled, R.attr.state_selected}, {-R.attr.state_enabled},
                {R.attr.state_selected}, {R.attr.state_pressed}, {R.attr.state_hovered}, {}
        }, new int[]{UiTheme.Control.SELECTED, base, UiTheme.Control.SELECTED,
                UiTheme.Control.ICON_PRESSED, card ? UiTheme.Control.ICON_HOVER : UiTheme.Control.SELECTED, base}));
        background.setStroke(Math.max(1, UIUtils.dp2pxInt(1)), new ColorStateList(new int[][]{
                {R.attr.state_selected}, {R.attr.state_enabled, R.attr.state_focused}, {}
        }, new int[]{UiTheme.Control.FOCUS, UiTheme.Control.FOCUS, 0}));
        item.setBackground(background);
        item.setClickable(true);
        item.setFocusable(true);
    }

    public static ColorStateList textColors(int normal) {
        return new ColorStateList(new int[][]{
                {-R.attr.state_enabled}, {R.attr.state_selected}, {R.attr.state_hovered}, {}
        }, new int[]{UiTheme.Text.DISABLED, UiTheme.Text.STRONG, UiTheme.Text.STRONG, normal});
    }
}
