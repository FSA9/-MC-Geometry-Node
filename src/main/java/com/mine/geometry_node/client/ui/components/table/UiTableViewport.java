package com.mine.geometry_node.client.ui.components.table;

import com.mine.geometry_node.client.ui.components.scroll.UiHorizontalScrollView;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;

/** Scrolls a header and body together; column resizing only remeasures existing views. */
public final class UiTableViewport extends UiHorizontalScrollView {
    private final UiTableColumns columns;
    private final LinearLayout table;
    private final Runnable widthsChanged = this::refreshTableWidths;

    public UiTableViewport(Context context, UiTableColumns columns, LinearLayout table) {
        super(context);
        this.columns = columns;
        this.table = table;
        setFillViewport(true);
        addView(table, new FrameLayout.LayoutParams(columns.contentWidth(), -1));
        columns.setOnWidthsChanged(widthsChanged);
    }

    private void refreshTableWidths() {
        var params = table.getLayoutParams();
        params.width = columns.contentWidth();
        table.setLayoutParams(params);
        requestRowLayouts(table);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        columns.setOnWidthsChanged(widthsChanged);
        refreshTableWidths();
    }

    @Override protected void onDetachedFromWindow() {
        columns.clearOnWidthsChanged(widthsChanged);
        super.onDetachedFromWindow();
    }

    private static void requestRowLayouts(View view) {
        // Internal widths can change with the same total width; invalidate cached cell measurements.
        if (view instanceof UiTableRow) {
            view.requestLayout();
        } else if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) requestRowLayouts(group.getChildAt(i));
        }
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        columns.setViewportWidth(Math.max(0,
                MeasureSpec.getSize(widthSpec) - getPaddingLeft() - getPaddingRight()));
        super.onMeasure(widthSpec, heightSpec);
    }
}
