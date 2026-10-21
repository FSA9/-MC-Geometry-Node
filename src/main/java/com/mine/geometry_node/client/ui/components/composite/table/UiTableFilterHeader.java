package com.mine.geometry_node.client.ui.components.composite.table;

import com.mine.geometry_node.client.ui.theme.UiThemes;
import com.mine.geometry_node.client.ui.theme.UiTheme;
import com.mine.geometry_node.client.ui.components.base.button.UiActionButton;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.widget.LinearLayout;

/** Separate title/sort and filter hit targets; the table row still owns boundary resizing. */
public final class UiTableFilterHeader extends LinearLayout {
    private final UiTheme theme;
    private final UiActionButton filter;

    public UiTableFilterHeader(Context context, String title, Runnable sort, Runnable openFilter) {
        this(context, title, sort, openFilter, UiThemes.EDITOR);
    }

    public UiTableFilterHeader(Context context, String title, Runnable sort, Runnable openFilter, UiTheme theme) {
        super(context);
        this.theme = java.util.Objects.requireNonNull(theme, "theme");
        setBaselineAligned(false);
        setGravity(Gravity.CENTER_VERTICAL);
        UiActionButton label = UiTableStyle.headerCell(context, title, sort, theme);
        if (sort == null) { label.setClickable(false); label.setFocusable(false); }
        addView(label, new LayoutParams(0, -1, 1));
        filter = UiTableStyle.rowButton(context, theme);
        filter.setText("▾");
        filter.setContentDescription(title);
        filter.setOnClickListener(view -> openFilter.run());
        int size = UIUtils.dp2pxInt(18);
        LayoutParams params = new LayoutParams(size, size);
        params.rightMargin = UIUtils.dp2pxInt(5);
        addView(filter, params);
        setFilterActive(false);
    }

    public UiActionButton filterButton() { return filter; }

    @Override public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        // The two child controls own their disabled visuals and click guards.
        for (int i = 0; i < getChildCount(); i++) getChildAt(i).setEnabled(enabled);
    }

    public void setFilterActive(boolean active) {
        filter.setSelected(active);
        filter.setPalette(UiTableStyle.rowPalette(active, theme.surface().header(), theme));
    }
}
