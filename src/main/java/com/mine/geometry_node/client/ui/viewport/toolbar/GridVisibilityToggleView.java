package com.mine.geometry_node.client.ui.viewport.toolbar;

import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.graphics.RectF;

final class GridVisibilityToggleView extends ViewportToolButton {
    private static final int COLOR_BG_OFF = 0xFF252A33;
    private static final int COLOR_BG_ON = 0xFF2E5138;
    private static final int COLOR_BORDER_OFF = 0xFF4B5565;
    private static final int COLOR_BORDER_ON = 0xFF8BE6A0;
    private static final int COLOR_LINE_OFF = 0xFF8992A3;
    private static final int COLOR_LINE_ON = 0xFFFFFFFF;
    private static final int COLOR_HIGHLIGHT = 0x33FFFFFF;

    private final Paint mPaint = new Paint();
    private final RectF mRect = new RectF();

    GridVisibilityToggleView(Context context, TooltipHost tooltipHost) {
        super(context, "", tooltipHost);
        mPaint.setAntiAlias(true);
        setToolActive(true);
    }

    void setGridVisible(boolean visible) {
        setToolActive(visible);
    }

    boolean isGridVisible() {
        return isToolActive();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float w = getWidth();
        float h = getHeight();
        boolean active = isToolActive();
        boolean hovered = isToolHovered();
        float minSize = Math.min(w, h);
        float pad = Math.max(1.0f, minSize * 0.08f);
        float stroke = Math.max(1.0f, UIUtils.dp2px(1.0f));
        float left = pad;
        float top = pad;
        float right = w - pad;
        float bottom = h - pad;
        float radius = minSize * 0.22f;

        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(active ? COLOR_BG_ON : COLOR_BG_OFF);
        mRect.set(left, top, right, bottom);
        canvas.drawRoundRect(mRect, radius, radius, radius, radius, mPaint);

        if (active || hovered) {
            mPaint.setColor(COLOR_HIGHLIGHT);
            mRect.set(left + stroke, top + stroke, right - stroke, top + minSize * 0.38f);
            canvas.drawRoundRect(mRect, radius * 0.8f, radius * 0.8f, radius * 0.8f, radius * 0.8f, mPaint);
        }

        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(stroke);
        mPaint.setColor(active ? COLOR_BORDER_ON : (hovered ? COLOR_LINE_OFF : COLOR_BORDER_OFF));
        mRect.set(left + stroke * 0.5f, top + stroke * 0.5f, right - stroke * 0.5f, bottom - stroke * 0.5f);
        canvas.drawRoundRect(mRect, radius, radius, radius, radius, mPaint);

        float iconInset = minSize * 0.27f;
        float iconLeft = left + iconInset;
        float iconTop = top + iconInset;
        float iconRight = right - iconInset;
        float iconBottom = bottom - iconInset;
        float cx = (iconLeft + iconRight) * 0.5f;
        float cy = (iconTop + iconBottom) * 0.5f;

        mPaint.setColor(active ? COLOR_LINE_ON : COLOR_LINE_OFF);
        mPaint.setAlpha(active ? 230 : (hovered ? 185 : 135));
        mPaint.setStrokeWidth(Math.max(1.0f, minSize * 0.055f));

        canvas.drawLine(iconLeft, iconTop, iconRight, iconTop, mPaint);
        canvas.drawLine(iconLeft, cy, iconRight, cy, mPaint);
        canvas.drawLine(iconLeft, iconBottom, iconRight, iconBottom, mPaint);
        canvas.drawLine(iconLeft, iconTop, iconLeft, iconBottom, mPaint);
        canvas.drawLine(cx, iconTop, cx, iconBottom, mPaint);
        canvas.drawLine(iconRight, iconTop, iconRight, iconBottom, mPaint);

        mPaint.setAlpha(active ? 255 : 160);
        mPaint.setStrokeWidth(Math.max(1.0f, minSize * 0.09f));
        canvas.drawLine(cx, iconTop, cx, iconBottom, mPaint);
        canvas.drawLine(iconLeft, cy, iconRight, cy, mPaint);
        mPaint.setAlpha(255);
    }
}
