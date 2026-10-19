package com.mine.geometry_node.client.ui.components.table;

import icyllis.modernui.core.Context;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.FrameLayout;

/** Sizes a row's background and overlays from its cells, without letting MATCH_PARENT overlays stretch it. */
public final class UiTableRowFrame extends FrameLayout {
    private final UiTableRow cells;

    public UiTableRowFrame(Context context, UiTableRow cells, View background) {
        super(context);
        this.cells = cells;
        addView(background, new LayoutParams(-1, -1));
        addView(cells, new LayoutParams(-1, -1));
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        cells.measure(widthSpec, getChildMeasureSpec(heightSpec, 0, LayoutParams.WRAP_CONTENT));
        super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(
                resolveSize(cells.getMeasuredHeight(), heightSpec), MeasureSpec.EXACTLY));
    }
}
