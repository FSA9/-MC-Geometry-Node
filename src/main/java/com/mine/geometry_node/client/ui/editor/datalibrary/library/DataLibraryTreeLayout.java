package com.mine.geometry_node.client.ui.editor.datalibrary.library;

import com.mine.geometry_node.client.ui.theme.UiThemes;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.widget.LinearLayout;

/** Draws the tree-local drop indicator after its rows. */
final class DataLibraryTreeLayout extends LinearLayout {
    private static final int DROP_INDICATOR_COLOR = UiThemes.EDITOR.selection().outline();
    private final Paint indicatorPaint = new Paint();
    private int indicatorY = -1;
    private int indicatorLeft = -1;

    DataLibraryTreeLayout(Context context) {
        super(context);
        setWillNotDraw(false);
        indicatorPaint.setColor(DROP_INDICATOR_COLOR);
    }

    void updateIndicator(int y, int left) {
        if (indicatorY == y && indicatorLeft == left) return;
        indicatorY = y;
        indicatorLeft = left;
        invalidate();
    }

    void hideIndicator() {
        if (indicatorY < 0) return;
        indicatorY = -1;
        indicatorLeft = -1;
        invalidate();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (indicatorY < 0) return;
        float inset = Math.max(1, px(1));
        float y = Math.max(inset, Math.min(getHeight() - inset, indicatorY));
        float right = Math.max(inset, getWidth() - getPaddingRight());
        float left = Math.min(indicatorLeft, right);
        indicatorPaint.setStyle(Paint.Style.STROKE);
        indicatorPaint.setStrokeWidth(Math.max(1, px(2)));
        indicatorPaint.setColor(DROP_INDICATOR_COLOR);
        canvas.drawLine(left, y, right, y, indicatorPaint);
        canvas.drawLine(left, Math.max(inset, y - px(3)), left,
                Math.min(getHeight() - inset, y + px(3)), indicatorPaint);
    }

    private static int px(float value) { return UIUtils.dp2pxInt(value); }
}
