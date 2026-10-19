package com.mine.geometry_node.client.ui.components.composite.list;

import com.mine.geometry_node.client.ui.components.base.button.UiActionButton;

import com.mine.geometry_node.client.ui.theme.UiTheme;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.text.TextUtils;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;

/** A name and count over one shared button; callers own selection and navigation. */
public final class UiNavigationRow extends FrameLayout {
    private final UiActionButton button;
    private final TextView name;

    public UiNavigationRow(Context context, String title, long count) {
        super(context);
        button = new UiActionButton(context, "", UiActionButton.Role.QUIET,
                UiActionButton.Density.COMPACT,
                new UiActionButton.Style(0, 0, 0, UiTheme.Metrics.DISABLED_ALPHA));
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setMinimumWidth(0);
        button.setMinimumHeight(0);
        button.setPadding(0, 0, 0, 0);
        button.setContentDescription(title + ": " + count);
        setContentDescription(button.getContentDescription());
        addView(button, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout cells = new LinearLayout(context);
        cells.setBaselineAligned(false);
        cells.setGravity(Gravity.CENTER_VERTICAL);
        cells.setPadding(UIUtils.dp2pxInt(10), 0, UIUtils.dp2pxInt(10), 0);
        name = UIUtils.createLockedTextView(context, title, 12, UiTheme.Text.NORMAL);
        name.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setPadding(0, 0, UIUtils.dp2pxInt(6), 0);
        cells.addView(name, new LinearLayout.LayoutParams(0, -1, 1));
        TextView total = UIUtils.createLockedTextView(context, Long.toString(count), 10, UiTheme.Text.MUTED);
        total.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        cells.addView(total, new LinearLayout.LayoutParams(-2, -1));
        addView(cells, new FrameLayout.LayoutParams(-1, -1));
    }

    public UiActionButton button() {
        return button;
    }

    @Override
    public boolean performClick() {
        return isEnabled() && button.performClick();
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (button != null) button.setEnabled(enabled);
    }

    public void setPalette(UiActionButton.Palette palette) {
        button.setPalette(palette);
        name.setTextColor(button.palette().text());
    }
}
