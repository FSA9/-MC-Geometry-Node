package com.mine.geometry_node.client.ui.components.composite.table;

import com.mine.geometry_node.client.ui.theme.UiThemes;
import com.mine.geometry_node.client.ui.theme.UiTheme;
import com.mine.geometry_node.client.ui.components.base.button.UiActionButton;
import com.mine.geometry_node.client.ui.components.base.icon.SvgIconView;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.widget.LinearLayout;

/** Separate title/sort and filter hit targets; the table row still owns boundary resizing. */
public final class UiTableFilterHeader extends LinearLayout {
    private final UiTheme theme;
    private final UiActionButton filter;
    private final SvgIconView indicator;
    private final UiActionButton label;

    public UiTableFilterHeader(Context context, String title, Runnable sort, Runnable openFilter) {
        this(context, title, sort, openFilter, UiThemes.EDITOR);
    }

    public UiTableFilterHeader(Context context, String title, Runnable sort, Runnable openFilter, UiTheme theme) {
        super(context);
        this.theme = java.util.Objects.requireNonNull(theme, "theme");
        setBaselineAligned(false);
        setGravity(Gravity.CENTER_VERTICAL);
        label = UiTableStyle.headerCell(context, title, sort, theme);
        if (sort == null) { label.setClickable(false); label.setFocusable(false); }
        addView(label, new LayoutParams(0, -1, 1));
        indicator = new SvgIconView(context, SvgIconView.Icon.ARROW_UP, theme.text().muted());
        indicator.setVisibility(GONE);
        indicator.setDuplicateParentStateEnabled(true);
        addView(indicator, new LayoutParams(UIUtils.dp2pxInt(12), UIUtils.dp2pxInt(12)));
        filter = UiTableStyle.rowButton(context, theme);
        SvgIconView chevron = new SvgIconView(context, SvgIconView.Icon.CHEVRON_DOWN, theme.text().muted());
        // Resource SVG, also used by the standalone headers while consumers migrate to UiTable.
        filter.setContentDescription(title);
        filter.setOnClickListener(view -> { if (openFilter != null) openFilter.run(); });
        int size = UIUtils.dp2pxInt(18);
        LayoutParams params = new LayoutParams(size, size);
        params.rightMargin = UIUtils.dp2pxInt(5);
        icyllis.modernui.widget.FrameLayout filterHost = new icyllis.modernui.widget.FrameLayout(context);
        filterHost.addView(filter, new icyllis.modernui.widget.FrameLayout.LayoutParams(-1, -1));
        filterHost.addView(chevron, new icyllis.modernui.widget.FrameLayout.LayoutParams(
                UIUtils.dp2pxInt(12), UIUtils.dp2pxInt(12), Gravity.CENTER));
        chevron.setClickable(false);
        chevron.setFocusable(false);
        filterHost.setVisibility(openFilter == null ? GONE : VISIBLE);
        addView(filterHost, params);
        setFilterActive(false);
    }

    public UiActionButton filterButton() { return filter; }

    public void setTitle(String title) { label.setText(title); }

    public void setSortDirection(int direction) {
        if (direction < 0 || direction > 2) throw new IllegalArgumentException("Invalid sort direction");
        indicator.setVisibility(direction == 0 ? GONE : VISIBLE);
        if (direction != 0) indicator.setIcon(direction == 1 ? SvgIconView.Icon.ARROW_UP : SvgIconView.Icon.ARROW_DOWN);
    }

    @Override public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        // The two child controls own their disabled visuals and click guards.
        if (label != null) label.setEnabled(enabled);
        if (filter != null) filter.setEnabled(enabled);
    }

    public void setFilterActive(boolean active) {
        filter.setSelected(active);
        filter.setPalette(UiTableStyle.rowPalette(active, theme.surface().header(), theme));
    }
}
