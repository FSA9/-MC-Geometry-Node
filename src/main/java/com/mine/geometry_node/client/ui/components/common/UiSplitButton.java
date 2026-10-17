package com.mine.geometry_node.client.ui.components.common;

import com.mine.geometry_node.client.ui.theme.UiTheme;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.widget.LinearLayout;

/** Two independent actions sharing one outline, with no spacing between their hit regions. */
public final class UiSplitButton extends LinearLayout {
    private final UiActionButton primary;
    private final UiActionButton secondary;

    public UiSplitButton(Context context, CharSequence text, UiActionButton.Palette palette,
                         Runnable primaryAction, Runnable secondaryAction) {
        super(context);
        setOrientation(HORIZONTAL);
        int border = Math.max(1, UIUtils.dp2pxInt(1));
        setPadding(border, border, border, border);
        ShapeDrawable background = new ShapeDrawable();
        background.setColor(palette.normal());
        background.setCornerRadius(UIUtils.dp2px(3));
        background.setStroke(border, UiTheme.Control.ICON_BORDER);
        setBackground(background);

        primary = button(context, text, palette, 9, primaryAction);
        secondary = button(context, "▾", palette, 5, secondaryAction);
        addView(primary, new LayoutParams(-2, -1));
        icyllis.modernui.view.View divider = new icyllis.modernui.view.View(context);
        ShapeDrawable separator = new ShapeDrawable();
        separator.setColor(UiTheme.Control.ICON_BORDER);
        divider.setBackground(separator);
        addView(divider, new LayoutParams(border, -1));
        addView(secondary, new LayoutParams(-2, -1));
    }

    public UiActionButton primaryButton() { return primary; }
    public UiActionButton secondaryButton() { return secondary; }

    private static UiActionButton button(Context context, CharSequence text, UiActionButton.Palette palette,
                                         int paddingDp, Runnable action) {
        var button = UiActionButton.withPalette(context, text, palette, UiActionButton.Density.COMPACT,
                new UiActionButton.Style(0, 0, 0, UiTheme.Metrics.DISABLED_ALPHA));
        UIUtils.setLockedTextSize(button, 11);
        button.setSingleLine(true);
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setPadding(UIUtils.dp2pxInt(paddingDp), 0, UIUtils.dp2pxInt(paddingDp), 0);
        button.setOnClickListener(v -> action.run());
        return button;
    }
}
