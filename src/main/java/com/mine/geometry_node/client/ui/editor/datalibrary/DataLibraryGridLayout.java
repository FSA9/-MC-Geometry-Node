package com.mine.geometry_node.client.ui.editor.datalibrary;

import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;

/** Dense fixed-size grid with predictable wrapping as the editor width changes. */
final class DataLibraryGridLayout extends ViewGroup {
    private final int gap = UIUtils.dp2pxInt(3);
    private final int cardWidth = UIUtils.dp2pxInt(156);
    private final int cardHeight;
    private int columns = 1;
    private int measuredCardWidth = cardWidth;

    DataLibraryGridLayout(Context context, int cardHeightDp) {
        super(context);
        cardHeight = UIUtils.dp2pxInt(cardHeightDp);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int available = Math.max(0, width - getPaddingLeft() - getPaddingRight());
        columns = Math.max(1, (available + gap) / (cardWidth + gap));
        measuredCardWidth = Math.min(cardWidth, available);
        int visible = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            child.measure(MeasureSpec.makeMeasureSpec(measuredCardWidth, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(cardHeight, MeasureSpec.EXACTLY));
            visible++;
        }
        int rows = visible == 0 ? 0 : (visible + columns - 1) / columns;
        int height = getPaddingTop() + getPaddingBottom()
                + rows * cardHeight + Math.max(0, rows - 1) * gap;
        setMeasuredDimension(resolveSize(width, widthMeasureSpec), resolveSize(height, heightMeasureSpec));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int x = getPaddingLeft();
        int y = getPaddingTop();
        int column = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            child.layout(x, y, x + measuredCardWidth, y + cardHeight);
            if (++column >= columns) {
                column = 0;
                x = getPaddingLeft();
                y += cardHeight + gap;
            } else {
                x += measuredCardWidth + gap;
            }
        }
    }
}
