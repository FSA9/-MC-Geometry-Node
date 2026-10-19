package com.mine.geometry_node.client.ui.components.composite.table;

import com.mine.geometry_node.client.ui.components.base.button.UiActionButton;
import com.mine.geometry_node.client.ui.theme.UiTheme;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.widget.LinearLayout;

/** Separate title/sort and filter hit targets; the table row still owns boundary resizing. */
public final class UiTableFilterHeader extends LinearLayout {
    private final UiActionButton filter;

    public UiTableFilterHeader(Context context, String title, Runnable sort, Runnable openFilter) {
        super(context);
        setBaselineAligned(false);
        setGravity(Gravity.CENTER_VERTICAL);
        UiActionButton label = UiTableStyle.headerCell(context, title, sort);
        if (sort == null) { label.setClickable(false); label.setFocusable(false); }
        addView(label, new LayoutParams(0, -1, 1));
        filter = UiTableStyle.rowButton(context);
        filter.setText("▾");
        filter.setContentDescription(title);
        filter.setOnClickListener(view -> openFilter.run());
        LayoutParams params = new LayoutParams(UIUtils.dp2pxInt(18), UIUtils.dp2pxInt(22));
        params.rightMargin = UIUtils.dp2pxInt(5);
        addView(filter, params);
        setFilterActive(false);
    }

    public UiActionButton filterButton() { return filter; }

    public void setFilterActive(boolean active) {
        filter.setSelected(active);
        filter.setText(active ? "▾•" : "▾");
        filter.setPalette(UiTableStyle.rowPalette(active, UiTheme.Surface.HEADER));
    }
}
